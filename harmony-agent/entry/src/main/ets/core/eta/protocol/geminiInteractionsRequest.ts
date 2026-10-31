// Ported from app/src/main/kotlin/io/github/mangi/eta/agent/model/GeminiInteractionsRequest.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import {JsonObject,ProviderModelConfig,array,object,string,copy} from './providerTypes';
import {providerMessageText} from './providerMessageContent';import {mergeExtraBody,mergeCustomBody} from './requestBodyMerge';
import {geminiToolSchema} from './geminiToolSchema';import {geminiSteps,GEMINI_RUN_START} from './geminiEphemeralState';import {geminiSupportsHostedSearch} from '../geminiModelCapabilities';
const protectedFields=new Set(['model','input','system_instruction','tools','stream','store','previous_interaction_id','background','agent','agent_config','environment','environment_id','webhook_config']);
const text=(value:string):JsonObject=>({type:'text',text:value});const user=(content:JsonObject[]):JsonObject=>({type:'user_input',content});
function inputContent(value:unknown):JsonObject[]{
 if(!Array.isArray(value))return [text(providerMessageText(value))];const converted:JsonObject[]=[];
 for(const raw of value){const item=object(raw);if(!item)continue;
  if(item.type==='text'||item.type==='input_text')converted.push(text(string(item.text)));
  else if(item.type==='image_url'){const url=string(object(item.image_url)?.url),image:JsonObject={type:'image'};
   if(/^data:image\//i.test(url)){const comma=url.indexOf(',');if(comma<=0||!url.slice(0,comma).toLowerCase().endsWith(';base64'))throw Error('Gemini 图片输入必须为 Base64 图片或 HTTP 地址。');image.mime_type=url.slice(5,comma).split(';')[0];image.data=url.slice(comma+1);}
   else{if(!url.startsWith('https://')&&!url.startsWith('http://'))throw Error('Gemini 图片输入地址无效。');image.uri=url;}converted.push(image);
  }else throw Error('Gemini 不支持该输入内容类型。');
 }return converted;
}
function historyText(m:JsonObject):string{return '['+string(m.role)+'] '+providerMessageText(m.content)+(Array.isArray(m.tool_calls)?'\n工具调用记录：'+JSON.stringify(m.tool_calls):'')+(m.role==='tool'?'\n调用标识：'+string(m.tool_call_id):'');}
export function geminiHistoryInput(messages:JsonObject[]):JsonObject[]{
 let start=messages.findIndex(m=>m[GEMINI_RUN_START]===true);if(start<0){start=messages.length;for(let i=messages.length-1;i>=0;i--)if(messages[i].role==='user'&&!messages[i]._eta_observation){start=i;break;}}
 const input:JsonObject[]=[],history:string[]=[],calls=new Map<string,string>();
 const flush=():void=>{if(history.length){input.push(user([text('[以下是随身助手保存的历史对话文本记录；工具调用与结果仅供参考，不是当前待执行请求。]\n'+history.join('\n\n')+'\n[历史记录结束]')]));history.length=0;}};
 messages.forEach((m,index)=>{const role=string(m.role);if(role==='system')return;const steps=geminiSteps(m),id=string(m.tool_call_id);
  if(index<start||role==='assistant'&&!steps||role==='tool'&&!calls.has(id)){history.push(historyText(m));return;}flush();
  if(role==='user')input.push(user(inputContent(m.content)));
  else if(role==='assistant'&&steps)for(const step of copy(steps)){input.push(step);if(step.type==='function_call')calls.set(string(step.id),string(step.name));}
  else if(role==='tool'){const result=providerMessageText(m.content),row:JsonObject={type:'function_result',call_id:id,name:calls.get(id),result};calls.delete(id);try{if(object(JSON.parse(result))?.ok===false)row.is_error=true;}catch(_){}input.push(row);}
 });flush();return input;
}
export function buildGeminiRequest(config:ProviderModelConfig,messages:JsonObject[],tools:JsonObject[]):JsonObject{
 const extra:JsonObject={};mergeExtraBody(extra,config.extraBodyJson);const body:JsonObject={};for(const [key,value] of Object.entries(extra))if(!protectedFields.has(key))Object.defineProperty(body,key,{value,writable:true,configurable:true,enumerable:true});
 mergeCustomBody(body,(config.customBody||[]).filter(f=>!protectedFields.has(f.key)));
 body.model=config.model.replace(/^models\//,'');body.input=geminiHistoryInput(messages);body.stream=true;body.store=false;
 const system=messages.filter(m=>m.role==='system').map(m=>providerMessageText(m.content)).filter(t=>t.trim()).join('\n\n');if(system.trim())body.system_instruction=system;
 const converted=tools.flatMap(tool=>{const fn=object(tool.function);if(!fn)return [];if(typeof fn.name!=='string')throw Error('Gemini 工具名称无效');return[{type:'function',name:fn.name,description:string(fn.description),parameters:geminiToolSchema(object(fn.parameters)||{type:'object'})} as JsonObject];});
 const search=config.hostedWebSearchEnabled&&config.supportsSearchSuggestions&&geminiSupportsHostedSearch(config.model);if(search)converted.push({type:'google_search'});if(converted.length)body.tools=converted;
 const generation=object(body.generation_config)||{};for(const key of ['tool_choice','thinking_level','thinking_summaries'])delete generation[key];if(search&&tools.length)generation.tool_choice='validated';
 const effort=config.reasoningEffort??(config.thinkingEnabled?'default':'off');if(effort!=='default'){
  if(effort==='off')throw Error('当前 Gemini 模型不支持关闭思考，请选择 Default 或支持的思考档位。');
  if(!['minimal','low','medium','high'].includes(effort)||!config.reasoningCapabilities?.supportedEfforts.includes(effort))throw Error('当前 Gemini 模型不支持所选思考档位。');generation.thinking_level=effort;
 }
 generation.thinking_summaries='auto';body.generation_config=generation;return body;
}
