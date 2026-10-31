import { customHeaderError, ProviderHeaderDraft } from './eta/providerConfigDraft';
export interface ModelMetadata {id:string;name:string;contextWindow?:number;vision?:boolean;source:'provider'|'catalog'|'custom';enabled?:boolean}
export function metadataFor(row:any,id:string):ModelMetadata{
 const n=row.context_length??row.context_window??row.inputTokenLimit;
 const modalities=row.architecture?.input_modalities??row.input_modalities;
 return {id,name:typeof row.name==='string'?row.name.slice(0,160):typeof row.display_name==='string'?row.display_name.slice(0,160):id,contextWindow:typeof n==='number'&&Number.isInteger(n)&&n>=1000&&n<=10000000?n:undefined,vision:Array.isArray(modalities)?modalities.includes('image'):undefined,source:'provider'};
}
export function safeHeaders(value:unknown):Record<string,string>{
 if(value===undefined)return {};if(!value||typeof value!=='object'||Array.isArray(value))throw new Error('附加请求头必须为 JSON 对象');
 const rows:ProviderHeaderDraft[]=Object.entries(value).map(([name,v],index)=>{if(typeof v!=='string')throw Error('请求头值必须为字符串');return{id:String(index),name,value:v};});
 const error=customHeaderError(rows);if(error)throw Error(error);
 return Object.fromEntries(rows.map(h=>[h.name.trim(),h.value]));
}
export function parseMetadata(value:unknown):ModelMetadata[]{
 if(value===undefined)return [];if(!Array.isArray(value)||value.length>5000)throw new Error('模型元信息无效');
 return value.map(m=>{if(!m||typeof m.id!=='string'||m.id.length>256||typeof m.name!=='string'||m.name.length>160)throw new Error('模型元信息无效');return {id:m.id,name:m.name,contextWindow:typeof m.contextWindow==='number'&&Number.isFinite(m.contextWindow)?m.contextWindow:undefined,vision:typeof m.vision==='boolean'?m.vision:undefined,enabled:typeof m.enabled==='boolean'?m.enabled:undefined,source:m.source==='catalog'||m.source==='custom'?m.source:'provider'};});
}
