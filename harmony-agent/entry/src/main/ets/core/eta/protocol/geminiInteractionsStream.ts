// Ported from app/src/main/kotlin/io/github/mangi/eta/agent/model/GeminiInteractionsStream.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import {JsonObject,ProviderEventSink,ProviderUsage,ProviderRunControl,AssistantBlockKind,array,object,copy} from './providerTypes';
import {ProviderSseReader,ProviderTextStream,readProviderSse} from './providerSseReader';import {AgentModelFailure} from './agentModelFailure';
import {SearchSuggestionsBudget} from '../searchSuggestionsBudget';import {geminiCitations} from './geminiCitationFormatter';import {attachGeminiSteps,attachGeminiSuggestions} from './geminiEphemeralState';
const MAX_EVENT=2*1024*1024,MAX_STREAM=16*1024*1024,MAX_ARGUMENTS=1024*1024,MAX_STEPS=2048;
interface Step {index:number;step:JsonObject;arguments:string;hasArgumentDelta:boolean;stopped:boolean;}
function malformed(code='GEMINI_STREAM_INVALID'):never {throw new AgentModelFailure(code,false,code==='GEMINI_UNSUPPORTED_OUTPUT'?'Gemini 返回了当前不支持的输出类型。':code==='GEMINI_SEARCH_SUGGESTIONS_LIMIT'?'Gemini 搜索建议超过展示容量限制。':'Gemini 流式响应格式无效或超过容量限制。');}
const has=(o:JsonObject,k:string)=>Object.prototype.hasOwnProperty.call(o,k);
function str(o:JsonObject,key:string,allowEmpty=false):string{const v=o[key];if(typeof v!=='string'||!allowEmpty&&!v.trim())malformed();return v;}
function obj(v:unknown):JsonObject{return object(v)||malformed();}
function list(v:unknown):unknown[]{if(!Array.isArray(v))malformed();return v;}
function parse(raw:string):JsonObject{try{return obj(JSON.parse(raw));}catch(_){return malformed();}}
function kind(s:Step):AssistantBlockKind|null{switch(s.step.type){case 'model_output':return 'TEXT';case 'thought':return 'THINKING';case 'function_call':return 'TOOL_CALL';default:return null;}}
const plain=(items:unknown[]):string=>items.map(v=>str(obj(v),'text',true)).join('');
const formatted=(items:unknown[]):string=>items.map(v=>{const item=obj(v);return geminiCitations(str(item,'text',true),array(item.annotations));}).join('');
function validateText(items:unknown[]):void{for(const raw of items){const item=obj(raw);if(str(item,'type')!=='text')malformed('GEMINI_UNSUPPORTED_OUTPUT');str(item,'text',true);}}
function validateSignature(step:JsonObject):void{if(has(step,'signature'))str(step,'signature',true);}
function validateSearch(step:JsonObject):void{
 if(has(step,'is_error')&&typeof step.is_error!=='boolean')malformed();if(!has(step,'result'))return;
 for(const raw of list(step.result)){const item=obj(raw);if(has(item,'search_suggestions')){const html=str(item,'search_suggestions',true);if(html.trim()&&!new SearchSuggestionsBudget().accept(0,0,html))malformed('GEMINI_SEARCH_SUGGESTIONS_LIMIT');}}
}
function lastText(step:JsonObject,key:string):JsonObject{const items=list(step[key]),last=object(items[items.length-1]);if(last)return last;const item={type:'text',text:''};items.push(item);return item;}
export class GeminiInteractionsStream {
 private reader:ProviderSseReader;private steps=new Map<number,Step>();private searchCalls=new Set<string>();private searchResults=new Set<string>();private received=0;private assistant:JsonObject|null=null;
 private usage:ProviderUsage={contextTokens:null,inputTokens:null,outputTokens:null,reasoningTokens:null,cachedTokens:null};
 constructor(private control:ProviderRunControl,private emit:ProviderEventSink,private pendingSuggestions:unknown[]=[]){this.reader=new ProviderSseReader(control,(event,data)=>this.accept(event,data));}
 feed(chunk:string):boolean{return this.reader.feed(chunk);}
 async read(stream:ProviderTextStream):Promise<JsonObject>{await readProviderSse(stream,this.control,(event,data)=>this.accept(event,data));return this.finish();}
 finish():JsonObject{this.reader.finish();this.control.check();if(!this.assistant)throw AgentModelFailure.incompleteStream('Gemini 响应提前结束，未收到完整终态。');return this.assistant;}
 private ordered():Step[]{return [...this.steps.values()].sort((a,b)=>a.index-b.index);}
 private index(event:JsonObject):number{const n=event.index;if(typeof n!=='number'||!Number.isInteger(n)||n<0||n>2147483647)malformed();return n;}
 private requireType(s:Step,type:string):void{if(s.step.type!==type)malformed();}
 private mergeUsage(raw:JsonObject):void{
  const token=(key:string,previous:number|null):number|null=>{if(!has(raw,key))return previous;const n=raw[key];if(typeof n!=='number'||!Number.isInteger(n)||n<0||n>2147483647)malformed();return n;};
  this.usage={contextTokens:token('total_tokens',this.usage.contextTokens),inputTokens:token('total_input_tokens',this.usage.inputTokens),outputTokens:token('total_output_tokens',this.usage.outputTokens),reasoningTokens:token('total_thought_tokens',this.usage.reasoningTokens),cachedTokens:token('total_cached_tokens',this.usage.cachedTokens)};
 }
 private accept(eventName:string,data:string):boolean{
  this.control.check();if(data.trim()==='[DONE]')throw AgentModelFailure.incompleteStream('Gemini 响应缺少完成事件。');this.received+=data.length;if(data.length>MAX_EVENT||this.received>MAX_STREAM)malformed('GEMINI_STREAM_LIMIT');
  const event=parse(data),type=str(event,'event_type');if(eventName.trim()&&eventName!==type)malformed();const metadata=object(object(event.metadata)?.total_usage);if(metadata)this.mergeUsage(metadata);
  switch(type){
   case 'step.start':this.start(this.index(event),obj(event.step));break;
   case 'step.delta':this.delta(this.index(event),obj(event.delta));break;
   case 'step.stop':this.stop(this.index(event));if(object(event.usage))this.mergeUsage(obj(event.usage));break;
   case 'interaction.created':{const i=obj(event.interaction);if(object(i.usage))this.mergeUsage(obj(i.usage));break;}
   case 'interaction.status_update':str(event,'status');if(object(event.usage))this.mergeUsage(obj(event.usage));break;
   case 'interaction.completed':this.terminal(obj(event.interaction));return false;
   case 'error':throw AgentModelFailure.stream(object(event.error)||{});
  }return true;
 }
 private start(index:number,raw:JsonObject):void{
  if(this.steps.has(index)||this.steps.size>=MAX_STEPS)malformed();const step=copy(raw),type=str(step,'type');
  if(!['model_output','thought','function_call','google_search_call','google_search_result'].includes(type))malformed('GEMINI_UNSUPPORTED_OUTPUT');validateSignature(step);
  const state:Step={index,step,arguments:'',hasArgumentDelta:false,stopped:false};this.steps.set(index,state);
  if(type==='model_output'||type==='thought'){
   const field=type==='thought'?'summary':'content',items=Array.isArray(step[field])?step[field] as unknown[]:[];step[field]=items;validateText(items);const k=kind(state)!;this.emit({type:'BlockStart',kind:k,index,blockId:null,name:null});const initial=plain(items);if(initial)this.emit({type:'BlockDelta',kind:k,index,delta:initial});
  }else if(type==='function_call'){
   const id=str(step,'id'),name=str(step,'name');if([...this.steps.values()].some(s=>s!==state&&s.step.type===type&&s.step.id===id))malformed();if(has(step,'arguments')&&!object(step.arguments))malformed();this.emit({type:'BlockStart',kind:'TOOL_CALL',index,blockId:id,name});
  }else if(type==='google_search_call'){
   const id=str(step,'id');if(has(step,'arguments')&&!object(step.arguments))malformed();if(this.searchCalls.has(id))malformed();this.searchCalls.add(id);this.emit({type:'HostedToolStarted',id,name:'web_search'});
  }else{const id=str(step,'call_id');if(!this.searchCalls.has(id)||this.searchResults.has(id))malformed();this.searchResults.add(id);validateSearch(step);}
 }
 private delta(index:number,delta:JsonObject):void{
  const state=this.steps.get(index);if(!state||state.stopped)malformed();const step=state.step;
  switch(str(delta,'type')){
   case 'text':{this.requireType(state,'model_output');const value=str(delta,'text',true),part=lastText(step,'content');part.text=str(part,'text',true)+value;this.emit({type:'BlockDelta',kind:'TEXT',index,delta:value});break;}
   case 'text_annotation_delta':{this.requireType(state,'model_output');const incoming=list(delta.annotations),part=lastText(step,'content'),annotations=Array.isArray(part.annotations)?part.annotations:[];part.annotations=annotations;for(const item of incoming)annotations.push(obj(item));break;}
   case 'thought_summary':{this.requireType(state,'thought');const item=obj(delta.content);validateText([item]);list(step.summary).push(copy(item));this.emit({type:'BlockDelta',kind:'THINKING',index,delta:str(item,'text',true)});break;}
   case 'thought_signature':this.requireType(state,'thought');step.signature=str(delta,'signature',true);break;
   case 'arguments_delta':{this.requireType(state,'function_call');const value=str(delta,'arguments',true);state.hasArgumentDelta=true;state.arguments+=value;if(state.arguments.length>MAX_ARGUMENTS)malformed('GEMINI_ARGUMENT_LIMIT');this.emit({type:'BlockDelta',kind:'TOOL_CALL',index,delta:value});break;}
   case 'google_search_call':case 'google_search_result':{
    this.requireType(state,str(delta,'type'));validateSignature(delta);const fields=step.type==='google_search_call'?['arguments','signature']:['result','is_error','signature'];
    for(const key of Object.keys(delta)){if(!fields.includes(key))continue;if(key==='arguments'&&!object(delta[key]))malformed();if(key==='is_error'&&typeof delta[key]!=='boolean')malformed();
     if(key==='result'){const incoming=list(delta[key]),target=Array.isArray(step[key])?step[key] as unknown[]:[];step[key]=target;target.push(...incoming);}else step[key]=delta[key];}
    if(step.type==='google_search_result')validateSearch(step);break;
   }
   default:malformed('GEMINI_UNSUPPORTED_OUTPUT');
  }
 }
 private stop(index:number):void{
  const state=this.steps.get(index);if(!state||state.stopped)malformed();state.stopped=true;const step=state.step;
  if(step.type==='function_call')step.arguments=state.hasArgumentDelta?parse(state.arguments):obj(step.arguments);
  if(step.type==='google_search_result'){if(!has(step,'result'))step.result=[];this.emit({type:'HostedToolFinished',id:str(step,'call_id'),name:'web_search',success:step.is_error!==true});}
  const k=kind(state);if(k)this.emit({type:'BlockEnd',kind:k,index,blockId:typeof step.id==='string'&&step.id.trim()?step.id:null,name:typeof step.name==='string'&&step.name.trim()?step.name:null,content:k==='TEXT'?formatted(list(step.content)):k==='THINKING'?plain(list(step.summary)):JSON.stringify(obj(step.arguments)),replaceContent:k==='TEXT'});
 }
 private suggestions(assistant:JsonObject):void{
  const all=this.ordered(),texts=all.filter(s=>s.step.type==='model_output'&&plain(list(s.step.content)).trim()),budget=new SearchSuggestionsBudget(),seen=new Set<string>(),deferred:string[]=[];
  const suggest=(after:number,html:string):void=>{if(!html.trim()||seen.has(html))return;seen.add(html);const target=texts.find(s=>s.index>after)||texts[texts.length-1];if(!budget.accept(0,target?.index??0,html))malformed('GEMINI_SEARCH_SUGGESTIONS_LIMIT');if(target)this.emit({type:'SearchSuggestions',index:target.index,html});else deferred.push(html);};
  for(const html of this.pendingSuggestions){if(typeof html!=='string')malformed();suggest(-1,html);}
  for(const state of all.filter(s=>s.step.type==='google_search_result'))for(const raw of array(state.step.result)){const item=object(raw);suggest(state.index,typeof item?.search_suggestions==='string'?item.search_suggestions:'');}attachGeminiSuggestions(assistant,deferred);
 }
 private terminal(interaction:JsonObject):void{
  const status=str(interaction,'status');if(object(interaction.usage))this.mergeUsage(obj(interaction.usage));
  if(status==='failed'||status==='cancelled')throw AgentModelFailure.stream(object(array(interaction.errors)[0])||{});
  if(!['completed','requires_action','incomplete','budget_exceeded'].includes(status))malformed();
  if(Object.values(this.usage).some(v=>v!==null))this.emit({type:'Usage',usage:{...this.usage},contextInputTokens:this.usage.inputTokens});
  if(status==='incomplete'||status==='budget_exceeded'){this.suggestions({});throw new AgentModelFailure('MODEL_OUTPUT_LIMIT',false,'Gemini 输出未完整生成，可能已达到输出或执行预算；请调整输出上限或思考档位后重试。');}
  const steps=this.ordered();if(steps.some(s=>!s.stopped))throw AgentModelFailure.incompleteStream('Gemini 输出步骤未完整结束。');const calls=steps.filter(s=>s.step.type==='function_call');
  if(status==='requires_action'&&!calls.length)malformed();if(calls.length&&status!=='requires_action')throw AgentModelFailure.incompleteStream('Gemini 工具调用缺少可执行的完整终态。');
  if(this.searchCalls.size!==this.searchResults.size||[...this.searchCalls].some(id=>!this.searchResults.has(id)))throw AgentModelFailure.incompleteStream('Gemini 搜索步骤未完整结束。');
  const texts=steps.filter(s=>s.step.type==='model_output').map(s=>formatted(list(s.step.content))),thoughts=steps.filter(s=>s.step.type==='thought').map(s=>plain(list(s.step.summary)));
  const result:JsonObject={role:'assistant',content:texts.join('\n'),finish_reason:status==='requires_action'?'tool_calls':'stop'};if(thoughts.some(t=>t.length))result.reasoning_content=thoughts.join('\n');
  if(calls.length)result.tool_calls=calls.map(s=>({id:str(s.step,'id'),type:'function',function:{name:str(s.step,'name'),arguments:JSON.stringify(obj(s.step.arguments))}}));attachGeminiSteps(result,steps.map(s=>s.step));this.suggestions(result);this.assistant=result;
 }
}
