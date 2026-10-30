export interface ModelMetadata {id:string;name:string;contextWindow?:number;vision?:boolean;source:'provider'}
export function metadataFor(row:any,id:string):ModelMetadata{
 const n=row.context_length??row.context_window??row.inputTokenLimit;
 const modalities=row.architecture?.input_modalities??row.input_modalities;
 return {id,name:typeof row.name==='string'?row.name.slice(0,160):typeof row.display_name==='string'?row.display_name.slice(0,160):id,contextWindow:typeof n==='number'&&Number.isInteger(n)&&n>=1000&&n<=10000000?n:undefined,vision:Array.isArray(modalities)?modalities.includes('image'):undefined,source:'provider'};
}
export function safeHeaders(value:unknown):Record<string,string>{
 if(value===undefined)return {};if(!value||typeof value!=='object'||Array.isArray(value))throw new Error('附加请求头必须为 JSON 对象');
 const allowed=['anthropic-beta','openai-project','openai-organization','x-client-name'],result:Record<string,string>={};
 for(const [name,v] of Object.entries(value)){if(!allowed.includes(name.toLowerCase())||typeof v!=='string'||v.length>512||/[\r\n\0]/.test(v))throw new Error('只允许非密钥请求头：'+allowed.join('、'));result[name]=v;}return result;
}
export function parseMetadata(value:unknown):ModelMetadata[]{
 if(value===undefined)return [];if(!Array.isArray(value)||value.length>5000)throw new Error('模型元信息无效');
 return value.map(m=>{if(!m||typeof m.id!=='string'||m.id.length>256||typeof m.name!=='string'||m.name.length>160)throw new Error('模型元信息无效');return {id:m.id,name:m.name,contextWindow:typeof m.contextWindow==='number'&&Number.isFinite(m.contextWindow)?m.contextWindow:undefined,vision:typeof m.vision==='boolean'?m.vision:undefined,source:'provider'};});
}
