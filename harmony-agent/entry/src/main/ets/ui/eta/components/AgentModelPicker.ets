// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/components/AgentModelPicker.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { FoundationController, ShellState, emptyShellState } from '../../../core/foundation/controller';
import { AgentModelOptionUi, AgentModelProviderGroupUi } from '../../../core/eta/modelPicker';
import { EtaComponentSizes as S, MiuixFontSizes as F, EtaChatDefaults as C, EtaModelPickerDefaults as M } from '../../../core/eta/tokens';
import { modelBrandResource } from './ModelBrandResources';
import { EtaListDataSource } from './EtaListDataSource';
import { EtaChoiceChip } from './EtaSmallComponents';

@Component
export struct ModelBrandMark {
  @Prop modelId: string = '';
  @Prop sourceType: string = 'custom';
  build() {
    if (modelBrandResource(this.modelId,this.sourceType) !== null) {
      Image(modelBrandResource(this.modelId,this.sourceType)!).width(C.ActionIconSize).height(C.ActionIconSize)
        .objectFit(ImageFit.Contain).borderRadius(C.ActionIconSize).accessibilityLevel('no')
    } else {
      Stack() {
        Image($r('app.media.ic_rounded_dns')).width(C.ActionIconSize*M.FallbackIconFraction).height(C.ActionIconSize*M.FallbackIconFraction).fillColor($r('app.color.miuix_primary'))
      }.width(C.ActionIconSize).height(C.ActionIconSize).borderRadius(C.ActionIconSize).backgroundColor($r('app.color.miuix_primary_container')).accessibilityLevel('no')
    }
  }
}
class PickerEntry {
  key: string;
  title: string;
  model: AgentModelOptionUi | null;
  constructor(key:string,title:string,model:AgentModelOptionUi|null=null) {this.key=key;this.title=title;this.model=model;}
}
@Component
export struct AgentModelPickerContent {
  @Prop @Watch('queryChanged') state: ShellState = emptyShellState();
  @Prop isSheet: boolean = false;
  controller: FoundationController | undefined = undefined;
  private scroller: Scroller = new Scroller();
  private queryKey: string = '';
  private dataSource: EtaListDataSource<PickerEntry> = new EtaListDataSource<PickerEntry>();
  private entriesKey: string = '';
  private selectedIndex: number = 0;
  aboutToAppear(): void { this.refreshEntries(); this.queryKey=this.state.modelQuery+'\n'+this.state.pickerFilter; }
  private refreshEntries(): void {
    const entries: PickerEntry[]=[];
    for(const group of this.state.pickerGroups) {
      if(this.isSheet?this.state.pickerFilter===null:this.state.picker.providerGroups.length>1)entries.push(new PickerEntry('header:'+group.providerId,group.providerName));
      for(const model of group.models)entries.push(new PickerEntry('model:'+model.id,'',model));
    }
    const key=JSON.stringify(entries);
    if(key!==this.entriesKey) {this.entriesKey=key;this.dataSource.replace(entries);}
    this.selectedIndex=Math.max(0,entries.findIndex(entry=>entry.model?.id===this.state.picker.selectedModel?.id)-2);
  }
  private queryChanged(): void {
    this.refreshEntries();
    const key=this.state.modelQuery+'\n'+this.state.pickerFilter;
    if(key!==this.queryKey) {this.queryKey=key;try {this.scroller.scrollEdge(Edge.Top);} catch (_) {}}
  }
  private selected(model: AgentModelOptionUi): boolean { return model.id===(this.state.pickerPending||this.state.picker.selectedModel?.id); }
  @Builder private modelRow(model: AgentModelOptionUi) {
    Column() { Row({space:S.Dp8}) {
      Text(model.displayName).fontSize(F.Body2).maxLines(1).textOverflow({overflow:TextOverflow.Ellipsis}).layoutWeight(1)
        .fontColor(this.selected(model)?$r('app.color.miuix_primary'):$r('app.color.miuix_on_surface'))
      if(this.selected(model)) { Image($r('app.media.ic_rounded_check')).width(S.Dp18).height(S.Dp18).fillColor($r('app.color.miuix_primary')).accessibilityText($r('app.string.ui_current_model_a0af8f')) }
    }.width('100%').padding({left:S.Dp12,right:S.Dp12,top:S.Dp11,bottom:S.Dp11}).borderRadius(S.Dp12)
      .enabled(!this.state.pickerPending && !this.state.chat.active && !this.state.busy)
      .onClick(()=>this.controller?.confirmPickerModel(model)).accessibilitySelected(this.selected(model))
    }.width('100%').padding({left:this.isSheet?S.Dp12:S.Dp8,right:this.isSheet?S.Dp12:S.Dp8})
  }
  build() {
    Column() {
      if(this.isSheet) {
        TextInput({text:this.state.modelQuery,placeholder:$r('app.string.ui_search_model_df5586')})
          .fontSize(F.Body1).margin({left:S.Dp20,right:S.Dp20,top:S.Dp4,bottom:S.Dp8})
          .onChange((query:string)=>this.controller?.searchModels(query))
        if(this.state.picker.providerGroups.length>1) {
          Scroll() {
            Row({space:S.Dp8}) {
              EtaChoiceChip({text:$r('app.string.model_picker_filter_all'),selected:this.state.pickerFilter===null,onAction:()=>this.controller?.filterPickerProvider(null)})
              ForEach(this.state.picker.providerGroups,(group:AgentModelProviderGroupUi)=>{
                EtaChoiceChip({text:group.providerName,selected:this.state.pickerFilter===group.providerId,onAction:()=>this.controller?.filterPickerProvider(group.providerId)})
              },(group:AgentModelProviderGroupUi)=>group.providerId)
            }.padding({left:S.Dp20,right:S.Dp20})
          }.scrollable(ScrollDirection.Horizontal).scrollBar(BarState.Off).margin({bottom:S.Dp6})
        }
      }
      List({scroller:this.scroller}) {
        if(this.state.pickerGroups.length===0) { ListItem(){Text($r('app.string.page_no_matching_model_found_ae7e96')).fontSize(F.Body2).fontColor($r('app.color.miuix_on_surface_variant_summary')).textAlign(TextAlign.Center).width('100%').padding({left:S.Dp20,right:S.Dp20,top:S.Dp48,bottom:S.Dp48})} }
        LazyForEach(this.dataSource,(entry:PickerEntry)=>{
          ListItem() {
            if(entry.model!==null) {this.modelRow(entry.model)}
            else {Text(entry.title).fontSize(F.Footnote1).fontColor($r('app.color.miuix_on_surface_variant_summary')).maxLines(1)
              .textOverflow({overflow:TextOverflow.Ellipsis}).padding({left:this.isSheet?S.Dp24:S.Dp20,right:this.isSheet?S.Dp24:S.Dp20,top:S.Dp14,bottom:S.Dp4})}
          }
        },(entry:PickerEntry)=>entry.key)
      }.width('100%').layoutWeight(1).padding({top:S.Dp2,bottom:this.isSheet?S.Dp12:S.Dp6}).scrollBar(BarState.Auto).onAppear(()=>{try{this.scroller.scrollToIndex(this.selectedIndex,false);}catch(_){}})
    }.width('100%').height('100%').backgroundColor($r('app.color.miuix_surface'))
  }
}
