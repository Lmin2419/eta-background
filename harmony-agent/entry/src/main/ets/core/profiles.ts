import { BUILTIN_PROVIDERS, mergeBuiltinProviders, seedOfficialModels } from './eta/builtinProviders';
import { DataGate } from './dataGate';
import { ModelMetadata, parseMetadata, safeHeaders } from './modelMetadata';
import { ModelProtocol } from './protocols';
import { ModelSettings, modelEndpoint } from './model';

export interface ModelProfile {
  anthropicVersion?:string;
  systemPrompt?:string;isEnabled?:boolean;headersRef?:string;isBuiltIn?:boolean;hasApiKey?:boolean;
  id: string; name: string; baseUrl: string; model: string;
  screenshots: boolean; allowInsecureHttp: boolean; secretRef?: string; models?: string[]; streaming?: boolean; protocol?:ModelProtocol; maxOutputTokens?:number; reasoningEffort?:string;metadata?:ModelMetadata[];extraHeaders?:Record<string,string>;
}
export function validProtocol(value:unknown):ModelProtocol{if(value===undefined)return 'chat_completions';if(!['chat_completions','responses','anthropic','gemini'].includes(String(value)))throw new Error('不支持的模型协议');return value as ModelProtocol;}
export function outputBudget(value:unknown):number|undefined{if(value===undefined)return undefined;if(typeof value!=='number'||!Number.isInteger(value)||value<256||value>65536)throw new Error('输出上限应为 256–65536');return value;}
export function reasoningEffort(value:unknown):string|undefined{if(value===undefined||value==='')return undefined;if(!['none','off','default','minimal','low','medium','high','xhigh','max'].includes(String(value)))throw new Error('推理档位无效');return String(value);}
function headerReference(value:unknown):string|undefined{if(value===undefined)return undefined;if(typeof value!=='string'||!value.startsWith('model-headers-'))throw Error('请求头安全引用无效');return value;}
function providerPrompt(value:unknown):string|undefined{if(value===undefined)return undefined;if(typeof value!=='string'||value.length>32000)throw Error('服务提示词过长');return value.trim()||undefined;}
function durableProfiles(document:ProfileDocument):string{return JSON.stringify({...document,profiles:document.profiles.map(p=>{const {hasApiKey,...clean}=p;if(!p.headersRef)return clean;const {extraHeaders,...stored}=clean;return stored;})});}
export interface ProfileDocument { profiles: ModelProfile[]; selectedId: string; officialCatalogRevision?:number }
export interface ProfileStorage { read(): Promise<string>; write(value: string): Promise<void> }
export interface SecretStorage {
  put(alias: string, value: string): Promise<void>;
  get(alias: string): Promise<string>;
  remove(alias: string): Promise<void>;
}
export function modelCatalog(value:unknown,selected=''):string[] {
  if(value!==undefined&&(!Array.isArray(value)||value.length>5000||value.some(id=>typeof id!=='string'||!id.trim()||id.length>256)))throw new Error('模型目录格式无效');
  return Array.from(new Set([...(value as string[]||[]),...(selected?[selected]:[])])).slice(0,5000);
}
export function parseProfileDocument(raw:string):ProfileDocument {
    const data = JSON.parse(raw);
    if (!data || !Array.isArray(data.profiles) || data.profiles.length > 62 || typeof data.selectedId !== 'string') throw new Error('模型配置文件无效');
    const ids = new Set<string>();
    const profiles: ModelProfile[] = [];
    for (const p of data.profiles) {
      if (!p || typeof p.id !== 'string' || !p.id || ids.has(p.id) || typeof p.name !== 'string' || typeof p.baseUrl !== 'string' || typeof p.model !== 'string' || typeof p.screenshots !== 'boolean' || typeof p.allowInsecureHttp !== 'boolean' || (p.secretRef !== undefined && (typeof p.secretRef !== 'string' || !p.secretRef.startsWith('model-key-')))) throw new Error('模型配置文件无效');
      ids.add(p.id);
      profiles.push({anthropicVersion:anthropicVersion(p.anthropicVersion),isBuiltIn:p.isBuiltIn===true&&BUILTIN_PROVIDERS.some(b=>b.id===p.id),hasApiKey:!!p.secretRef,id:p.id,name:p.name,baseUrl:p.baseUrl,model:p.model,screenshots:p.screenshots,allowInsecureHttp:p.allowInsecureHttp,secretRef:p.secretRef,models:modelCatalog(p.models,p.model),streaming:p.streaming!==false,protocol:validProtocol(p.protocol),maxOutputTokens:outputBudget(p.maxOutputTokens),reasoningEffort:reasoningEffort(p.reasoningEffort),metadata:parseMetadata(p.metadata),extraHeaders:safeHeaders(p.extraHeaders),headersRef:headerReference(p.headersRef),systemPrompt:providerPrompt(p.systemPrompt),isEnabled:p.isEnabled!==false});
    }
    return {officialCatalogRevision:Number.isInteger(data.officialCatalogRevision)&&data.officialCatalogRevision>=0?data.officialCatalogRevision:0,profiles,selectedId:ids.has(data.selectedId) ? data.selectedId : (profiles.find(p=>p.isEnabled!==false)?.id || '')};

}
export class ProfileRepository {
  document: ProfileDocument = {profiles:[],selectedId:''};
  warning = '';
  private busy = false;
  private sessionKeys:Map<string,{url:string;key:string}>=new Map();
  clearSessionKeys():void{this.sessionKeys.clear();for(const p of this.document.profiles)p.hasApiKey=!!p.secretRef;}
  rememberSessionKey(profileId:string,url:string,key:string):void{const profile=this.document.profiles.find(p=>p.id===profileId);if(profile&&profile.baseUrl===url){this.sessionKeys.set(profileId,{url,key});profile.hasApiKey=!!key.trim()||!!profile.secretRef;}}
  constructor(private metadata: ProfileStorage, private secrets: SecretStorage, private newId: () => string,private gate?:DataGate) {}
  async load(): Promise<ProfileDocument> {
    const raw = await this.metadata.read();
    if (!raw) return this.document;
    const loaded=parseProfileDocument(raw);
    for(const p of loaded.profiles)if(p.headersRef){const encoded=await this.secrets.get(p.headersRef);p.extraHeaders=safeHeaders(JSON.parse(encoded||'{}'));}
    this.document=loaded;
    return this.document;
  }
  async ensureBuiltInsMerged():Promise<void>{
    if(this.busy)throw Error('模型配置正在保存');this.busy=true;const release=this.gate?.enter();
    try{const next=mergeBuiltinProviders(this.document);if(durableProfiles(next)!==durableProfiles(this.document))await this.metadata.write(durableProfiles(next));this.document=next;}finally{release?.();this.busy=false;}
  }
  async resetBuiltIn(id:string):Promise<ModelProfile>{
    if(this.busy)throw Error('模型配置正在保存');const preset=BUILTIN_PROVIDERS.find(p=>p.id===id),old=this.document.profiles.find(p=>p.id===id);
    if(!preset||!old?.isBuiltIn)throw Error('不是内置提供商');this.busy=true;const release=this.gate?.enter();
    try{
      // Keep the existing key reference, never read/rewrite its plaintext while resetting metadata.
      const restored={...seedOfficialModels(preset),secretRef:old.secretRef,hasApiKey:old.hasApiKey};
      const next={...this.document,profiles:this.document.profiles.map(p=>p.id===id?restored:p)};
      await this.metadata.write(durableProfiles(next));this.document=next;
      const session=this.sessionKeys.get(id);if(session)this.sessionKeys.set(id,{url:restored.baseUrl,key:session.key});
      if(old.headersRef)await this.cleanup(old.headersRef);return restored;
    }finally{release?.();this.busy=false;}
  }
  async key(profile: ModelProfile): Promise<string> { if(profile.secretRef)return this.secrets.get(profile.secretRef);const saved=this.sessionKeys.get(profile.id);return saved?.url===profile.baseUrl?saved.key:''; }
  async save(profile: ModelProfile, apiKey: string, remember: boolean): Promise<ModelProfile> {const release=this.gate?.enter();try{return await this.saveUnlocked(profile,apiKey,remember);}finally{release?.();}}
  private async saveUnlocked(profile: ModelProfile, apiKey: string, remember: boolean): Promise<ModelProfile> {
    if (this.busy) throw new Error('模型配置正在保存');
    this.busy = true; this.warning = '';
    let newRef = '',newHeadersRef='';
    let committed = false;
    try {
      if (!profile.name.trim()) throw new Error('请填写配置名称');
      modelEndpoint({...profile,apiKey} as ModelSettings);
      const old = this.document.profiles.find(p => p.id === profile.id);
      if (!old && this.document.profiles.filter(p=>!p.isBuiltIn).length >= 50) throw new Error('最多保存 50 个模型配置');
      if (remember && apiKey.trim()) {
        newRef = 'model-key-' + this.newId();
        await this.secrets.put(newRef,apiKey.trim());
      }
      const extraHeaders=safeHeaders(profile.extraHeaders);
      if(Object.keys(extraHeaders).length){newHeadersRef='model-headers-'+this.newId();await this.secrets.put(newHeadersRef,JSON.stringify(extraHeaders));}
      // Explicit projection prevents plaintext keys or unrelated caller fields from entering metadata.
      const saved: ModelProfile = {anthropicVersion:anthropicVersion(profile.anthropicVersion),isBuiltIn:old?.isBuiltIn===true,hasApiKey:!!apiKey.trim(),id:old?.id || this.newId(),name:profile.name.trim().slice(0,80),baseUrl:profile.baseUrl.trim(),model:profile.model.trim(),screenshots:profile.screenshots,allowInsecureHttp:profile.allowInsecureHttp,secretRef:newRef || undefined,models:modelCatalog(profile.models,profile.model.trim()),streaming:profile.streaming!==false,protocol:validProtocol(profile.protocol),maxOutputTokens:outputBudget(profile.maxOutputTokens),reasoningEffort:reasoningEffort(profile.reasoningEffort),metadata:parseMetadata(profile.metadata),extraHeaders,headersRef:newHeadersRef||undefined,systemPrompt:providerPrompt(profile.systemPrompt),isEnabled:profile.isEnabled!==false};
      const profiles = old ? this.document.profiles.map(p=>p.id===saved.id?saved:p) : this.document.profiles.concat([saved]);
      const next: ProfileDocument = {...this.document,profiles,selectedId:saved.isEnabled!==false?saved.id:this.document.selectedId};
      await this.metadata.write(durableProfiles(next));
      this.document = next; committed = true;this.rememberSessionKey(saved.id,saved.baseUrl,apiKey);
      if (old?.secretRef) await this.cleanup(old.secretRef);
      if (old?.headersRef) await this.cleanup(old.headersRef);
      return saved;
    } catch (e) {
      if (newRef && !committed) await this.cleanup(newRef);
      if (newHeadersRef && !committed) await this.cleanup(newHeadersRef);
      throw e;
    } finally { this.busy = false; }
  }
  async select(id:string):Promise<void>{if(this.busy)throw Error('模型配置正在保存');const profile=this.document.profiles.find(p=>p.id===id);if(!profile||profile.isEnabled===false)throw Error('提供商未启用');this.busy=true;try{const next={...this.document,selectedId:id};await this.metadata.write(durableProfiles(next));this.document=next;}finally{this.busy=false;}}
  async remove(id: string): Promise<void> {const release=this.gate?.enter();try{await this.removeUnlocked(id);}finally{release?.();}}
  private async removeUnlocked(id: string): Promise<void> {
    if (this.busy) throw new Error('模型配置正在保存');
    this.busy = true; this.warning = '';
    try {
      const old = this.document.profiles.find(p => p.id === id);
      if(old?.isBuiltIn)throw Error('内置提供商不能删除');
      const profiles = this.document.profiles.filter(p => p.id !== id);
      const next = {...this.document,profiles,selectedId:this.document.selectedId === id ? (profiles.find(p=>p.isEnabled!==false)?.id || '') : this.document.selectedId};
      await this.metadata.write(durableProfiles(next));
      this.document = next;this.sessionKeys.delete(id);
      if (old?.secretRef) await this.cleanup(old.secretRef);
      if (old?.headersRef) await this.cleanup(old.headersRef);
    } finally { this.busy = false; }
  }
  private async cleanup(alias: string): Promise<void> {
    try { await this.secrets.remove(alias); }
    catch (_) { this.warning = '配置已更新，但旧密钥清理失败；卸载应用可清理其安全存储'; }
  }
}

export function anthropicVersion(value:unknown):string|undefined {if(value===undefined||value==='')return undefined;if(typeof value!=='string'||!/^[-A-Za-z0-9_.]{1,64}$/.test(value))throw Error('anthropic-version 格式无效');return value;}
