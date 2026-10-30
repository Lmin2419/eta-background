import { AgentTask } from './session';
import { ConversationContext, ConversationMode, ConversationRepository, redactSecrets } from './conversations';
import { RunEvent } from './types';

export type RunJournal = (event:RunEvent)=>Promise<void>;
export interface TurnRequest { conversationId:string; text:string; modelLabel:string; profileId:string; mode:ConversationMode; secrets:string[]; runId?:string;revisionOf?:string;attachments?:string[] }

/** One-shot lifetime: durable user turn first, then execution, then durable outcome. */
export class ConversationRun {
  private started=false;
  private cancelled=false;
  private finishing=false;
  private task:AgentTask|undefined;
  private reason='任务已停止，请确认当前页面后再继续';
  private turnId='';
  private partial='';
  private reasoning='';
  private checkpointTimer:ReturnType<typeof setTimeout>|undefined;
  private writes:Promise<void>=Promise.resolve();
  private storageError:Error|undefined;
  constructor(private repository:ConversationRepository,private request:TurnRequest,
    private factory:(context:ConversationContext,journal:RunJournal)=>AgentTask) {}
  stop(reason='任务已停止，请确认当前页面后再继续'):void {
    if(this.finishing || this.cancelled) return;
    this.cancelled=true;this.reason=reason;this.task?.stop();
  }
  private clean(text:string):string {return redactSecrets(text,this.request.secrets);}
  capture(text:string,reasoning=false):void {
    if(this.finishing||!this.turnId)return;
    if(reasoning)this.reasoning=this.clean(text).slice(0,32000);else this.partial=this.clean(text).slice(0,32000);
    if(this.checkpointTimer===undefined)this.checkpointTimer=setTimeout(()=>{this.checkpointTimer=undefined;this.checkpoint();},750);
  }
  private checkpoint():void {
    const text=this.partial,reasoning=this.reasoning;
    this.writes=this.writes.then(()=>this.repository.progress(this.request.conversationId,this.turnId,text,reasoning)).catch(e=>{this.storageError=e as Error;this.task?.stop();});
  }
  private async flush():Promise<void>{
    if(this.checkpointTimer!==undefined){clearTimeout(this.checkpointTimer);this.checkpointTimer=undefined;this.checkpoint();}
    await this.writes;if(this.storageError)throw this.storageError;
  }
  async run():Promise<string> {
    if(this.started) throw new Error('同一轮不能重复执行');this.started=true;
    let turnId='',partial='';
    try {
      if(this.cancelled) throw new Error('CANCELLED');
      const begun=await this.repository.begin(this.request.conversationId,this.clean(this.request.text),this.clean(this.request.modelLabel),this.request.profileId,this.request.mode,this.request.runId);
      turnId=begun.turnId;this.turnId=turnId;
      if(this.request.revisionOf||this.request.attachments?.length)await this.repository.annotate(this.request.conversationId,turnId,{revisionOf:this.request.revisionOf,attachments:this.request.attachments});
      if(this.cancelled) throw new Error('CANCELLED');
      this.task=this.factory(begun.context,async event=>{
        if(event.kind==='usage'&&event.usage){await this.repository.annotate(this.request.conversationId,turnId,{usage:event.usage});return;}
        if(event.kind==='context'){await this.repository.annotate(this.request.conversationId,turnId,{contextNote:event.text});return;}
        if(event.kind==='reply') {
          partial=this.clean(event.text);this.partial=partial;
          await this.flush();await this.repository.progress(this.request.conversationId,turnId,partial,this.reasoning);return;
        }
        if(this.cancelled) throw new Error('CANCELLED');
        await this.flush();await this.repository.step(this.request.conversationId,turnId,{...event,detail:event.detail?this.clean(event.detail):undefined});
        if(this.cancelled) throw new Error('CANCELLED');
      });
      const result=await this.task.run(this.clean(this.request.text));
      if(this.cancelled) throw new Error('CANCELLED');
      this.finishing=true;
      await this.flush();
      await this.repository.finish(this.request.conversationId,turnId,'completed',this.clean(result));
      return this.clean(result);
    } catch(e) {
      this.task?.stop();
      try{await this.flush();}catch(_){}
      partial=this.partial||partial;
      const cancelled=this.cancelled || (e instanceof Error && e.message==='CANCELLED');
      const notice=cancelled?this.reason:'本轮未完成。请检查连接和配置后再发送；不会自动重复执行。';
      if(turnId) {
        try {await this.repository.finish(this.request.conversationId,turnId,cancelled?'stopped':'failed',partial,this.clean(notice)+(partial?' 已保留收到的部分内容，不代表任务已完成。':''));}
        catch (_) {throw new Error('本轮已停止，但最终记录未能保存。重启后会标为中断；请先检查设备状态。');}
      }
      if(cancelled) throw new Error(this.clean(this.reason));
      throw new Error(this.clean(e instanceof Error?e.message:'会话执行失败'));
    } finally {if(this.checkpointTimer!==undefined)clearTimeout(this.checkpointTimer);this.finishing=true;this.task=undefined;this.request.secrets=[];}
  }
}
