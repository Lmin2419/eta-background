// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/app/AgentRunStore.kt,
// AgentRunEventCoalescer.kt, AgentRunMessageProjector.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { AgentRunStore } from './agentRunStore';
import { AgentRunMessageProjector } from './agentRunMessageProjector';
import { AgentChatMessageUi } from './model/conversation';
import { AgentProjectionEvent } from './model/runtimeProjection';
/** The legacy HTTP port reports cumulative text. Convert it once, before the Eta reducer. */
export class FoundationRunProjection {
  private projector:AgentRunMessageProjector;
  private store:AgentRunStore;
  private value:AgentChatMessageUi[]=[];
  private content=new Map<'TEXT'|'THINKING',string>();
  private finished=false;
  constructor(readonly runId:string,clock:()=>number,private changed:()=>void){this.projector=new AgentRunMessageProjector(clock);this.store=new AgentRunStore((_id,event)=>{this.apply(event);this.changed();});this.store.register(runId,runId);}
  private apply(event:AgentProjectionEvent):void {this.value=this.projector.applyEvent(this.runId,event,this.value);}
  update(kind:'TEXT'|'THINKING',text:string):void {
    if(this.finished)return;
    const old=this.content.get(kind),index=kind==='TEXT'?1:0;
    if(old===text)return;
    if(old===undefined){this.flush();this.apply({type:'AssistantBlockStart',round:0,kind,index});}
    if(old!==undefined&&!text.startsWith(old)){
      this.flush();this.apply({type:'AssistantBlockEnd',round:0,kind,index,replacementContent:text});
      this.apply({type:'AssistantBlockStart',round:0,kind,index});
    }else{
      const delta=text.slice(old?.length||0);
      this.store.enqueue(this.runId,{type:'AssistantBlockDelta',round:0,kind,index,delta});
    }
    this.content.set(kind,text);
  }
  flush():void {this.store.flush(this.runId);}
  finish():void {if(this.finished)return;this.store.enqueue(this.runId,{type:'RunFinished'});this.store.beginSettlement(this.runId);this.store.remove(this.runId);this.finished=true;}
  messages():AgentChatMessageUi[]{
    // Keep the existing durable turn identity across streaming, settlement and process reload.
    return this.value.map(m=>m.kind==='assistant'?{...m,id:this.runId+'-assistant'}:m.kind==='thinking'?{...m,id:this.runId+'-thinking'}:m);
  }
}
