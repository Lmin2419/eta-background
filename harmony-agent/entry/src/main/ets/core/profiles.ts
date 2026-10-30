import { DataGate } from './dataGate';
import { ModelMetadata, parseMetadata, safeHeaders } from './modelMetadata';
import { ModelProtocol } from './protocols';
import { ModelSettings, modelEndpoint } from './model';

export interface ModelProfile {
  id: string; name: string; baseUrl: string; model: string;
  screenshots: boolean; allowInsecureHttp: boolean; secretRef?: string; models?: string[]; streaming?: boolean; protocol?:ModelProtocol; maxOutputTokens?:number; reasoningEffort?:string;metadata?:ModelMetadata[];extraHeaders?:Record<string,string>;
}
export function validProtocol(value:unknown):ModelProtocol{if(value===undefined)return 'chat_completions';if(!['chat_completions','responses','anthropic','gemini'].includes(String(value)))throw new Error('不支持的模型协议');return value as ModelProtocol;}
export function outputBudget(value:unknown):number|undefined{if(value===undefined)return undefined;if(typeof value!=='number'||!Number.isInteger(value)||value<256||value>65536)throw new Error('输出上限应为 256–65536');return value;}
export function reasoningEffort(value:unknown):string|undefined{if(value===undefined||value==='')return undefined;if(!['none','low','medium','high'].includes(String(value)))throw new Error('推理档位无效');return String(value);}
export interface ProfileDocument { profiles: ModelProfile[]; selectedId: string }
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
    if (!data || !Array.isArray(data.profiles) || data.profiles.length > 50 || typeof data.selectedId !== 'string') throw new Error('模型配置文件无效');
    const ids = new Set<string>();
    const profiles: ModelProfile[] = [];
    for (const p of data.profiles) {
      if (!p || typeof p.id !== 'string' || !p.id || ids.has(p.id) || typeof p.name !== 'string' || typeof p.baseUrl !== 'string' || typeof p.model !== 'string' || typeof p.screenshots !== 'boolean' || typeof p.allowInsecureHttp !== 'boolean' || (p.secretRef !== undefined && (typeof p.secretRef !== 'string' || !p.secretRef.startsWith('model-key-')))) throw new Error('模型配置文件无效');
      ids.add(p.id);
      profiles.push({id:p.id,name:p.name,baseUrl:p.baseUrl,model:p.model,screenshots:p.screenshots,allowInsecureHttp:p.allowInsecureHttp,secretRef:p.secretRef,models:modelCatalog(p.models,p.model),streaming:p.streaming!==false,protocol:validProtocol(p.protocol),maxOutputTokens:outputBudget(p.maxOutputTokens),reasoningEffort:reasoningEffort(p.reasoningEffort),metadata:parseMetadata(p.metadata),extraHeaders:safeHeaders(p.extraHeaders)});
    }
    return {profiles,selectedId:ids.has(data.selectedId) ? data.selectedId : (profiles[0]?.id || '')};

}
export class ProfileRepository {
  document: ProfileDocument = {profiles:[],selectedId:''};
  warning = '';
  private busy = false;
  private sessionKeys:Map<string,{url:string;key:string}>=new Map();
  clearSessionKeys():void{this.sessionKeys.clear();}
  rememberSessionKey(profileId:string,url:string,key:string):void{const profile=this.document.profiles.find(p=>p.id===profileId);if(profile&&profile.baseUrl===url)this.sessionKeys.set(profileId,{url,key});}
  constructor(private metadata: ProfileStorage, private secrets: SecretStorage, private newId: () => string,private gate?:DataGate) {}
  async load(): Promise<ProfileDocument> {
    const raw = await this.metadata.read();
    if (!raw) return this.document;
    this.document=parseProfileDocument(raw);
    return this.document;
  }
  async key(profile: ModelProfile): Promise<string> { if(profile.secretRef)return this.secrets.get(profile.secretRef);const saved=this.sessionKeys.get(profile.id);return saved?.url===profile.baseUrl?saved.key:''; }
  async save(profile: ModelProfile, apiKey: string, remember: boolean): Promise<ModelProfile> {const release=this.gate?.enter();try{return await this.saveUnlocked(profile,apiKey,remember);}finally{release?.();}}
  private async saveUnlocked(profile: ModelProfile, apiKey: string, remember: boolean): Promise<ModelProfile> {
    if (this.busy) throw new Error('模型配置正在保存');
    this.busy = true; this.warning = '';
    let newRef = '';
    let committed = false;
    try {
      if (!profile.name.trim() || !profile.model.trim()) throw new Error('请填写配置名称和模型 ID');
      modelEndpoint({...profile,apiKey} as ModelSettings);
      const old = this.document.profiles.find(p => p.id === profile.id);
      if (!old && this.document.profiles.length >= 50) throw new Error('最多保存 50 个模型配置');
      if (remember && apiKey.trim()) {
        newRef = 'model-key-' + this.newId();
        await this.secrets.put(newRef,apiKey.trim());
      }
      // Explicit projection prevents plaintext keys or unrelated caller fields from entering metadata.
      const saved: ModelProfile = {id:old?.id || this.newId(),name:profile.name.trim().slice(0,80),baseUrl:profile.baseUrl.trim(),model:profile.model.trim(),screenshots:profile.screenshots,allowInsecureHttp:profile.allowInsecureHttp,secretRef:newRef || undefined,models:modelCatalog(profile.models,profile.model.trim()),streaming:profile.streaming!==false,protocol:validProtocol(profile.protocol),maxOutputTokens:outputBudget(profile.maxOutputTokens),reasoningEffort:reasoningEffort(profile.reasoningEffort),metadata:parseMetadata(profile.metadata),extraHeaders:safeHeaders(profile.extraHeaders)};
      const profiles = this.document.profiles.filter(p => p.id !== saved.id).concat([saved]);
      const next: ProfileDocument = {profiles,selectedId:saved.id};
      await this.metadata.write(JSON.stringify(next));
      this.document = next; committed = true;this.rememberSessionKey(saved.id,saved.baseUrl,apiKey);
      if (old?.secretRef) await this.cleanup(old.secretRef);
      return saved;
    } catch (e) {
      if (newRef && !committed) await this.cleanup(newRef);
      throw e;
    } finally { this.busy = false; }
  }
  async remove(id: string): Promise<void> {const release=this.gate?.enter();try{await this.removeUnlocked(id);}finally{release?.();}}
  private async removeUnlocked(id: string): Promise<void> {
    if (this.busy) throw new Error('模型配置正在保存');
    this.busy = true; this.warning = '';
    try {
      const old = this.document.profiles.find(p => p.id === id);
      const profiles = this.document.profiles.filter(p => p.id !== id);
      const next = {profiles,selectedId:this.document.selectedId === id ? (profiles[0]?.id || '') : this.document.selectedId};
      await this.metadata.write(JSON.stringify(next));
      this.document = next;this.sessionKeys.delete(id);
      if (old?.secretRef) await this.cleanup(old.secretRef);
    } finally { this.busy = false; }
  }
  private async cleanup(alias: string): Promise<void> {
    try { await this.secrets.remove(alias); }
    catch (_) { this.warning = '配置已更新，但旧密钥清理失败；卸载应用可清理其安全存储'; }
  }
}
