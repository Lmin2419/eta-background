// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/components/AgentChatBody.kt,
// ChatHistoryPaging.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { messagePage } from '../../../core/eta/conversationDrawer';
import { FoundationController, ChatView, emptyShellState } from '../../../core/foundation/controller';
import { AgentTimelineEntry, toTimelineEntries } from '../../../core/eta/agentChatTimelineEntries';
import { LengthMetrics } from '@kit.ArkUI';
import { ChatHistoryPagingController, VisibleHistoryItem, PagingConfiguration, HistoryViewport, ChatHistoryBoundaryKey, ChatBottomSentinelKey } from '../../../core/eta/chatHistoryPaging';
import { resolveBottomFollowDecision, resolveKeepBottomAnchored, smoothBottomFollowStep } from '../../../core/eta/agentChatScrollPolicy';
import { MarkdownRenderStore } from '../../../core/eta/markdown/markdownRendering';
import { EtaMessageDefaults as C, EtaComponentSizes as S } from '../../../core/eta/tokens';
import { parseEtaMarkdown } from '../../../platform/EtaMarkdownParser';
import { EtaFrameLoop } from '../../../platform/EtaFrameLoop';
import { ChatMessageItem } from './ChatMessageItem';
import { AgentWorkProcess } from './AgentWorkProcess';
@Observed
class TimelineItem {
  data:AgentTimelineEntry;
  constructor(data:AgentTimelineEntry){this.data=data;}
}
class ChatDataSource implements IDataSource {
  items:TimelineItem[]=[];
  private listeners:DataChangeListener[]=[];
  totalCount():number{return this.items.length;}
  getData(index:number):TimelineItem{return this.items[index];}
  update(entries:AgentTimelineEntry[]):void {
    const old=new Map<string,TimelineItem>();for(const item of this.items)old.set(item.data.key,item);
    const keysChanged=this.items.length!==entries.length||entries.some((entry,index)=>this.items[index]?.data.key!==entry.key);
    this.items=entries.map(entry=>{const item=old.get(entry.key);if(item){if(JSON.stringify(item.data)!==JSON.stringify(entry))item.data=entry;return item;}return new TimelineItem(entry);});
    if(keysChanged)this.listeners.forEach(listener=>listener.onDataReloaded());
  }
  registerDataChangeListener(listener:DataChangeListener):void{if(!this.listeners.includes(listener))this.listeners.push(listener);}
  unregisterDataChangeListener(listener:DataChangeListener):void{this.listeners=this.listeners.filter(l=>l!==listener);}
}
@Component
struct TimelineEntryView {
  controller:FoundationController|undefined=undefined;
  @ObjectLink item:TimelineItem;
  @Prop activeWork:string='';
  @Prop actionsEnabled:boolean=true;
  store:MarkdownRenderStore|undefined=undefined;
  build(){Column(){if(this.item.data.kind==='message'){ChatMessageItem({message:this.item.data.message,actionsEnabled:this.actionsEnabled,controller:this.controller,store:this.store})}else{AgentWorkProcess({identity:this.item.data.key,messages:this.item.data.messages,active:this.activeWork===this.item.data.key,store:this.store})}}.width('100%')}
}
@Component
export struct AgentChatBody {
  @Prop @Watch('refresh') chat:ChatView=emptyShellState().chat;
  controller:FoundationController|undefined=undefined;
  @State private anchored:boolean=true;
  @State private earlier:boolean=false;
  @State private activeWork:string='';
  private source:ChatDataSource=new ChatDataSource();
  private scroller:Scroller=new Scroller();
  private store:MarkdownRenderStore=new MarkdownRenderStore(parseEtaMarkdown);
  private identity:string='';
  private limit:number=C.PageSize;
  private start:number=0;
  private entries:AgentTimelineEntry[]=[];
  private paging:ChatHistoryPagingController|undefined=undefined;
  private frames:EtaFrameLoop|undefined=undefined;
  private viewportHeight:number=0;
  private firstIndex:number=0;
  private lastIndex:number=0;
  private dragging:boolean=false;
  private atBottom:boolean=false;
  private mounted:boolean=false;
  private lastFollow:boolean=true;
  private lastMessageCount:number=0;
  aboutToAppear():void {
    this.mounted=true;
    this.frames=new EtaFrameLoop(this.getUIContext(),(seconds:number)=>this.follow(seconds));
    this.refresh();
  }
  aboutToDisappear():void {this.mounted=false;this.frames?.stop();this.rememberViewport();this.store.clear();}
  private viewport():HistoryViewport {
    const visible:VisibleHistoryItem[]=[];
    for(let index=this.firstIndex;index<=this.lastIndex;index++){
      const data=this.entries[index-(this.earlier?1:0)];if(data){try{visible.push({key:data.key,offset:this.scroller.getItemRect(index).y});}catch(_){}}
    }
    return {measured:this.viewportHeight>0,firstVisibleItemIndex:this.firstIndex,visibleItems:visible,isScrollInProgress:this.dragging,canScrollForward:!this.atBottom};
  }
  private configuration():PagingConfiguration {return {entryKeys:this.entries.map(e=>e.key),messageStartIndex:this.start,messageCount:this.chat.messages.length-this.start,hasEarlier:this.earlier,loadState:'Idle',enabled:!this.chat.active,canReleasePages:!this.chat.active,keepBottomAnchored:this.anchored};}
  private refresh():void {
    if(!this.mounted)return;
    const switched=this.identity!==this.chat.id;
    if(switched){this.rememberViewport();this.store.clear();this.identity=this.chat.id;this.limit=C.PageSize;this.anchored=this.chat.followBottom;this.paging=undefined;this.entries=[];}
    if(switched||this.lastFollow!==this.chat.followBottom)this.anchored=this.chat.followBottom;
    this.lastFollow=this.chat.followBottom;
    if(!switched&&!this.anchored&&this.chat.messages.length>this.lastMessageCount)this.limit+=this.chat.messages.length-this.lastMessageCount;
    this.lastMessageCount=this.chat.messages.length;
    const page=messagePage(this.chat.messages,this.chat.messages.length,this.limit);this.start=page.startIndex;this.earlier=page.hasEarlierMessages;
    this.entries=toTimelineEntries(page.messages,this.entries);
    const last=this.entries[this.entries.length-1];this.activeWork=last?.kind==='work'&&this.chat.active?last.key:'';
    this.source.update(this.entries);
    const retained=new Set<string>(this.chat.messages.slice(this.start).map(m=>m.id));for(const entry of this.entries)retained.add(entry.key);this.store.retain(retained);
    if(!this.paging)this.paging=new ChatHistoryPagingController(this.configuration(),{
      loadEarlier:()=>{this.limit+=C.PageSize;this.refresh();},
      bottomAnchorChanged:(value:boolean)=>{this.anchored=value;},
      viewportChanged:(latest:boolean)=>{if(latest&&this.limit>C.PageSize*3){this.limit=C.PageSize;this.refresh();}},
      requestScrollToItem:(index:number,offset:number)=>{const id=this.identity;new EtaFrameLoop(this.getUIContext(),()=>{if(this.mounted&&this.identity===id)try{this.scroller.scrollToIndex(index,false,ScrollAlign.START,{extraOffset:LengthMetrics.vp(offset)});}catch(_){}return false;}).start();}
    });
    else this.paging.configure(this.configuration(),this.viewport());
    if(switched&&!this.anchored){const id=this.identity,scroll=this.chat.scroll;new EtaFrameLoop(this.getUIContext(),()=>{if(this.mounted&&this.identity===id)try{this.scroller.scrollTo({xOffset:S.Dp0,yOffset:scroll});}catch(_){}return false;}).start();}
    if(this.anchored)this.frames?.start();
  }
  private follow(seconds:number):boolean {
    if(!this.mounted||!this.anchored||this.dragging||this.viewportHeight<=0)return false;
    const bottom=this.entries.length+(this.earlier?1:0);let sentinel:number|null=null;
    if(this.lastIndex>=bottom){try{const rect=this.scroller.getItemRect(bottom);sentinel=rect.y+rect.height+S.Dp16;}catch(_){}}
    const decision=resolveBottomFollowDecision(true,bottom,sentinel,this.viewportHeight,this.lastIndex);
    if(decision.requestIndex!==null)this.scroller.scrollToIndex(decision.requestIndex,false,ScrollAlign.END);
    else if(decision.scrollByPx>0)this.scroller.scrollBy(S.Dp0,smoothBottomFollowStep(decision.scrollByPx,seconds,1));
    else return false;
    return true;
  }
  private rememberViewport():void {try{this.controller?.viewport(this.identity,this.scroller.currentOffset().yOffset,this.anchored);}catch(_){}}
  build(){Stack({alignContent:Alignment.Bottom}){
    List({scroller:this.scroller}){
      if(this.earlier){ListItem(){Button($r('app.string.conversation_retry_earlier_messages')).height(S.Dp48).width('100%').backgroundColor($r('app.color.eta_transparent')).fontColor($r('app.color.miuix_on_surface_variant_summary')).enabled(!this.chat.active).onClick(()=>this.paging?.requestEarlier(this.viewport()))}}
      LazyForEach(this.source,(item:TimelineItem)=>{ListItem(){TimelineEntryView({item,activeWork:this.activeWork,actionsEnabled:!this.chat.active&&!this.chat.messageEdit,controller:this.controller,store:this.store}).onAreaChange(()=>{if(this.anchored)this.frames?.start();})}},(item:TimelineItem)=>item.data.key)
      ListItem(){Row(){}.width('100%').height(S.Dp1)}
    }.width('100%').height('100%').padding({top:S.Dp16,bottom:S.Dp16}).scrollBar(BarState.Auto)
      .onAreaChange((_old,area)=>{this.viewportHeight=Number(area.height);if(this.anchored)this.frames?.start();})
      .onScrollIndex((start:number,end:number)=>{this.firstIndex=start;this.lastIndex=end;this.paging?.maybeLoad(this.viewport());})
      .onWillScroll((offset:number,state:ScrollState,source:ScrollSource)=>{
        const user=source===ScrollSource.DRAG||source===ScrollSource.FLING;
        if(user&&offset<S.Dp0){this.anchored=false;this.atBottom=false;this.frames?.stop();}
        this.paging?.onPreScroll(-offset,user,this.viewport());return {offsetRemain:offset};
      })
      .onTouch((event:TouchEvent)=>{this.dragging=event.type===TouchType.Down||event.type===TouchType.Move;if(!this.dragging&&this.anchored)this.frames?.start();})
      .onReachEnd(()=>{this.atBottom=true;this.anchored=resolveKeepBottomAnchored(this.anchored,this.dragging,true);})
      .onDidScroll((offset:number)=>{if(offset<S.Dp0)this.atBottom=false;this.paging?.onPostScroll(-offset,this.viewport());})
      .onScrollStop(()=>{this.dragging=false;this.paging?.notifyViewport(this.viewport());this.rememberViewport();})
    if(!this.anchored&&!this.atBottom){Button({type:ButtonType.Circle}){Image($r('app.media.ic_rounded_arrow_downward')).width(S.Dp18).height(S.Dp18).fillColor($r('app.color.miuix_on_surface'))}.width(S.Dp40).height(S.Dp40).padding(S.Dp0).backgroundColor($r('app.color.miuix_surface_container_high')).border({width:S.Dp0_5,color:$r('app.color.eta_input_outline')}).shadow({radius:S.Dp8,color:$r('app.color.eta_input_shadow')}).margin({bottom:S.Dp12}).accessibilityText($r('app.string.ui_back_to_bottom_32282e')).onClick(()=>{this.anchored=true;this.scroller.scrollEdge(Edge.Bottom);this.frames?.start();})}
  }.width('100%').height('100%')}
}
