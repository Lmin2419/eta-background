import { TokenUsage, usageOf } from './usage';
import { ModelReply, ToolCall } from './types';

/** Incremental SSE framing. Nothing in a partial tool call is executable. */
export class CompletionStream {
  private buffer = '';
  private eventData: string[] = [];
  private size = 0;
  private mode = '';
  private content = '';
  private reasoning='';private usage:TokenUsage|undefined;
  private finishReason = '';
  private done = false;
  private calls: Map<number,ToolCall> = new Map();
  constructor(private textChanged: (text:string)=>void,private reasoningChanged:(text:string)=>void=()=>{}) {}
  feed(chunk:string):void {
    this.size += chunk.length;
    if(this.size > 2_000_000) throw new Error('模型响应超过大小限制');
    this.buffer += chunk;
    if(!this.mode) {
      const first=this.buffer.trimStart()[0];
      if(!first) return;
      this.mode=first==='{'?'json':'sse';
    }
    if(this.mode==='json') return;
    let end:number;
    while((end=this.buffer.indexOf('\n'))>=0) {
      const line=this.buffer.slice(0,end).replace(/\r$/,'');this.buffer=this.buffer.slice(end+1);
      if(!line) {this.dispatch();continue;}
      if(line.startsWith('data:')) this.eventData.push(line.slice(5).replace(/^ /,''));
    }
  }
  private dispatch():void {
    if(!this.eventData.length)return;
    const data=this.eventData.join('\n');this.eventData=[];
    if(this.done)throw new Error('模型流结束后收到额外数据');
    if(data==='[DONE]'){this.done=true;return;}
    let frame:any;
    try {frame=JSON.parse(data);}catch(_){throw new Error('模型流包含无效 JSON');}
    if(frame.error)throw new Error('模型服务在生成过程中返回错误，请检查额度与模型配置');
    if(!Array.isArray(frame.choices))throw new Error('无效的模型流事件');
    if(frame.usage)this.usage=usageOf(frame.usage);
    if(frame.choices.length===0)return; // usage-only event
    if(frame.choices.length!==1)throw new Error('不支持同时返回多个候选回复');
    const choice=frame.choices[0];
    if(choice.index!==undefined && choice.index!==0)throw new Error('模型候选序号无效');
    if(this.finishReason)throw new Error('模型完成后仍返回内容');
    const delta=choice.delta;
    if(!delta || typeof delta!=='object' || Array.isArray(delta))throw new Error('模型流缺少增量内容');
    if(delta.role!==undefined && delta.role!=='assistant')throw new Error('模型流角色无效');
    const reasoning=delta.reasoning_content??delta.reasoning;
    if(reasoning!=null){if(typeof reasoning!=='string')throw new Error('模型推理字段格式无效');this.reasoning+=reasoning;if(this.reasoning.length>32000)throw new Error('模型推理内容超过上限');if(reasoning)this.reasoningChanged(this.reasoning);}
    if(delta.content!=null) {
      if(typeof delta.content!=='string')throw new Error('模型文本格式无效');
      this.content+=delta.content;
      if(this.content.length>32000)throw new Error('本轮回复超过 32000 字符，请缩小请求范围');
      if(delta.content)this.textChanged(this.content);
    }
    if(delta.tool_calls!=null) {
      if(!Array.isArray(delta.tool_calls))throw new Error('无效工具流格式');
      for(const part of delta.tool_calls) {
        if(!Number.isInteger(part.index)||part.index<0||part.index>=8)throw new Error('无效工具流序号');
        const call=this.calls.get(part.index)||{id:'',type:'function',function:{name:'',arguments:''}};
        if(part.id!==undefined) {
          if(typeof part.id!=='string'||(call.id&&call.id!==part.id))throw new Error('工具 ID 在流中发生变化');
          call.id=part.id;
        }
        if(part.type!==undefined&&part.type!=='function')throw new Error('工具流类型无效');
        if(part.function!==undefined) {
          for(const key of ['name','arguments'] as const) {
            if(part.function[key]!==undefined) {
              if(typeof part.function[key]!=='string')throw new Error('工具流字段无效');
              call.function[key]+=part.function[key];
            }
          }
        }
        if(call.function.arguments.length>64000||call.function.name.length>256||call.id.length>256)throw new Error('工具流超过大小限制');
        this.calls.set(part.index,call);
      }
    }
    if(choice.finish_reason!=null) {
      if(typeof choice.finish_reason!=='string'||!choice.finish_reason)throw new Error('模型结束标记无效');
      this.finishReason=choice.finish_reason;
    }
  }
  finish(jsonReply:(raw:string)=>ModelReply):ModelReply {
    if(this.mode==='json') {
      const result=jsonReply(this.buffer);
      if(typeof result.message.content==='string')this.textChanged(result.message.content);
      if(result.reasoning)this.reasoningChanged(result.reasoning);
      return result;
    }
    // A closed connection is not a completed reply. Require a terminated SSE event and finish_reason.
    if(this.buffer.trim()||this.eventData.length||!this.finishReason)throw new Error('模型流中断，回复或工具参数未完整结束；未执行未完成的调用');
    const calls=Array.from(this.calls.entries()).sort((a,b)=>a[0]-b[0]).map(([index,call],i)=>{
      if(index!==i||!call.id||!call.function.name)throw new Error('工具流缺少 ID 或名称');
      return call;
    });
    if(new Set(calls.map(c=>c.id)).size!==calls.length)throw new Error('工具 ID 重复');
    if(!this.content&&!calls.length)throw new Error('模型返回空消息');
    return {message:{role:'assistant',content:this.content||null,...(calls.length?{tool_calls:calls}:{})},finishReason:this.finishReason,reasoning:this.reasoning,usage:this.usage};
  }
}
