// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/markdown/StreamingMarkdown.kt,
// StaticMarkdown.kt, StreamingMarkdownStateCache.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { MarkdownBlock } from '../../../core/eta/markdown/markdownModel';
import { MarkdownTone } from '../../../core/eta/markdown/markdownStylePolicy';
import { MarkdownRenderSession } from '../../../core/eta/markdown/markdownRendering';
import { EtaFrameLoop } from '../../../platform/EtaFrameLoop';
import { MarkdownBlocks } from './MarkdownContent';
@Component
export struct StreamingMarkdown {
  @Prop @Watch('updateTarget') content:string='';
  @Prop @Watch('updateTarget') streaming:boolean=false;
  @Prop tone:MarkdownTone='Answer';
  session:MarkdownRenderSession|undefined=undefined;
  @State private blocks:MarkdownBlock[]=[];
  @State private startedKeys:number[]=[];
  onRevealComplete:(complete:boolean)=>void=()=>{};
  private unsubscribe:(()=>void)|undefined=undefined;
  private frames:EtaFrameLoop|undefined=undefined;
  private mounted:boolean=false;
  aboutToAppear():void {
    this.mounted=true;this.session?.attach();
    this.frames=new EtaFrameLoop(this.getUIContext(),(seconds:number)=>{const coordinator=this.session?.state.revealCoordinator;coordinator?.advanceFrame(seconds);const keys=Array.from(coordinator?.started||new Set<number>());if(keys.join()!==this.startedKeys.join())this.startedKeys=keys;this.onRevealComplete(!!coordinator&&coordinator.drained&&!this.streaming&&!!this.session?.state.snapshot?.isComplete);return !!coordinator&&!coordinator.drained;});
    this.unsubscribe=this.session?.subscribe(()=>this.publish());this.updateTarget();this.publish();
  }
  aboutToDisappear():void {this.mounted=false;this.unsubscribe?.();this.frames?.stop();this.session?.detach();}
  private updateTarget():void {if(this.mounted)this.session?.update(this.content,this.streaming);}
  private publish():void {
    if(!this.mounted||!this.session)return;
    this.onRevealComplete(false);this.blocks=this.session.document.blocks;this.session.state.revealCoordinator.retainBlocks(this.session.document.revealKeys);this.frames?.start();
  }
  build(){MarkdownBlocks({blocks:this.blocks,tone:this.tone,startedKeys:this.startedKeys,reveal:this.session?.state.revealCoordinator})}
}
