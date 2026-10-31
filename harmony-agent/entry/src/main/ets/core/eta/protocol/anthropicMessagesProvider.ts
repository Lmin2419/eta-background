// Ported from app/src/main/kotlin/io/github/mangi/eta/agent/model/AnthropicMessagesProvider.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { JsonObject, ProviderRequest, ProviderModelConfig, ProviderRunControl, ProviderEventSink, ProviderUsage, AssistantBlockKind, array, object, string, integer, copy, effectiveRequest } from './providerTypes';
import { ProviderStreamingTransport } from './openAiResponsesProvider';
import { ProviderSseReader } from './providerSseReader';
import { AgentModelFailure } from './agentModelFailure';
import { ProviderUrls } from './providerUrls';
import { mergeProviderHeaders } from './providerRequestHeaders';
import { mergeCustomBody } from './requestBodyMerge';
import { applyAnthropicReasoning } from './providerReasoning';
import { providerMessageText } from './providerMessageContent';
export const ANTHROPIC_CONTENT_BLOCKS='_eta_anthropic_content_blocks';
export function anthropicContentBlocks(m:JsonObject):JsonObject[]|null{return Array.isArray(m[ANTHROPIC_CONTENT_BLOCKS])?m[ANTHROPIC_CONTENT_BLOCKS] as JsonObject[]:null;}
export function attachAnthropicBlocks(m:JsonObject,blocks:JsonObject[]):void {m[ANTHROPIC_CONTENT_BLOCKS]=copy(blocks);}
function parseJsonObject(raw:string):JsonObject{try{return object(JSON.parse(raw||'{}'))||{};}catch(_){return {};}}
function breakpoint(block:JsonObject):JsonObject{block.cache_control={type:'ephemeral'};return block;}
function userContent(content:unknown):JsonObject[]{
 if(!Array.isArray(content))return [{type:'text',text:providerMessageText(content)}];const blocks:JsonObject[]=[];
 for(const value of content){const item=object(value);if(!item)continue;
  if(item.type==='text'&&string(item.text).trim())blocks.push({type:'text',text:string(item.text)});
  else if(item.type==='image_url'){
   const url=string(object(item.image_url)?.url),comma=url.indexOf(',');if(!/^data:/i.test(url)||comma<=5)continue;
   blocks.push({type:'image',source:{type:'base64',media_type:url.slice(5,comma).split(';')[0]||'image/png',data:url.slice(comma+1)}});
  }
 }
 return blocks.length?blocks:[{type:'text',text:''}];
}
function assistantContent(message:JsonObject,preserve:boolean):JsonObject[]{
 const opaque=anthropicContentBlocks(message);if(preserve&&opaque)return copy(opaque);
 const content:JsonObject[]=[],text=providerMessageText(message.content);if(text.trim()&&text!=='null')content.push({type:'text',text});
 array(message.tool_calls).forEach((value,index)=>{const call=object(value),fn=object(call?.function);if(!call||!fn)return;
  content.push({type:'tool_use',id:string(call.id).trim()?string(call.id):'tool_call_'+index,name:string(fn.name),input:parseJsonObject(string(fn.arguments))});
 });return content.length?content:[{type:'text',text:''}];
}
function appendTurn(messages:JsonObject[],role:string,content:JsonObject[]):void{
 const previous=messages[messages.length-1];if(previous?.role===role)(previous.content as JsonObject[]).push(...content);else messages.push({role,content});
}
function toolsContent(tools:JsonObject[]):JsonObject[]{return tools.flatMap(tool=>{
 const fn=object(tool.function);if(!fn||!string(fn.name).trim())return [];
 const schema=copy(object(fn.parameters)||{type:'object'});for(const key of ['oneOf','anyOf','allOf'])delete schema[key];
 return [{name:string(fn.name),description:string(fn.description),input_schema:schema}];
});}
export function buildAnthropicRequest(config:ProviderModelConfig,input:JsonObject[],tools:JsonObject[]):JsonObject{
 const system:string[]=[],messages:JsonObject[]=[];let currentStart=-1;
 for(let i=input.length-1;i>=0;i--)if(input[i].role==='user'&&!input[i]._eta_observation){currentStart=i;break;}
 input.forEach((m,index)=>{
  if(m.role==='system'){const text=providerMessageText(m.content);if(text.trim())system.push(text);}
  else if(m.role==='user')appendTurn(messages,'user',userContent(m.content).map(b=>m._eta_observation?{...b,_eta_transient:true}:b));
  else if(m.role==='assistant')appendTurn(messages,'assistant',assistantContent(m,index>currentStart));
  else if(m.role==='tool')appendTurn(messages,'user',[{type:'tool_result',tool_use_id:string(m.tool_call_id),content:string(m.content)}]);
 });
 const body:JsonObject={model:config.model,max_tokens:['claude-fable-5-1','claude-opus-5-5'].includes(config.model.trim().toLowerCase())?16384:4096,stream:true,messages};
 const text=system.join('\n\n').trim();if(text)body.system=[breakpoint({type:'text',text})];const converted=toolsContent(tools);
 if(converted.length){breakpoint(converted[converted.length-1]);body.tools=converted;}
 const last=messages[messages.length-1];if(last){const blocks=last.content as JsonObject[];const target=blocks.slice().reverse().find(b=>!b._eta_transient&&(b.type==='text'?!!string(b.text):['image','tool_result','tool_use','document'].includes(string(b.type))));if(target)breakpoint(target);}
 for(const m of messages)for(const b of m.content as JsonObject[])delete b._eta_transient;
 mergeCustomBody(body,config.customBody||[]);const explicit=config.customBody?.some(b=>b.key==='max_tokens')?body.max_tokens:undefined;
 applyAnthropicReasoning(body,config);if(explicit!==undefined&&explicit!==null)body.max_tokens=explicit;return body;
}
interface Block {index:number;type:string;id:string;name:string;stopped:boolean;initial:JsonObject;signature:string;text:string;thinking:string;args:string;}
function newBlock(index:number,type:string):Block{return {index,type,id:'',name:'',stopped:false,initial:{},signature:'',text:'',thinking:'',args:''};}
function kind(b:Block):AssistantBlockKind|null{return b.type==='text'?'TEXT':b.type==='thinking'?'THINKING':b.type==='tool_use'?'TOOL_CALL':null;}
function content(b:Block):string{return b.type==='text'?b.text:b.type==='thinking'?b.thinking:b.args;}
function usage(value:JsonObject|null):ProviderUsage|null {
 if(!value)return null;const number=(key:string):number|null=>{const n=integer(value[key]);return n>=0?n:null;},input=number('input_tokens');
 const u:ProviderUsage={contextTokens:input===null?null:input+(number('cache_read_input_tokens')||0)+(number('cache_creation_input_tokens')||0),inputTokens:input,outputTokens:number('output_tokens'),reasoningTokens:number('thinking_output_tokens'),cachedTokens:number('cache_read_input_tokens')};
 return Object.values(u).every(v=>v===null)?null:u;
}
export class AnthropicMessagesStream {
 private reader:ProviderSseReader;private blocks=new Map<number,Block>();private text='';private reasoning='';private stopped=false;private finishReason='';private stopDetails:JsonObject|null=null;private usage:ProviderUsage|null=null;private result:JsonObject|null=null;
 constructor(private control:ProviderRunControl,private emit:ProviderEventSink){this.reader=new ProviderSseReader(control,(event,payload)=>this.frame(event,payload.trim()));}
 feed(chunk:string):boolean{return this.reader.feed(chunk);}
 private append(b:Block,value:string):void{if(!value)return;const k=kind(b);if(k==='TEXT'){b.text+=value;this.text+=value;}else if(k==='THINKING'){b.thinking+=value;this.reasoning+=value;}else return;this.emit({type:'BlockDelta',kind:k,index:b.index,delta:value});}
 private updateUsage(delta:ProviderUsage|null):void{if(!delta)return;const prior=this.usage;this.usage={contextTokens:delta.contextTokens??prior?.contextTokens??null,inputTokens:delta.inputTokens??prior?.inputTokens??null,outputTokens:delta.outputTokens??prior?.outputTokens??null,reasoningTokens:delta.reasoningTokens??prior?.reasoningTokens??null,cachedTokens:delta.cachedTokens??prior?.cachedTokens??null};this.emit({type:'Usage',usage:this.usage,contextInputTokens:this.usage.contextTokens});}
 private frame(event:string,payload:string):boolean{
  this.control.check();if(payload==='[DONE]'){this.stopped=true;return false;}let json:JsonObject|null;try{json=object(JSON.parse(payload));}catch(_){throw Error('Anthropic SSE 返回无效 JSON');}if(!json)throw Error('Anthropic SSE 返回无效 JSON');
  const type=string(json.type).trim()||event,index=integer(json.index,0);
  if(type==='error')throw AgentModelFailure.stream(object(json.error)||{});
  if(type==='message_start')this.updateUsage(usage(object(object(json.message)?.usage)));
  if(type==='content_block_start'){
   const raw=object(json.content_block)||{},b={...newBlock(index,string(raw.type)),id:string(raw.id),name:string(raw.name),initial:copy(raw),signature:string(raw.signature)};
   const input=object(raw.input);if(input&&Object.keys(input).length)b.args=JSON.stringify(input);this.blocks.set(index,b);const k=kind(b);
   if(k)this.emit({type:'BlockStart',kind:k,index,blockId:k==='TOOL_CALL'?b.id||null:null,name:k==='TOOL_CALL'?b.name||null:null});
   if(b.type==='text')this.append(b,typeof raw.text==='string'?raw.text:'');if(b.type==='thinking')this.append(b,typeof raw.thinking==='string'?raw.thinking:'');
  }else if(type==='content_block_delta'){
   const delta=object(json.delta)||{},dt=string(delta.type);let b=this.blocks.get(index);
   if(dt==='text_delta'||dt==='thinking_delta'){if(!b){b=newBlock(index,dt==='text_delta'?'text':'thinking');this.blocks.set(index,b);}const value=delta[dt==='text_delta'?'text':'thinking'];this.append(b,typeof value==='string'?value:'');}
   else if(dt==='signature_delta'){if(b)b.signature+=string(delta.signature);}
   else if(dt==='input_json_delta'){const value=string(delta.partial_json);if(value){if(!b){b=newBlock(index,'tool_use');this.blocks.set(index,b);}b.args+=value;this.emit({type:'BlockDelta',kind:'TOOL_CALL',index,delta:value});}}
  }else if(type==='content_block_stop'){
   const b=this.blocks.get(index);if(b&&!b.stopped){b.stopped=true;const k=kind(b);if(k)this.emit({type:'BlockEnd',kind:k,index,blockId:b.id||null,name:b.name||null,content:content(b),replaceContent:false});}
  }else if(type==='message_delta'){
   const delta=object(json.delta);if(string(delta?.stop_reason).trim())this.finishReason=string(delta?.stop_reason);if(object(delta?.stop_details))this.stopDetails=object(delta?.stop_details);this.updateUsage(usage(object(json.usage)));
  }else if(type==='message_stop')this.stopped=true;
  return !this.stopped;
 }
 finish():JsonObject{
  if(this.result)return this.result;this.reader.finish();this.control.check();if(!this.stopped)throw AgentModelFailure.incompleteStream('Anthropic SSE 流未正常结束');
  const blocks=[...this.blocks.values()].sort((a,b)=>a.index-b.index);if(blocks.some(b=>['text','thinking','redacted_thinking','tool_use'].includes(b.type)&&!b.stopped))throw AgentModelFailure.incompleteStream('Anthropic SSE 内容块未正常结束');
  const calls=blocks.filter(b=>b.type==='tool_use'&&b.name.trim());if(calls.length&&blocks.some(b=>b.type==='thinking'&&!b.signature))throw AgentModelFailure.incompleteStream('Anthropic SSE 工具回合缺少思考签名');
  const result:JsonObject={role:'assistant',content:this.text,reasoning_content:this.reasoning,finish_reason:this.finishReason};if(this.stopDetails)result.stop_details=copy(this.stopDetails);
  if(this.usage){const u=this.usage,raw:JsonObject={};for(const [key,v] of [['input_tokens',u.inputTokens],['output_tokens',u.outputTokens],['reasoning_tokens',u.reasoningTokens],['cached_tokens',u.cachedTokens]] as const)if(v!==null)raw[key]=v;result.usage=raw;}
  if(calls.length){const ids=new Map<number,string>();result.tool_calls=calls.map((b,i)=>{const id=b.id.trim()?b.id:'tool_call_'+i;ids.set(b.index,id);return{id,type:'function',function:{name:b.name,arguments:b.args.trim()?b.args:'{}'}};});
   attachAnthropicBlocks(result,blocks.flatMap(b=>{
    const raw=copy(b.initial);if(b.type==='text')return [{...raw,text:b.text}];if(b.type==='thinking'){raw.thinking=b.thinking;if(b.signature)raw.signature=b.signature;else delete raw.signature;return [raw];}
    if(b.type==='redacted_thinking')return [raw];if(b.type==='tool_use')return [{...raw,id:ids.get(b.index)??b.id,name:b.name,input:parseJsonObject(b.args)}];return [];
   }));
  }
  this.result=result;return result;
 }
}
export async function completeAnthropicMessages(input:ProviderRequest,control:ProviderRunControl,transport:ProviderStreamingTransport,emit:ProviderEventSink):Promise<JsonObject>{
 const request=effectiveRequest(input),c=request.config,headers:Record<string,string>={'Content-Type':'application/json; charset=utf-8',Accept:'text/event-stream','anthropic-version':c.anthropicVersion||'2023-06-01'};
 if(/[\r\n\u0000]/.test(c.apiKey))throw Error('API Key 包含无效字符');if(c.anthropicVersion&&!/^[-A-Za-z0-9_.]{1,64}$/.test(c.anthropicVersion))throw Error('anthropic-version 格式无效');
 if(c.apiKey.trim())headers['x-api-key']=c.apiKey;mergeProviderHeaders(headers,c.baseUrl,c.customHeaders||[],request.sessionId);
 const body=buildAnthropicRequest(c,request.messages,request.tools),stream=new AnthropicMessagesStream(control,emit);control.check();emit({type:'RequestStarted'});
 await transport.stream(ProviderUrls.anthropicMessagesUrl(c.baseUrl),headers,JSON.stringify(body),chunk=>stream.feed(chunk),httpCode=>{control.check();emit({type:'ResponseHeaders',httpCode});});
 control.check();const result=stream.finish();emit({type:'Completed',reason:string(result.finish_reason)||null});return result;
}
