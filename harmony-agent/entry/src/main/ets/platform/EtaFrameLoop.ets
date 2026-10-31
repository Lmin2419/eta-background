// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/markdown/StreamingMarkdown.kt,
// ui/components/AgentChatBody.kt withFrameNanos loops @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { UIContext, FrameCallback } from '@kit.ArkUI';
export class EtaFrameLoop extends FrameCallback {
  private active: boolean = false;
  private pending: boolean = false;
  private last: number = 0;
  private context:UIContext;
  private tick:(seconds:number)=>boolean;
  constructor(context:UIContext,tick:(seconds:number)=>boolean){super();this.context=context;this.tick=tick;}
  start():void {this.active=true;if(!this.pending){this.pending=true;this.context.postFrameCallback(this);}}
  stop():void {this.active=false;this.last=0;}
  onFrame(nanos:number):void {
    this.pending=false;if(!this.active)return;
    const elapsed=this.last===0?0:(nanos-this.last)/1e9;this.last=nanos;
    if(this.tick(elapsed))this.start();else this.stop();
  }
}
