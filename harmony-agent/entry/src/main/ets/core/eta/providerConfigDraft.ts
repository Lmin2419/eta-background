// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/pages/providers/ProviderConfigDraft.kt,
// ProviderModelsTab.kt and agent/model/CustomHeaderFilter.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import type { ModelMetadata } from '../modelMetadata';
import { ModelSearchQuery } from './modelPicker';
export interface ProviderHeaderDraft {id:string;name:string;value:string}
export interface ProviderConfigDraft {
  name:string;baseUrl:string;apiKey:string;systemPrompt:string;isEnabled:boolean;endpointMode:string;
  hostedWebSearchEnabled:boolean;anthropicVersion:string;headers:ProviderHeaderDraft[];authMode:string;
}
/** In-memory recreation only. The Harmony disk draft explicitly removes credentials. */
export function saveProviderConfigDraft(draft:ProviderConfigDraft):ProviderConfigDraft{return {...draft,headers:draft.headers.map(h=>({...h}))};}
export function restoreProviderConfigDraft(saved:ProviderConfigDraft):ProviderConfigDraft{return saveProviderConfigDraft(saved);}
const forbidden=new Set(['host','content-length','connection','transfer-encoding','content-encoding','accept-encoding','expect','keep-alive','proxy-connection','upgrade','authorization','x-api-key','x-goog-api-key','anthropic-version']);
export function customHeaderError(headers:ProviderHeaderDraft[]):string|null{
  const names=new Set<string>();
  for(let index=0;index<headers.length;index++){
    const h=headers[index],name=h.name.trim(),prefix='第 '+(index+1)+' 个请求头';
    if(!name||forbidden.has(name.toLowerCase()))return prefix+'名称为空或由系统管理';
    if(!/^[!#$%&'*+.^_`|~0-9A-Za-z-]+$/.test(name))return prefix+'名称包含无效字符';
    if(/[^\t\x20-\x7e]/.test(h.value))return prefix+'值只能包含可打印的 ASCII 字符或制表符';
    if(names.has(name.toLowerCase()))return prefix+'名称重复（不区分大小写）';names.add(name.toLowerCase());
  }
  return null;
}
export function headersRecord(headers:ProviderHeaderDraft[]):Record<string,string>{const error=customHeaderError(headers);if(error)throw Error(error);return Object.fromEntries(headers.map(h=>[h.name.trim(),h.value]));}
export function contextWindowInputError(value:string,errorMessage='Context window must be a positive integer'):string|null{
 const v=value.trim();return /^\+?\d+$/.test(v)&&Number(v)>0&&Number(v)<=2147483647?null:errorMessage;
}
export const contextWindowPresets=[128000,200000,256000,1000000];
export interface ProviderModelUi {id:string;modelId:string;displayName:string;sortOrder:number;enabled:boolean;metadata?:ModelMetadata;contextWindow?:number;vision?:boolean}
export function filterProviderModels(models:ProviderModelUi[],query:string):ProviderModelUi[]{const search=new ModelSearchQuery(query);return models.slice().sort((a,b)=>a.sortOrder-b.sortOrder).filter(m=>search.matches([m.displayName,m.modelId]));}
