// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/app/AgentRunStore.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { AgentRunEventCoalescer } from './agentRunEventCoalescer';
import { AgentProjectionEvent, BlockDelta } from './model/runtimeProjection';
import { EtaQueueDefaults } from './tokens';
export interface RunTimerPort { schedule(delay:number,action:()=>void):()=>void; }
export type GuiControlStatus='Waiting'|'Acquired'|'Released';
interface Entry {conversationId:string;observed:boolean;cancelTimer:(()=>void)|null;stopRequested:boolean;stoppedByUser:boolean;settling:boolean;guiStatus:GuiControlStatus|null;}
export class AgentRunStore {
  private entries=new Map<string,Entry>();
  private coalescer=new AgentRunEventCoalescer();
  constructor(private onEvent:(runId:string,event:AgentProjectionEvent)=>void,private timer:RunTimerPort={schedule:(delay,action)=>{const id=setTimeout(action,delay);return ()=>clearTimeout(id);}}){}
  get observedRunIds():Set<string>{return new Set([...this.entries].filter(([,e])=>e.observed).map(([id])=>id));}
  get hasActiveRuns():boolean{return [...this.entries.values()].some(e=>e.observed);}
  activeRunId(conversationId:string|null):string|null{return [...this.entries].find(([,e])=>e.observed&&e.conversationId===conversationId)?.[0]||null;}
  conversationId(runId:string):string|null{return this.entries.get(runId)?.conversationId||null;}
  register(runId:string,conversationId:string,observed=true):boolean{
    if(this.entries.has(runId)||(observed&&this.activeRunId(conversationId)))return false;
    this.entries.set(runId,{conversationId,observed,cancelTimer:null,stopRequested:false,stoppedByUser:false,settling:false,guiStatus:null});return true;
  }
  requestStop(runId:string,byUser=false):boolean{const e=this.entries.get(runId);if(!e)return false;e.stoppedByUser=e.stoppedByUser||byUser;if(e.stopRequested)return false;e.stopRequested=true;return true;}
  isStopRequested(runId:string):boolean{return this.entries.get(runId)?.stopRequested||false;}
  wasStoppedByUser(runId:string):boolean{return this.entries.get(runId)?.stoppedByUser||false;}
  beginSettlement(runId:string):boolean{const e=this.entries.get(runId);if(!e||e.settling)return false;e.settling=true;e.guiStatus=null;return true;}
  guiStatus(conversationId:string):GuiControlStatus|null{return this.entries.get(this.activeRunId(conversationId)||'')?.guiStatus||null;}
  updateGuiStatus(runId:string,status:GuiControlStatus):void{const e=this.entries.get(runId);if(e)e.guiStatus=status;}
  enqueue(runId:string,event:AgentProjectionEvent):void{
    const e=this.entries.get(runId);if(!e||e.settling)return;
    if(event.type==='AssistantBlockDelta'){
      if(event.kind==='TOOL_CALL'||!event.delta)return;
      const previous=this.coalescer.append(runId,{...event,deltaChars:event.delta.length});if(previous)this.onEvent(runId,{type:'AssistantBlockDelta',...previous});
      if(!e.cancelTimer)e.cancelTimer=this.timer.schedule(EtaQueueDefaults.StreamUiUpdateMs,()=>{e.cancelTimer=null;this.flush(runId);});
    }else{this.flush(runId);this.onEvent(runId,event);}
  }
  flush(runId:string):void{const e=this.entries.get(runId);if(!e)return;e.cancelTimer?.();e.cancelTimer=null;const delta=this.coalescer.flush(runId);if(delta)this.onEvent(runId,{type:'AssistantBlockDelta',...delta});}
  remove(runId:string):void{this.entries.get(runId)?.cancelTimer?.();this.entries.delete(runId);this.coalescer.flush(runId);}
}
