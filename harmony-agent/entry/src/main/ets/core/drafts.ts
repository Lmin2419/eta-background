import { ConversationStorage } from './conversations';
import { ModelDraft, copyModelDraft, emptyModelDraft } from './modelForm';

export interface EditorDraft {id:string;text:string;scroll:number;followBottom?:boolean;attachments?:string[]}
interface DraftDocument {version:1;messages:EditorDraft[];providers:ModelDraft[]}
/** Provider secrets are never written to ordinary draft storage. */
export class DraftRepository {
  private document:DraftDocument={version:1,messages:[],providers:[]};
  private headers:Map<string,Pick<ModelDraft,'extraHeaders'|'headerDrafts'>>=new Map();
  private keys:Map<string,string>=new Map();
  private tail:Promise<void>=Promise.resolve();
  private readable=true;
  constructor(private storage:ConversationStorage){}
  async load():Promise<void>{
    this.readable=false;
    const raw=await this.storage.read();
    if(!raw){this.readable=true;return;}
    if(raw.length>2_000_000)throw new Error('草稿文件过大');
    const data=JSON.parse(raw) as DraftDocument;
    if(data.version!==1||!Array.isArray(data.messages)||!Array.isArray(data.providers)||data.messages.length>50||data.providers.length>51)throw new Error('草稿格式无效');
    for(const m of data.messages)if(!m||typeof m.id!=='string'||typeof m.text!=='string'||m.text.length>8000||typeof m.scroll!=='number'||!Number.isFinite(m.scroll)||m.scroll<0||(m.attachments!==undefined&&(!Array.isArray(m.attachments)||m.attachments.length>5||m.attachments.some(n=>typeof n!=='string'||n.length>128||/[\\/\0]/.test(n)))))throw new Error('会话草稿格式无效');
    for(const p of data.providers)if(!p||typeof p.id!=='string'||typeof p.name!=='string'||typeof p.baseUrl!=='string'||typeof p.model!=='string'||!Array.isArray(p.models)||p.models.length>5000||p.models.some(m=>typeof m!=='string'))throw new Error('服务草稿格式无效');
    this.document={version:1,messages:data.messages,providers:data.providers.map(p=>({...emptyModelDraft(),...p,apiKey:'',rememberKey:Boolean(p.rememberKey)}))};
    this.readable=true;
  }
  editors():EditorDraft[]{return JSON.parse(JSON.stringify(this.document.messages));}
  message(id:string):EditorDraft{return {...(this.document.messages.find(d=>d.id===id)||{id,text:'',scroll:0})};}
  provider(id:string):ModelDraft|undefined{const p=this.document.providers.find(p=>p.id===id);return p?{...copyModelDraft(p),...this.headers.get(id),apiKey:this.keys.get(id)||''}:undefined;}
  hasProviderKey(id:string):boolean{return this.keys.has(id);}
  setMessage(id:string,text:string,scroll?:number):void{const previous=this.message(id);scroll=scroll===undefined?previous.scroll:scroll;this.document.messages=this.document.messages.filter(d=>d.id!==id);if(text||scroll||previous.attachments?.length)this.document.messages.push({id,text:text.slice(0,8000),scroll,followBottom:previous.followBottom,attachments:previous.attachments});this.document.messages=this.document.messages.slice(-50);}
  setViewport(id:string,scroll:number,followBottom:boolean):void{const m=this.message(id);this.document.messages=this.document.messages.filter(d=>d.id!==id).concat([{...m,scroll:Math.max(0,scroll),followBottom}]).slice(-50);}
  setAttachments(id:string,names:string[]):void{if(names.length>5||names.some(n=>typeof n!=='string'||n.length>128||/[\\/\0]/.test(n)))throw new Error('附件引用无效');const m=this.message(id);this.document.messages=this.document.messages.filter(d=>d.id!==id).concat([{...m,attachments:names.slice()}]).slice(-50);}
  setProvider(draft:ModelDraft):void{this.keys.set(draft.id,draft.apiKey);this.headers.set(draft.id,{extraHeaders:{...draft.extraHeaders},headerDrafts:draft.headerDrafts?.map(h=>({...h}))});this.document.providers=this.document.providers.filter(p=>p.id!==draft.id).concat([{...copyModelDraft(draft),apiKey:'',extraHeaders:undefined,headerDrafts:undefined}]).slice(-51);}
  removeProvider(id:string):void{this.keys.delete(id);this.headers.delete(id);this.document.providers=this.document.providers.filter(p=>p.id!==id);}
  flush():Promise<void>{if(!this.readable)return Promise.reject(new Error('原草稿损坏，未覆盖；请先导出诊断后恢复'));const raw=JSON.stringify(this.document);const p=this.tail.then(()=>this.storage.write(raw));this.tail=p.catch(()=>{});return p;}
}
