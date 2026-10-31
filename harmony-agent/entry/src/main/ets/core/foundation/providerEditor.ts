// Ported provider interaction from app/src/main/kotlin/io/github/mangi/eta/ui/pages/providers/ProviderConfigDraft.kt, ProviderModelsTab.kt, RemoteModelImportSheet.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { ProviderHeaderDraft, headersRecord, ProviderModelUi, filterProviderModels, contextWindowInputError } from '../eta/providerConfigDraft';
import { DraftRepository } from '../drafts';
import { ModelDraft, copyModelDraft, emptyModelDraft, editModelAddress, modelSnapshot, profileDraft } from '../modelForm';
import { fetchModels, HttpTransport, OpenAIClient } from '../model';
import { ProfileRepository, ModelProfile } from '../profiles';
import { ModelProtocol } from '../protocols';
import { enrichOfficialModels } from '../eta/officialModelCatalog';
import { ModelCapabilityEdit } from '../eta/modelCapabilityEditor';
import { foundationReasoningConfig } from '../eta/foundationReasoningConfig';
import { RunControl } from '../types';
import { redactSecrets } from '../conversations';

export interface ProviderEditorState {
  draft: ModelDraft; busy: boolean; working: string; error: string; message: string;
  headersExpanded:boolean;modelQuery:string;selectionMode:boolean;modelSelection:string[];connection: string; catalog: string[]; selected: string[]; query: string; tab: 'config' | 'models';
}
export type ProviderText = 'name' | 'baseUrl' | 'apiKey' | 'model' | 'systemPrompt' | 'anthropicVersion';
export type ProviderFlag = 'rememberKey' | 'streaming' | 'allowInsecureHttp' | 'isEnabled';
function fresh(): ProviderEditorState { return {headersExpanded:false,modelQuery:'',selectionMode:false,modelSelection:[], draft: emptyModelDraft(), busy: false, working: '', error: '', message: '', connection: '', catalog: [], selected: [], query: '', tab: 'config' }; }

/** Provider edits never change the conversation's model. Results belong to a specific editor epoch. */
export class ProviderEditor {
  state = fresh();
  private epoch = 0;private headerSequence=0;
  private io: HttpTransport | undefined;
  private control: RunControl | undefined;
  private timer: ReturnType<typeof setTimeout> | undefined;
  constructor(private profiles: ProfileRepository, private drafts: DraftRepository,
    private transport: () => HttpTransport, private changed: () => void) {}
  snapshot(): ProviderEditorState { return { ...this.state, draft: copyModelDraft(this.state.draft), catalog: this.state.catalog.slice(), selected: this.state.selected.slice(),modelSelection:this.state.modelSelection.slice() }; }
  private publish(): void { this.changed(); }
  private cache(): void {
    this.drafts.setProvider(this.state.draft);
    clearTimeout(this.timer);
    this.timer = setTimeout(() => { void this.flush().catch(e => { this.state.error = (e as Error).message; this.publish(); }); }, 400);
  }
  async flush(): Promise<void> { clearTimeout(this.timer); await this.drafts.flush(); }
  cancelRequest(): void {
    this.epoch++; this.control?.stop(); this.io?.cancel(); this.io = undefined; this.control = undefined;
    this.state.busy = false; this.state.working = '';
  }
  async open(id = '',newProtocol?:ModelProtocol): Promise<void> {
    this.cancelRequest(); const epoch = this.epoch;
    this.state = fresh(); this.state.busy = true; this.state.working = '载入'; this.publish();
    try {
      const profile = this.profiles.document.profiles.find(p => p.id === id);
      if (id && !profile) throw new Error('服务已删除，请重新选择');
      const cached = this.drafts.provider(id);
      let draft = (newProtocol&&cached?.protocol!==newProtocol?undefined:cached) || (profile ? profileDraft(profile, '') : {...emptyModelDraft(),name:'',protocol:newProtocol||'chat_completions',baseUrl:newProtocol==='anthropic'?'https://api.anthropic.com':newProtocol==='gemini'?'https://generativelanguage.googleapis.com/v1beta':'',anthropicVersion:newProtocol==='anthropic'?'2023-06-01':undefined});
      if (profile && !draft.apiKey && !this.drafts.hasProviderKey(id) && draft.baseUrl === profile.baseUrl) {
        const key = await this.profiles.key(profile);
        draft = { ...draft, apiKey: key };
      }
      if (epoch !== this.epoch) return;
      if(draft.headerDrafts===undefined&&profile&&draft.baseUrl===profile.baseUrl)draft.extraHeaders={...profile.extraHeaders};
      draft.headerDrafts??=Object.entries(draft.extraHeaders||{}).map(([name,value])=>({id:'header-'+(++this.headerSequence),name,value}));
      this.state.draft = copyModelDraft(draft);
    } catch (e) { if (epoch === this.epoch) this.state.error = (e as Error).message; }
    finally { if (epoch === this.epoch) { this.state.busy = false; this.state.working = ''; this.publish(); } }
  }
  text(field: ProviderText, value: string): void {
    if (this.state.working === '保存' || this.state.working === '删除' || this.state.working === '载入' || value === this.state.draft[field]) return;
    this.cancelRequest();
    this.state.draft = field === 'baseUrl' ? editModelAddress(this.state.draft, value) : { ...this.state.draft, [field]: value };
    this.state.catalog = []; this.state.selected = []; this.state.error = ''; this.state.message = ''; this.state.connection = '';
    this.cache(); this.publish();
  }
  toggleHeaders():void{this.state.headersExpanded=!this.state.headersExpanded;this.publish();}
  header(id:string,field:'name'|'value',value:string):void{
    if(['保存','删除','载入'].includes(this.state.working))return;
    this.cancelRequest();this.state.draft.headerDrafts=(this.state.draft.headerDrafts||[]).map(h=>h.id===id?{...h,[field]:value}:h);this.state.catalog=[];this.state.selected=[];this.cache();this.publish();
  }
  addHeader():void{if(this.state.busy)return;this.state.draft.headerDrafts=(this.state.draft.headerDrafts||[]).concat([{id:'header-'+(++this.headerSequence),name:'',value:''}]);this.cache();this.publish();}
  removeHeader(id:string):void{if(this.state.busy)return;this.state.draft.headerDrafts=(this.state.draft.headerDrafts||[]).filter(h=>h.id!==id);this.cache();this.publish();}
  private prepareHeaders():boolean{try{this.state.draft.extraHeaders=headersRecord(this.state.draft.headerDrafts||[]);return true;}catch(e){this.state.error=(e as Error).message;this.publish();return false;}}
  modelSearch(query:string):void{this.state.modelQuery=query;this.publish();}
  models():ProviderModelUi[]{return filterProviderModels(this.state.draft.models.map((id,index)=>{const m=this.state.draft.metadata?.find(x=>x.id===id);return{id,modelId:id,displayName:m?.name||id,sortOrder:index,enabled:m?.enabled!==false,contextWindow:m?.contextWindow,vision:m?.vision,metadata:m};}),this.state.modelQuery);}
  beginModelSelection(id:string):void{if(this.state.busy)return;this.state.selectionMode=true;this.state.modelSelection=[id];this.publish();}
  endModelSelection():void{this.state.selectionMode=false;this.publish();}
  toggleModelSelection(id:string):void{if(this.state.busy)return;this.state.modelSelection=this.state.modelSelection.includes(id)?this.state.modelSelection.filter(x=>x!==id):this.state.modelSelection.concat(id);this.publish();}
  toggleAllModels():void{const ids=this.models().map(m=>m.id);const all=ids.length>0&&ids.every(id=>this.state.modelSelection.includes(id));this.state.modelSelection=all?this.state.modelSelection.filter(id=>!ids.includes(id)):[...new Set([...this.state.modelSelection,...ids])];this.publish();}
  deleteSelectedModels():void{if(this.state.busy)return;for(const id of this.state.modelSelection)this.removeModel(id);this.state.selectionMode=false;this.publish();}
  editModel(previous:string,id:string,name:string,context:string,vision:boolean,capabilities?:ModelCapabilityEdit):boolean{
    if(this.state.busy)return false;id=id.trim();if(!id||id.length>256){this.state.error='模型 ID 无效';this.publish();return false;}
    if(contextWindowInputError(context)){this.state.error='上下文窗口必须是正整数';this.publish();return false;}
    if(id!==previous&&this.state.draft.models.includes(id)){this.state.error='模型 ID 已存在';this.publish();return false;}
    const prior=this.state.draft.metadata?.find(m=>m.id===previous);this.state.draft.models=this.state.draft.models.map(m=>m===previous?id:m);
    if(!previous)this.addModel(id);if(this.state.draft.model===previous)this.state.draft.model=id;
    this.state.draft.metadata=(this.state.draft.metadata||[]).filter(m=>m.id!==previous&&m.id!==id).concat([{...prior,...capabilities,id,name:name.trim()||id,contextWindow:Number(context.trim()),vision,source:prior?.source||'custom'}]);this.cache();this.publish();return true;
  }
  enableModel(id:string,enabled:boolean):void{if(this.state.busy)return;const prior=this.state.draft.metadata?.find(m=>m.id===id);this.state.draft.metadata=(this.state.draft.metadata||[]).filter(m=>m.id!==id).concat([{...prior,id,name:prior?.name||id,source:prior?.source||'custom',enabled}]);this.cache();this.publish();}
  flag(field: ProviderFlag, value: boolean): void {
    if (this.state.busy) return;
    this.state.draft = { ...this.state.draft, [field]: value }; this.cache(); this.publish();
  }
  protocol(value: ModelProtocol): void {
    if (this.state.busy) return;
    this.cancelRequest(); this.state.draft = { ...this.state.draft, protocol: value };
    this.state.catalog = []; this.state.connection = ''; this.cache(); this.publish();
  }
  tab(value: 'config' | 'models'): void { this.state.tab = value; this.publish(); }
  search(value: string): void { this.state.query = value; this.publish(); }
  filteredCatalog(): string[] { return filterProviderModels(this.state.catalog.map((id,index)=>({id,modelId:id,displayName:this.state.draft.metadata?.find(m=>m.id===id)?.name||id,sortOrder:index,enabled:true})),this.state.query).map(m=>m.id); }
  toggle(id: string): void {
    if (!this.state.catalog.includes(id)) return;
    this.state.selected = this.state.selected.includes(id) ? this.state.selected.filter(x => x !== id) : this.state.selected.concat(id); this.publish();
  }
  toggleVisible(): void {
    const visible = this.filteredCatalog();
    this.state.selected = visible.every(id => this.state.selected.includes(id)) ? this.state.selected.filter(id => !visible.includes(id)) : [...new Set([...this.state.selected, ...visible])]; this.publish();
  }
  dismissCatalog(): void { this.state.catalog = []; this.state.selected = []; this.publish(); }
  importCatalog(): void {
    const ids = this.state.selected.filter(id => this.state.catalog.includes(id));
    this.state.draft.models = [...new Set([...this.state.draft.models, ...ids])].slice(0, 5000);
    if (!this.state.draft.model) this.state.draft.model = ids[0] || '';
    this.state.message = '已添加 ' + ids.length + ' 个模型，保存服务后生效'; this.state.tab = 'models'; this.cache(); this.dismissCatalog();
  }
  removeModel(id: string): void {
    if (this.state.busy) return;
    this.state.draft.models = this.state.draft.models.filter(m => m !== id);
    if (this.state.draft.model === id) this.state.draft.model = this.state.draft.models[0] || '';
    this.cache(); this.publish();
  }
  addModel(id: string): void {
    const value = id.trim(); if (!value || value.length > 256 || this.state.busy) return;
    this.state.draft.models = [...new Set([...this.state.draft.models, value])].slice(0, 5000);
    if (!this.state.draft.model) this.state.draft.model = value;
    this.cache(); this.publish();
  }
  async probe(testFirst: boolean): Promise<boolean> {
    if (this.state.busy||!this.prepareHeaders()) return false;
    this.cancelRequest(); const epoch = this.epoch, snapshot = {...modelSnapshot(this.state.draft),...foundationReasoningConfig(this.state.draft,this.state.draft.model,{thinkingEnabled:true,autoCompactionEnabled:false})}, io = this.transport(), control = new RunControl();
    this.io = io; this.control = control; this.state.busy = true; this.state.working = '连接'; this.state.error = ''; this.state.message = ''; this.state.connection = ''; this.publish();
    try {
      if (testFirst && snapshot.model.trim()) {
        const reply = await new OpenAIClient(snapshot, io, []).complete([{ role: 'user', content: '请只回复 OK。这是连接测试。' }], control);
        if (epoch !== this.epoch) return false;
        if (reply.finishReason !== 'stop' || !reply.message.content) throw new Error('未收到完整文本回复');
        this.state.connection = '文本连接成功'; this.publish();
      }
      const result = await fetchModels(snapshot, io);
      if (epoch !== this.epoch) return false;
      const previous=this.state.draft.metadata||[];this.state.draft.metadata=[...previous,...enrichOfficialModels(this.state.draft,result.metadata||[]).filter(m=>!previous.some(p=>p.id===m.id))];
      this.state.catalog = result.ids.filter(id => !this.state.draft.models.includes(id)); this.state.selected = []; this.state.query = '';
      this.state.message = '已获取 ' + result.ids.length + ' 个模型' + (result.partial ? '（部分目录）' : '') + (this.state.catalog.length ? '，请选择要添加的模型' : '，均已添加');
      if (!this.state.connection) this.state.connection = '模型目录连接成功';
      return this.state.catalog.length > 0;
    } catch (e) {
      if (epoch === this.epoch) this.state.error = (this.state.connection ? this.state.connection + '；目录获取未完成，可手动添加模型。' : '') + redactSecrets((e as Error).message, [snapshot.apiKey,...Object.values(snapshot.extraHeaders||{})]);
      return false;
    } finally {
      io.cancel();
      if (epoch === this.epoch) { this.io = undefined; this.control = undefined; this.state.busy = false; this.state.working = ''; this.publish(); }
    }
  }
  async save(): Promise<boolean> {
    if (this.state.busy||!this.prepareHeaders()) return false;
    const draft = copyModelDraft(this.state.draft), epoch = ++this.epoch;
    this.state.busy = true; this.state.working = '保存'; this.state.error = ''; this.publish();
    try {
      const profile: ModelProfile = { ...draft };
      const saved = await this.profiles.save(profile, draft.apiKey, draft.rememberKey);
      if (epoch !== this.epoch) return false;
      if (!draft.id) this.drafts.removeProvider('');
      this.state.draft = {...profileDraft(saved, draft.apiKey),headerDrafts:draft.headerDrafts}; this.cache();
      this.state.message = this.profiles.warning || '服务已保存'; return true;
    } catch (e) { if (epoch === this.epoch) this.state.error = redactSecrets((e as Error).message, [draft.apiKey,...Object.values(draft.extraHeaders||{})]); return false; }
    finally { if (epoch === this.epoch) { this.state.busy = false; this.state.working = ''; this.publish(); } }
  }
  async resetBuiltIn():Promise<void>{
    if(this.state.busy||!this.state.draft.isBuiltIn)return;
    this.cancelRequest();const id=this.state.draft.id;this.state.busy=true;this.state.working='保存';this.publish();
    try{await this.profiles.resetBuiltIn(id);this.drafts.removeProvider(id);await this.flush();await this.open(id);this.state.message='已重置';}
    catch(e){this.state.error=(e as Error).message;}
    finally{this.state.busy=false;this.state.working='';this.publish();}
  }
  async remove(): Promise<boolean> {
    if (this.state.busy || !this.state.draft.id) return false;
    this.state.busy = true; this.state.working = '删除'; this.publish();
    try { await this.profiles.remove(this.state.draft.id); this.drafts.removeProvider(this.state.draft.id); await this.flush(); return true; }
    catch (e) { this.state.error = (e as Error).message; return false; }
    finally { this.state.busy = false; this.state.working = ''; this.publish(); }
  }
  close(): void { if (!['保存', '删除'].includes(this.state.working)) this.cancelRequest(); clearTimeout(this.timer); void this.flush().catch(() => {}); }
}
