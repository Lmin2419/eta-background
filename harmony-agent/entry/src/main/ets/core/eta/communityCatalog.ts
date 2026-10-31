// Ported from app/src/main/kotlin/io/github/mangi/eta/data/provider/CommunityCatalog.kt,
// data/repository/CommunityCatalogRepository.kt, ui/app/CommunityCatalogStore.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { ModelDraft, emptyModelDraft } from '../modelForm';
export type CommunityCatalogSource='snapshot'|'cache'|'online';
export interface CommunityCatalogModel {modelId:string;displayName:string;contextWindow:number;inputModalities:string[];releaseDate:string|null;reasoning:boolean;structuredOutput:boolean|null;supportsTemperature:boolean|null}
export interface CommunityCatalogProvider {id:string;name:string;baseUrl:string;models:CommunityCatalogModel[]}
export interface CommunityCatalog {providers:CommunityCatalogProvider[];source:CommunityCatalogSource;fetchedAt:number|null}
function object(value:unknown):Record<string,unknown>|null{return value!==null&&typeof value==='object'&&!Array.isArray(value)?value as Record<string,unknown>:null;}
function strings(value:unknown):string[]{return Array.isArray(value)?value.filter(v=>typeof v==='string'&&v).map(v=>(v as string).toLowerCase()):[];}
function string(value:unknown):string{return typeof value==='string'?value:'';}
export function safeChatBaseUrl(raw:string):string|null{
  const m=/^https:\/\/([a-z\d.-]+)(?::(\d{1,5}))?([^\s\\?#]*)$/i.exec(raw.trim());if(!m)return null;
  const host=m[1].toLowerCase();if(host==='localhost'||host.endsWith('.local')||/^[\d.]+$/.test(host)||host.split('.').some(l=>!l||l.startsWith('-')||l.endsWith('-')))return null;
  if(m[2]&&(Number(m[2])<1||Number(m[2])>65535))return null;
  const path=m[3].replace(/\/+$/,'');if(path&&!path.startsWith('/'))return null;
  if(['/chat/completions','/responses','/messages','/models'].some(p=>path.toLowerCase().endsWith(p)))return null;
  return 'https://'+host+(m[2]&&m[2]!=='443'?':'+m[2]:'')+path;
}
export function parseCommunityCatalog(raw:string,source:CommunityCatalogSource,fetchedAt:number|null=null):CommunityCatalog{
  const root=object(JSON.parse(raw));if(!root)throw Error('目录格式无效');const providers:CommunityCatalogProvider[]=[];
  for(const [id,value]of Object.entries(root)){
    const item=object(value);if(!item||!/^[a-z0-9][a-z0-9_-]{0,79}$/.test(id))continue;
    const known:Record<string,string>={xai:'https://api.x.ai/v1',mistral:'https://api.mistral.ai/v1'};
    const url=known[id]||(item.npm==='@ai-sdk/openai-compatible'?string(item.api):'');const baseUrl=safeChatBaseUrl(url);if(!baseUrl)continue;
    const sourceModels=object(item.models);if(!sourceModels)continue;const models:CommunityCatalogModel[]=[];
    for(const [modelId,v]of Object.entries(sourceModels)){
      const row=object(v);if(!row||!modelId.trim()||modelId.length>200||/[\u0000-\u001f\u007f-\u009f]/.test(modelId)||'provider'in row)continue;
      if(['alpha','deprecated','retired','removed'].includes(string(row.status).toLowerCase())||row.tool_call!==true)continue;
      const modes=object(row.modalities),limit=object(row.limit);if(!modes||!limit)continue;
      const input=strings(modes.input),output=strings(modes.output),context=limit.context;
      if(!input.includes('text')||output.length!==1||output[0]!=='text'||typeof context!=='number'||!Number.isInteger(context)||context<1||context>2147483647)continue;
      const date=string(row.release_date);models.push({modelId,displayName:string(row.name).trim().slice(0,120)||modelId,contextWindow:context,inputModalities:input,releaseDate:/^\d{4}-\d{2}(-\d{2})?$/.test(date)?date:null,reasoning:row.reasoning===true,structuredOutput:typeof row.structured_output==='boolean'?row.structured_output:null,supportsTemperature:typeof row.temperature==='boolean'?row.temperature:null});
    }
    const compare=(a:string,b:string)=>a<b?-1:a>b?1:0;models.sort((a,b)=>compare(b.releaseDate||'',a.releaseDate||'')||compare(a.displayName,b.displayName));
    if(models.length)providers.push({id,name:string(item.name).trim().slice(0,120)||id,baseUrl,models});
  }
  providers.sort((a,b)=>a.name<b.name?-1:a.name>b.name?1:a.id<b.id?-1:a.id>b.id?1:0);
  if(!providers.length)throw Error('目录中没有可导入的对话提供商');return {providers,source,fetchedAt};
}
export function newCommunityProvider(selection:CommunityCatalogProvider,modelIds:string[]):ModelDraft{
  const ids=new Set(modelIds);if(!ids.size)throw Error('请至少选择一个模型');const models=selection.models.filter(m=>ids.has(m.modelId));if(models.length!==ids.size)throw Error('选中的模型已不在目录中，请重新选择');
  return {...emptyModelDraft(),name:selection.name,baseUrl:selection.baseUrl,isEnabled:false,apiKey:'',model:models[0].modelId,models:models.map(m=>m.modelId),metadata:models.map(m=>({id:m.modelId,name:m.displayName,contextWindow:m.contextWindow,vision:m.inputModalities.includes('image'),source:'catalog'}))};
}
export interface CatalogCache {raw:string;fetchedAt:number}
export interface CommunityCatalogPort {readCache():Promise<CatalogCache|null>;readSnapshot():Promise<string>;fetch():Promise<string>;writeCache(cache:CatalogCache):Promise<void>;cancel():void;}
export class CommunityCatalogRepository {
  constructor(private port:CommunityCatalogPort,private now:()=>number=Date.now){}
  async loadLocal():Promise<CommunityCatalog>{try{const cache=await this.port.readCache();if(cache)return parseCommunityCatalog(cache.raw,'cache',cache.fetchedAt);}catch(_){}return parseCommunityCatalog(await this.port.readSnapshot(),'snapshot');}
  async refresh():Promise<CommunityCatalog>{const raw=await this.port.fetch(),at=this.now();const value=parseCommunityCatalog(raw,'online',at);await this.port.writeCache({raw,fetchedAt:at});return value;}
  cancel():void{this.port.cancel();}
}
export interface CommunityCatalogState {catalog:CommunityCatalog|null;loading:boolean;refreshing:boolean;loadError:boolean;refreshError:string;importing:boolean;query:string;modelQuery:string;selectedIds:string[];selection:CommunityCatalogProvider|null;importError:string}
export function emptyCatalogState():CommunityCatalogState{return{catalog:null,loading:false,refreshing:false,loadError:false,refreshError:'',importing:false,query:'',modelQuery:'',selectedIds:[],selection:null,importError:''};}
export class CommunityCatalogStore {
  state=emptyCatalogState();private loadJob:Promise<void>|undefined;private refreshJob:Promise<void>|undefined;private lastAutomaticAttempt:number|null=null;
  constructor(private repository:CommunityCatalogRepository,private add:(draft:ModelDraft)=>Promise<string>,private changed:()=>void,private now:()=>number=Date.now){}
  snapshot():CommunityCatalogState{return{...this.state,selectedIds:this.state.selectedIds.slice()};}
  async openCatalog():Promise<void>{await this.ensureLoaded();const now=this.now(),c=this.state.catalog;const stale=!c||c.source==='snapshot'||c.fetchedAt===null||c.fetchedAt>now||now-c.fetchedAt>=86400000;if(stale&&(this.lastAutomaticAttempt===null||now<this.lastAutomaticAttempt||now-this.lastAutomaticAttempt>=3600000)){this.lastAutomaticAttempt=now;void this.refresh();}}
  async ensureLoaded():Promise<void>{if(this.state.catalog)return;if(this.loadJob)return this.loadJob;this.loadJob=(async()=>{this.state.loading=true;this.state.loadError=false;this.changed();try{this.state.catalog=await this.repository.loadLocal();}catch(_){this.state.loadError=true;}finally{this.state.loading=false;this.loadJob=undefined;this.changed();}})();return this.loadJob;}
  async refresh():Promise<void>{if(this.refreshJob)return this.refreshJob;this.refreshJob=(async()=>{this.state.refreshing=true;this.state.refreshError='';this.changed();try{this.state.catalog=await this.repository.refresh();this.state.loadError=false;}catch(_){this.state.refreshError='在线目录更新失败，仍可使用当前目录';}finally{this.state.refreshing=false;this.refreshJob=undefined;this.changed();}})();return this.refreshJob;}
  search(query:string):void{this.state.query=query;this.changed();}
  providers():CommunityCatalogProvider[]{const q=this.state.query.trim().toLowerCase();return this.state.catalog?.providers.filter(p=>[p.name,p.id,p.baseUrl].some(v=>v.toLowerCase().includes(q)))||[];}
  choose(id:string):void{const p=this.state.catalog?.providers.find(p=>p.id===id);this.state.selection=p?{...p,models:p.models.map(m=>({...m,inputModalities:m.inputModalities.slice()}))}:null;this.state.selectedIds=[];this.state.modelQuery='';this.state.importError='';this.changed();}
  searchModels(query:string):void{this.state.modelQuery=query;this.changed();}
  models():CommunityCatalogModel[]{const q=this.state.modelQuery.trim().toLowerCase();return this.state.selection?.models.filter(m=>[m.displayName,m.modelId].some(v=>v.toLowerCase().includes(q)))||[];}
  toggle(id:string):void{if(this.state.importing)return;this.state.selectedIds=this.state.selectedIds.includes(id)?this.state.selectedIds.filter(x=>x!==id):this.state.selectedIds.concat(id);this.changed();}
  selectVisible():void{if(this.state.importing)return;this.state.selectedIds=[...new Set([...this.state.selectedIds,...this.models().map(m=>m.modelId)])];this.changed();}
  clear():void{if(this.state.importing)return;this.state.selectedIds=[];this.changed();}
  async importProvider():Promise<string|null>{if(this.state.importing||!this.state.selection)return null;this.state.importing=true;this.state.importError='';const selected=this.state.selection,ids=this.state.selectedIds.slice();this.changed();try{return await this.add(newCommunityProvider(selected,ids));}catch(_){this.state.importError='保存提供商失败，请重试';return null;}finally{this.state.importing=false;this.changed();}}
}
