import { TokenUsage, usageOf } from './usage';
import { safeHeaders } from './modelMetadata';
import type { HttpTransport, ModelSettings } from './model';
import { Message, ModelReply, RunControl, ToolCall, ContentPart } from './types';

export type ModelProtocol='chat_completions'|'responses'|'anthropic'|'gemini';
export function protocolName(protocol?:ModelProtocol):string{return {chat_completions:'OpenAI Chat Completions',responses:'OpenAI Responses',anthropic:'Anthropic Messages',gemini:'Gemini GenerateContent'}[protocol||'chat_completions'];}
function image(part:ContentPart):{mime:string;data:string}|undefined{const match=/^data:(image\/(?:png|jpeg|webp));base64,([A-Za-z0-9+/=]+)$/.exec(part.image_url?.url||'');return match?{mime:match[1],data:match[2]}:undefined;}
function parts(message:Message):ContentPart[]{return typeof message.content==='string'?[{type:'text',text:message.content}]:message.content||[];}
function validCall(id:unknown,name:unknown,args:unknown):ToolCall{
  if(typeof id!=='string'||!id||id.length>256||typeof name!=='string'||!/^[a-zA-Z_][a-zA-Z0-9_-]{0,127}$/.test(name)||typeof args!=='string'||args.length>64000)throw new Error('模型工具调用格式无效');
  const parsed=JSON.parse(args);if(!parsed||typeof parsed!=='object'||Array.isArray(parsed))throw new Error('模型工具参数不是对象');
  return {id,type:'function',function:{name,arguments:args}};
}
function result(text:string,thinking:string,calls:ToolCall[],finish:string,protocol?:string,providerParts?:Record<string,unknown>[],usage?:TokenUsage):ModelReply{
  if(calls.length>8||new Set(calls.map(c=>c.id)).size!==calls.length)throw new Error('工具调用重复或超过上限');
  if(!text&&!calls.length)throw new Error('模型返回空回复');
  return {message:{role:'assistant',content:text||null,...(calls.length?{tool_calls:calls}:{}),providerProtocol:protocol,providerParts},finishReason:finish,reasoning:thinking,usage};
}
export function decodeProtocol(protocol:ModelProtocol,value:any):ModelReply{
  if(!value||value.error)throw new Error('模型服务返回错误');
  if(protocol==='anthropic'){
    if(value.type!=='message'||!Array.isArray(value.content))throw new Error('无效的 Anthropic 响应');
    const calls:ToolCall[]=[];let text='',thinking='';
    for(const b of value.content){if(b.type==='text'&&typeof b.text==='string')text+=b.text;else if(b.type==='thinking'&&typeof b.thinking==='string')thinking+=b.thinking;else if(b.type==='tool_use')calls.push(validCall(b.id,b.name,JSON.stringify(b.input)));}
    return result(text,thinking,calls,value.stop_reason==='max_tokens'?'length':value.stop_reason==='tool_use'?'tool_calls':['end_turn','stop_sequence'].includes(value.stop_reason)?'stop':'incomplete','anthropic',value.content,usageOf(value.usage));
  }
  if(protocol==='responses'){
    if(!Array.isArray(value.output))throw new Error('无效的 Responses 响应');
    let text='',thinking='';const calls:ToolCall[]=[];
    for(const item of value.output){
      if(item.type==='message')for(const p of item.content||[])if(p.type==='output_text'&&typeof p.text==='string')text+=p.text;
      if(item.type==='reasoning')for(const p of item.summary||[])if(typeof p.text==='string')thinking+=p.text;
      if(item.type==='function_call')calls.push(validCall(item.call_id,item.name,item.arguments));
    }
    return result(text,thinking,calls,value.status==='completed'?(calls.length?'tool_calls':'stop'):value.status==='incomplete'?'length':'incomplete','responses',value.output,usageOf(value.usage));
  }
  if(protocol==='gemini'){
    const c=value.candidates?.[0];if(!c?.content?.parts)throw new Error('模型没有返回可用 Gemini 内容');
    let text='',thinking='';const calls:ToolCall[]=[];
    for(const p of c.content.parts){if(typeof p.text==='string'){if(p.thought)thinking+=p.text;else text+=p.text;}if(p.functionCall)calls.push(validCall(p.functionCall.id||'gemini-'+calls.length,p.functionCall.name,JSON.stringify(p.functionCall.args||{})));}
    return result(text,thinking,calls,c.finishReason==='STOP'?(calls.length?'tool_calls':'stop'):c.finishReason==='MAX_TOKENS'?'length':'incomplete','gemini',c.content.parts,usageOf(value.usageMetadata));
  }
  throw new Error('不支持的模型协议');
}
export function protocolRequest(protocol:ModelProtocol,settings:ModelSettings,messages:Message[],tools:Record<string,unknown>[],stream:boolean):Record<string,unknown>{
  const schema=tools.map(t=>t.function as {name:string;description:string;parameters:Record<string,unknown>});
  const budget=settings.maxOutputTokens||4096;
  if(protocol==='anthropic'){
    const entries:any[]=[];
    for(const m of messages){if(m.role==='system')continue;
      if(m.role==='tool'){entries.push({role:'user',content:[{type:'tool_result',tool_use_id:m.tool_call_id,content:m.content||''}]});continue;}
      if(m.role==='assistant'&&m.providerProtocol===protocol&&m.providerParts){entries.push({role:'assistant',content:m.providerParts});continue;}
      const content:any[]=parts(m).map(p=>{const img=image(p);if(img)return {type:'image',source:{type:'base64',media_type:img.mime,data:img.data}};if(p.type!=='text')throw new Error('此协议仅支持直接图片附件');return {type:'text',text:p.text||''};});
      for(const c of m.tool_calls||[])content.push({type:'tool_use',id:c.id,name:c.function.name,input:JSON.parse(c.function.arguments)});
      if(content.length)entries.push({role:m.role,content});
    }
    return {model:settings.model,messages:entries,system:messages.filter(m=>m.role==='system').map(m=>m.content).join('\n'),max_tokens:budget,stream,...(schema.length?{tools:schema.map(t=>({name:t.name,description:t.description,input_schema:t.parameters}))}:{})};
  }
  if(protocol==='responses'){
    const input:any[]=[];
    for(const m of messages){
      if(m.role==='tool'){input.push({type:'function_call_output',call_id:m.tool_call_id,output:m.content||''});continue;}
      if(m.role==='assistant'&&m.providerProtocol===protocol&&m.providerParts){input.push(...m.providerParts);continue;}
      const content=parts(m).map(p=>p.type==='image_url'?{type:'input_image',image_url:p.image_url?.url}:{type:m.role==='assistant'?'output_text':'input_text',text:p.text||''});
      if(content.length)input.push({role:m.role==='system'?'developer':m.role,content});
      for(const c of m.tool_calls||[])input.push({type:'function_call',call_id:c.id,name:c.function.name,arguments:c.function.arguments});
    }
    return {model:settings.model,input,store:false,include:['reasoning.encrypted_content'],stream,max_output_tokens:budget,...(schema.length?{tools:schema.map(t=>({type:'function',name:t.name,description:t.description,parameters:t.parameters,strict:false}))}:{}),...(settings.reasoningEffort?{reasoning:{effort:settings.reasoningEffort,summary:'auto'}}:{})};
  }
  if(protocol==='gemini'){
    const entries:any[]=[];const toolNames=new Map<string,string>();
    for(const m of messages){if(m.role==='system')continue;
      if(m.role==='tool'){entries.push({role:'user',parts:[{functionResponse:{name:toolNames.get(m.tool_call_id||'')||'tool',response:{result:m.content||''}}}]});continue;}
      if(m.role==='assistant'&&m.providerProtocol===protocol&&m.providerParts){for(const c of m.tool_calls||[])toolNames.set(c.id,c.function.name);entries.push({role:'model',parts:m.providerParts});continue;}
      const content:any[]=parts(m).map(p=>{const img=image(p);if(img)return {inlineData:{mimeType:img.mime,data:img.data}};if(p.type!=='text')throw new Error('此协议仅支持直接图片附件');return {text:p.text||''};});
      for(const c of m.tool_calls||[]){toolNames.set(c.id,c.function.name);content.push({functionCall:{name:c.function.name,args:JSON.parse(c.function.arguments)}});}
      if(content.length)entries.push({role:m.role==='assistant'?'model':'user',parts:content});
    }
    return {contents:entries,systemInstruction:{parts:[{text:messages.filter(m=>m.role==='system').map(m=>m.content).join('\n')}]},generationConfig:{maxOutputTokens:budget},...(schema.length?{tools:[{functionDeclarations:schema.map(t=>({name:t.name,description:t.description,parameters:t.parameters}))}]}:{})};
  }
  throw new Error('不支持的模型协议');
}

/** Provider-specific event state. A closed stream without its completion event is never executable. */
export class ProtocolStream {
  private buffer='';private raw='';private data:string[]=[];private size=0;private mode='';
  private text='';private thinking='';private complete:ModelReply|undefined;
  private anthropicBlocks:Map<number,any>=new Map();private stopReason='';private inputTokens=0;private outputTokens=0;private geminiParts:any[]=[];
  constructor(private protocol:ModelProtocol,private onText:(text:string)=>void,private onReasoning:(text:string)=>void){}
  feed(chunk:string):void{
    this.size+=chunk.length;if(this.size>2_000_000)throw new Error('模型响应超过大小限制');
    this.buffer+=chunk;this.raw+=chunk;if(!this.mode){const c=this.buffer.trimStart()[0];if(!c)return;this.mode=c==='{'?'json':'sse';}if(this.mode==='json')return;
    let at:number;while((at=this.buffer.indexOf('\n'))>=0){const line=this.buffer.slice(0,at).replace(/\r$/,'');this.buffer=this.buffer.slice(at+1);if(!line){if(this.data.length){const payload=this.data.join('\n');this.data=[];if(payload!=='[DONE]')this.event(parseJson(payload));}}else if(line.startsWith('data:'))this.data.push(line.slice(5).replace(/^ /,''));}
  }
  private publish():void{if(this.text.length>32000||this.thinking.length>32000)throw new Error('模型输出超过上限');if(this.text)this.onText(this.text);if(this.thinking)this.onReasoning(this.thinking);}
  private event(v:any):void{
    if(v.error||v.type==='error')throw new Error('模型服务在流式生成时返回错误');
    if(this.complete)throw new Error('模型结束后又返回事件');
    if(this.protocol==='responses'){
      if(v.type==='response.output_text.delta'){if(typeof v.delta!=='string')throw new Error('文本增量无效');this.text+=v.delta;}
      if(v.type==='response.reasoning_summary_text.delta'){if(typeof v.delta!=='string')throw new Error('推理增量无效');this.thinking+=v.delta;}
      if(v.type==='response.completed'||v.type==='response.incomplete')this.complete=decodeProtocol('responses',v.response);
      if(v.type==='response.failed')throw new Error('Responses 请求失败');
    }else if(this.protocol==='anthropic'){
      if(v.type==='message_start')this.inputTokens=v.message?.usage?.input_tokens||0;
      if(v.type==='message_delta')this.outputTokens=v.usage?.output_tokens||0;
      if(v.type==='content_block_start'){if(!Number.isInteger(v.index)||v.index<0||v.index>64||this.anthropicBlocks.has(v.index))throw new Error('内容块序号无效');this.anthropicBlocks.set(v.index,{...v.content_block,partial:''});if(v.content_block?.text)this.text+=v.content_block.text;}
      if(v.type==='content_block_delta'){
        const b=this.anthropicBlocks.get(v.index);if(!b)throw new Error('收到没有开始事件的内容块');
        if(v.delta?.type==='text_delta'){if(typeof v.delta.text!=='string')throw new Error('文本增量无效');b.text=(b.text||'')+v.delta.text;this.text+=v.delta.text;}
        if(v.delta?.type==='thinking_delta'){if(typeof v.delta.thinking!=='string')throw new Error('推理增量无效');b.thinking=(b.thinking||'')+v.delta.thinking;this.thinking+=v.delta.thinking;}
        if(v.delta?.type==='signature_delta'){if(typeof v.delta.signature==='string')b.signature=(b.signature||'')+v.delta.signature;}
        if(v.delta?.type==='input_json_delta'){if(typeof v.delta.partial_json!=='string')throw new Error('工具增量无效');b.partial+=v.delta.partial_json;}
      }
      if(v.type==='content_block_stop'){const b=this.anthropicBlocks.get(v.index);if(!b)throw new Error('内容块结束序号无效');if(b.type==='tool_use'&&b.partial)b.input=parseJson(b.partial);b.closed=true;}
      if(v.type==='message_delta')this.stopReason=v.delta?.stop_reason||'';
      if(v.type==='message_stop'){
        if(!this.stopReason||Array.from(this.anthropicBlocks.values()).some(b=>!b.closed))throw new Error('Anthropic 内容块未完整结束');
        this.complete=decodeProtocol('anthropic',{type:'message',content:Array.from(this.anthropicBlocks.values()).map(b=>{const {partial,closed,...content}=b;return content;}),stop_reason:this.stopReason,usage:{input_tokens:this.inputTokens,output_tokens:this.outputTokens}});
      }
    }else{
      const c=v.candidates?.[0];if(!c){if(v.usageMetadata)return;throw new Error('Gemini 流缺少候选内容');}
      for(const p of c.content?.parts||[]){
        if(typeof p.text==='string'){if(p.thought)this.thinking+=p.text;else this.text+=p.text;}
        if(p.functionCall){if(this.geminiParts.some(old=>JSON.stringify(old.functionCall)===JSON.stringify(p.functionCall)))throw new Error('Gemini 工具块重复，未执行');this.geminiParts.push(p);}
      }
      if(c.finishReason)this.complete=decodeProtocol('gemini',{usageMetadata:v.usageMetadata,candidates:[{finishReason:c.finishReason,content:{parts:[{text:this.text},...(this.thinking?[{text:this.thinking,thought:true}]:[]),...this.geminiParts]}}]});
    }
    this.publish();
  }
  finish():ModelReply{
    if(this.mode==='json')this.complete=decodeProtocol(this.protocol,parseJson(this.raw));
    else if(this.buffer.trim()||this.data.length)throw new Error('模型流在事件中间中断');
    if(!this.complete)throw new Error('模型流缺少完整结束事件；未执行工具');
    if(typeof this.complete.message.content==='string')this.onText(this.complete.message.content);if(this.complete.reasoning)this.onReasoning(this.complete.reasoning);
    return this.complete;
  }
}

export async function completeProtocol(protocol:ModelProtocol,base:string,settings:ModelSettings,transport:HttpTransport,tools:Record<string,unknown>[],messages:Message[],control:RunControl,onText:(text:string)=>void,onReasoning:(text:string)=>void):Promise<ModelReply>{
  if(/[\r\n\0]/.test(settings.apiKey))throw new Error('API Key 包含无效字符');
  const headers:Record<string,string>={'Content-Type':'application/json',...safeHeaders(settings.extraHeaders)};
  if(settings.reasoningEffort&&['anthropic','gemini'].includes(protocol))throw new Error('当前 Anthropic/Gemini 适配器未映射推理档位，请清空该参数');
  if(settings.apiKey){if(protocol==='anthropic')headers['x-api-key']=settings.apiKey.trim();else if(protocol==='gemini')headers['x-goog-api-key']=settings.apiKey.trim();else headers.Authorization='Bearer '+settings.apiKey.trim();}
  if(protocol==='anthropic')headers['anthropic-version']='2023-06-01';
  const streaming=settings.streaming!==false&&Boolean(transport.stream);
  const model=settings.model.replace(/^models\//,'');if(!model||model.length>256)throw new Error('模型名称无效');
  const url=protocol==='responses'?base+'/responses':protocol==='anthropic'?base+'/messages':base+'/models/'+encodeURIComponent(model)+(streaming?':streamGenerateContent?alt=sse':':generateContent');
  const body=JSON.stringify(protocolRequest(protocol,settings,messages,tools,streaming));control.check();
  if(streaming){const parser=new ProtocolStream(protocol,t=>{control.check();onText(t);},t=>{control.check();onReasoning(t);});await transport.stream!(url,headers,body,chunk=>{control.check();parser.feed(chunk);});control.check();return parser.finish();}
  const raw=await transport.post(url,headers,body);control.check();if(raw.length>2_000_000)throw new Error('模型响应过大');const reply=decodeProtocol(protocol,parseJson(raw));if(typeof reply.message.content==='string')onText(reply.message.content);if(reply.reasoning)onReasoning(reply.reasoning);return reply;
}

function parseJson(raw:string):any{try{return JSON.parse(raw);}catch(_){throw new Error('模型返回无效 JSON；未执行工具');}}
