// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/pages/providers/CommunityCatalogScreens.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { FoundationController, ShellState, emptyShellState } from '../../../../core/foundation/controller';
import { CommunityCatalogProvider, CommunityCatalogModel } from '../../../../core/eta/communityCatalog';
import { EtaComponentSizes as S, MiuixFontSizes as F } from '../../../../core/eta/tokens';
import { ProviderSection, ProviderPage } from './ProviderComponents';
import { EtaListDataSource } from '../../components/EtaListDataSource';
import { EtaPreferenceRow } from '../../components/EtaPreferenceRow';
import { EtaPreferenceDivider, EtaPreferenceGroupTitle } from '../../components/EtaPreferenceStyle';
import { EtaCheckboxPreference } from '../../components/EtaSelectionPreferences';
import { EtaTextButton } from '../../components/EtaControls';
import { EtaCard } from '../../components/EtaCard';
import { renderKey } from '../../../../core/eta/renderKey';
@Component
export struct CommunityCatalogScreen {
  @Prop @Watch('refreshRows') state:ShellState=emptyShellState();controller:FoundationController|undefined=undefined;
  private source:EtaListDataSource<CommunityCatalogProvider>=new EtaListDataSource<CommunityCatalogProvider>();private entriesKey:string='';
  aboutToAppear():void{this.refreshRows();}
  private refreshRows():void{const rows=this.controller?.community?.providers()||[];const key=rows.map(p=>p.id+':'+p.models.length).join(',')+this.state.catalog.catalog?.fetchedAt;if(key!==this.entriesKey){this.entriesKey=key;this.source.replace(rows);}}
  private sourceLabel():Resource{return this.state.catalog.catalog?.source==='snapshot'?$r('app.string.eta_catalog_snapshot'):this.state.catalog.catalog?.source==='cache'?$r('app.string.eta_catalog_cache'):this.state.catalog.catalog?.source==='online'?$r('app.string.eta_catalog_online'):this.state.catalog.loading?$r('app.string.eta_catalog_loading'):$r('app.string.eta_catalog_missing');}
  @Builder private refreshButton(){Button({type:ButtonType.Normal}){Image($r('app.media.ic_rounded_refresh')).width(S.Dp24).height(S.Dp24).fillColor($r('app.color.miuix_on_surface'))}.width(S.Dp44).height(S.Dp44).backgroundColor($r('app.color.eta_transparent')).accessibilityText($r('app.string.eta_catalog_refresh')).enabled(!this.state.catalog.loading&&!this.state.catalog.refreshing).onClick(()=>{void this.controller?.community?.refresh();})}
  @Builder private status(){Column(){
    EtaPreferenceRow({title:$r('app.string.eta_catalog_title'),summary:$r('app.string.eta_catalog_key_hint')});EtaPreferenceDivider({hasLeading:false})
    EtaPreferenceRow({title:$r('app.string.eta_catalog_candidates'),summary:$r('app.string.eta_catalog_candidates_hint')});EtaPreferenceDivider({hasLeading:false})
    EtaPreferenceRow({title:this.sourceLabel(),summary:$r('app.string.eta_catalog_provider_count',this.state.catalog.catalog?.providers.length||0)})
    if(this.state.catalog.refreshing){EtaPreferenceDivider({hasLeading:false});EtaPreferenceRow({title:$r('app.string.eta_catalog_refreshing')})}
    if(this.state.catalog.refreshError){EtaPreferenceDivider({hasLeading:false});EtaPreferenceRow({title:this.state.catalog.refreshError})}
  }}
  @Builder private provider(p:CommunityCatalogProvider){Column(){
    Text(p.name).fontSize(F.Body1).fontWeight(FontWeight.Medium).fontColor($r('app.color.miuix_on_surface')).maxLines(1).textOverflow({overflow:TextOverflow.Ellipsis})
    Text(p.baseUrl).fontSize(F.Body2).fontColor($r('app.color.miuix_on_surface_variant_summary')).maxLines(1).textOverflow({overflow:TextOverflow.Ellipsis})
    Text($r('app.string.eta_catalog_models_count',p.models.length)).fontSize(F.Footnote1).fontColor($r('app.color.miuix_on_surface_variant_summary'))
  }.width('100%').alignItems(HorizontalAlign.Start)}
  @Builder private body(){List(){
    ListItem(){TextInput({text:this.state.catalog.query,placeholder:$r('app.string.ui_search_provider_74e049')}).fontSize(F.Body1).fontColor($r('app.color.miuix_on_surface')).backgroundColor($r('app.color.miuix_secondary')).margin({left:S.Dp16,right:S.Dp16,top:S.Dp8,bottom:S.Dp8}).onChange((v:string)=>this.controller?.community?.search(v))}
    ListItem(){ProviderSection({title:$r('app.string.eta_catalog_status'),content:() => { this.status(); }})}
    ListItem(){EtaPreferenceGroupTitle({title:$r('app.string.eta_catalog_provider_count',this.source.totalCount())})}
    if(this.state.catalog.loading){ListItem(){LoadingProgress().width(S.Dp28).height(S.Dp28).margin(S.Dp40)}}
    if(!this.source.totalCount()&&!this.state.catalog.loading){ListItem(){Text($r('app.string.eta_catalog_empty')).fontSize(F.Body1).fontColor($r('app.color.miuix_on_surface_variant_summary')).width('100%').padding(S.Dp24)}}
    LazyForEach(this.source,(p:CommunityCatalogProvider)=>{ListItem(){EtaCard({insidePadding:S.Dp16,clickable:true,sinkFeedback:true,content:()=>{this.provider(p);},onAction:()=>this.controller?.chooseCommunity(p.id)}).margin({left:S.Dp16,right:S.Dp16,top:S.Dp6,bottom:S.Dp6})}},(p:CommunityCatalogProvider)=>renderKey(p.id,p))
  }.width('100%').height('100%').scrollBar(BarState.Auto).edgeEffect(EdgeEffect.Spring)}
  build(){ProviderPage({title:$r('app.string.eta_catalog_add'),blurEnabled:this.state.appearance.blurEnabled,actions:() => { this.refreshButton(); },content:() => { this.body(); },onBack:()=>this.controller?.back()})}
}
@Component
export struct CommunityCatalogProviderScreen {
  @Prop @Watch('refreshRows') state:ShellState=emptyShellState();controller:FoundationController|undefined=undefined;
  private source:EtaListDataSource<CommunityCatalogModel>=new EtaListDataSource<CommunityCatalogModel>();private entriesKey:string='';
  aboutToAppear():void{this.refreshRows();}
  private refreshRows():void{const rows=this.controller?.community?.models()||[];const key=this.state.catalog.selection?.id+'\n'+rows.map(m=>m.modelId).join('\n');if(key!==this.entriesKey){this.entriesKey=key;this.source.replace(rows);}}
  @Builder private importButton(){EtaTextButton({text:$r('app.string.eta_catalog_import',this.state.catalog.selectedIds.length),isEnabled:!!this.state.catalog.selection&&!!this.state.catalog.selectedIds.length&&!this.state.catalog.importing,onAction:()=>{void this.controller?.importCommunity();}})}
  @Builder private providerInfo(){Column(){
    EtaPreferenceRow({title:$r('app.string.ui_name_1be7ae'),summary:this.state.catalog.selection?.name||''});EtaPreferenceDivider({hasLeading:false})
    EtaPreferenceRow({title:$r('app.string.eta_provider_base_url'),summary:this.state.catalog.selection?.baseUrl||''})
  }}
  @Builder private body(){List(){
    ListItem(){ProviderSection({title:$r('app.string.eta_catalog_provider'),content:() => { this.providerInfo(); }})}
    ListItem(){Column({space:S.Dp4}){
      Text($r('app.string.eta_catalog_selected',this.state.catalog.selectedIds.length,this.state.catalog.selection?.models.length||0)).fontSize(F.Body2).fontColor($r('app.color.miuix_on_surface_variant_summary'))
      Text($r('app.string.eta_catalog_import_hint')).fontSize(F.Footnote1).fontColor($r('app.color.miuix_on_surface_variant_summary'))
      if(this.state.catalog.importError){Text(this.state.catalog.importError).fontSize(F.Body2).fontColor($r('app.color.miuix_error'))}
    }.padding({left:S.Dp20,right:S.Dp20,top:S.Dp4,bottom:S.Dp4}).width('100%').alignItems(HorizontalAlign.Start)}
    ListItem(){TextInput({text:this.state.catalog.modelQuery,placeholder:$r('app.string.eta_catalog_search_models')}).fontSize(F.Body1).fontColor($r('app.color.miuix_on_surface')).backgroundColor($r('app.color.miuix_secondary')).margin({left:S.Dp16,right:S.Dp16,top:S.Dp8,bottom:S.Dp8}).onChange((v:string)=>this.controller?.community?.searchModels(v))}
    ListItem(){Row(){EtaTextButton({text:$r('app.string.eta_catalog_select_visible'),isEnabled:!!this.source.totalCount()&&!this.state.catalog.importing,onAction:()=>this.controller?.community?.selectVisible()});Blank();EtaTextButton({text:$r('app.string.eta_catalog_clear'),isEnabled:!!this.state.catalog.selectedIds.length&&!this.state.catalog.importing,onAction:()=>this.controller?.community?.clear()})}.padding({left:S.Dp16,right:S.Dp16}).width('100%')}
    ListItem(){EtaPreferenceGroupTitle({title:$r('app.string.eta_catalog_models_title',this.source.totalCount())})}
    if(!this.source.totalCount()){ListItem(){Text($r('app.string.page_no_matching_model_found_ae7e96')).fontSize(F.Body2).fontColor($r('app.color.miuix_on_surface_variant_summary')).padding(S.Dp24)}}
    LazyForEach(this.source,(model:CommunityCatalogModel)=>{ListItem(){EtaCheckboxPreference({title:model.displayName,summary:model.modelId+' · '+String(model.contextWindow)+' tokens'+(model.releaseDate?' · '+model.releaseDate:''),checked:this.state.catalog.selectedIds.includes(model.modelId),isEnabled:!this.state.catalog.importing,onCheckedChange:()=>this.controller?.community?.toggle(model.modelId)})}.margin({left:S.Dp16,right:S.Dp16}).backgroundColor($r('app.color.miuix_surface_container'))},(model:CommunityCatalogModel)=>renderKey(model.modelId,model))
  }.width('100%').height('100%').scrollBar(BarState.Auto).edgeEffect(EdgeEffect.Spring)}
  build(){ProviderPage({title:this.state.catalog.selection?.name||$r('app.string.eta_catalog_pick'),blurEnabled:this.state.appearance.blurEnabled,actions:() => { this.importButton(); },content:() => { this.body(); },onBack:()=>this.controller?.back()})}
}
