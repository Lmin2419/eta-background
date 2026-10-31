// Ported capability metadata from app/src/main/kotlin/io/github/mangi/eta/data/model/Model.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { ModelReasoningCapabilities, REASONING_EFFORTS, ReasoningEffort, object } from './eta/protocol/providerTypes';
import { customHeaderError, ProviderHeaderDraft } from './eta/providerConfigDraft';
export interface ModelMetadata {id:string;name:string;contextWindow?:number;vision?:boolean;source:'provider'|'catalog'|'custom';enabled?:boolean;catalogId?:string;sortOrder?:number;catalogContextWindow?:number;ownedBy?:string;inputModalities?:string[];outputModalities?:string[];attachment?:boolean;toolCall?:boolean;reasoning?:boolean;structuredOutput?:boolean;supportsTemperature?:boolean;reasoningOverride?:boolean;reasoningCapabilities?:ModelReasoningCapabilities;reasoningCapabilitiesOverride?:ModelReasoningCapabilities}
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
 return value.map(m=>{if(!m||typeof m.id!=='string'||m.id.length>256||typeof m.name!=='string'||m.name.length>160)throw new Error('模型元信息无效');return {...capabilityMetadata(m),id:m.id,name:m.name,contextWindow:typeof m.contextWindow==='number'&&Number.isFinite(m.contextWindow)?m.contextWindow:undefined,vision:typeof m.vision==='boolean'?m.vision:undefined,enabled:typeof m.enabled==='boolean'?m.enabled:undefined,source:m.source==='catalog'||m.source==='custom'?m.source:'provider'};});
}

function capability(value:unknown):ModelReasoningCapabilities|undefined {
 const v=object(value);if(!v)return undefined;
 if(!Array.isArray(v.supportedEfforts)||v.supportedEfforts.some(e=>!REASONING_EFFORTS.includes(e as ReasoningEffort)))throw Error('模型思考档位无效');
 const result:ModelReasoningCapabilities={supportedEfforts:(v.supportedEfforts as ReasoningEffort[]).slice(),defaultEffort:v.defaultEffort===null?null:REASONING_EFFORTS.includes(v.defaultEffort as ReasoningEffort)?v.defaultEffort as ReasoningEffort:undefined,
 defaultEnabled:v.defaultEnabled===null?null:bool(v.defaultEnabled),mandatory:bool(v.mandatory),canDisable:bool(v.canDisable),supportsBudget:bool(v.supportsBudget),maxBudgetTokens:v.maxBudgetTokens===null?null:positive(v.maxBudgetTokens),supportsMaxTokens:v.supportsMaxTokens===null?null:bool(v.supportsMaxTokens)};
 return Object.fromEntries(Object.entries(result).filter(([,value])=>value!==undefined)) as unknown as ModelReasoningCapabilities;
}
function bool(v:unknown):boolean|undefined{return typeof v==='boolean'?v:undefined;}
function positive(v:unknown):number|undefined{return typeof v==='number'&&Number.isSafeInteger(v)&&v>0&&v<=2147483647?v:undefined;}
function modalities(v:unknown):string[]|undefined{return Array.isArray(v)?v.filter(x=>typeof x==='string'&&x.length<=32).slice(0,20):undefined;}
function capabilityMetadata(v:Record<string,unknown>):Partial<ModelMetadata>{return {catalogId:typeof v.catalogId==='string'?v.catalogId.slice(0,256):undefined,sortOrder:typeof v.sortOrder==='number'?v.sortOrder:undefined,
 catalogContextWindow:positive(v.catalogContextWindow),ownedBy:typeof v.ownedBy==='string'?v.ownedBy.slice(0,160):undefined,inputModalities:modalities(v.inputModalities),outputModalities:modalities(v.outputModalities),attachment:bool(v.attachment),toolCall:bool(v.toolCall),reasoning:bool(v.reasoning),structuredOutput:bool(v.structuredOutput),supportsTemperature:bool(v.supportsTemperature),reasoningOverride:bool(v.reasoningOverride),reasoningCapabilities:capability(v.reasoningCapabilities),reasoningCapabilitiesOverride:capability(v.reasoningCapabilitiesOverride)};}
