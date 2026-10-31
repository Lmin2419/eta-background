// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/pages/providers/RemoteModelImportSheet.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { FoundationController, ShellState, emptyShellState } from '../../../../core/foundation/controller';
import { EtaComponentSizes as S, MiuixFontSizes as F } from '../../../../core/eta/tokens';
import { EtaListDataSource } from '../../components/EtaListDataSource';
import { EtaCheckboxPreference } from '../../components/EtaSelectionPreferences';
import { EtaTextButton } from '../../components/EtaControls';
@Component
export struct RemoteModelImportSheet {
  @Prop @Watch('refresh') state:ShellState=emptyShellState();controller:FoundationController|undefined=undefined;
  private source:EtaListDataSource<string>=new EtaListDataSource<string>();private entriesKey:string='';
  aboutToAppear():void{this.refresh();}
  private refresh():void{if(this.state.sheet!=='catalog')return;const models=this.controller?.editor.filteredCatalog()||[];const key=models.join('\n');if(key!==this.entriesKey){this.entriesKey=key;this.source.replace(models);}}
  private allSelected():boolean{const rows=this.controller?.editor.filteredCatalog()||[];return !!rows.length&&rows.every(id=>this.state.editor.selected.includes(id));}
  private name(id:string):string{return this.state.editor.draft.metadata?.find(m=>m.id===id)?.name||id;}
  build(){Column(){
    Text($r('app.string.remote_model_import_title')).fontSize(F.Title4).fontColor($r('app.color.miuix_on_surface')).padding(S.Dp16)
    Row(){Text($r('app.string.remote_model_import_summary',this.state.editor.catalog.length)).fontSize(F.Footnote1).fontColor($r('app.color.miuix_on_surface_variant_summary')).layoutWeight(1)
      EtaTextButton({text:this.allSelected()?$r('app.string.page_select_none_ba20eb'):$r('app.string.page_select_all_3e44b2'),isEnabled:!!this.source.totalCount(),onAction:()=>this.controller?.editor.toggleVisible()})
    }.padding({left:S.Dp24,right:S.Dp12})
    TextInput({text:this.state.editor.query,placeholder:$r('app.string.ui_search_model_df5586')}).fontSize(F.Body1).fontColor($r('app.color.miuix_on_surface')).backgroundColor($r('app.color.miuix_secondary')).margin({left:S.Dp20,right:S.Dp20,top:S.Dp4,bottom:S.Dp6}).onChange((v:string)=>this.controller?.editor.search(v))
    List(){
      if(!this.source.totalCount()){ListItem(){Text($r('app.string.page_no_matching_model_found_ae7e96')).fontSize(F.Body2).fontColor($r('app.color.miuix_on_surface_variant_summary')).width('100%').padding({left:S.Dp20,right:S.Dp20,top:S.Dp48,bottom:S.Dp48})}}
      LazyForEach(this.source,(id:string)=>{ListItem(){EtaCheckboxPreference({title:this.name(id),summary:this.name(id).toLowerCase()===id.toLowerCase()?'':id,checked:this.state.editor.selected.includes(id),onCheckedChange:()=>this.controller?.editor.toggle(id)})}},(id:string)=>id)
    }.layoutWeight(1).width('100%')
    EtaTextButton({text:this.state.editor.selected.length?$r('app.string.remote_model_import_confirm',this.state.editor.selected.length):$r('app.string.remote_model_import_pick_hint'),primary:true,isEnabled:this.state.editor.selected.length>0,onAction:()=>this.controller?.importModels()}).width('100%').padding({left:S.Dp20,right:S.Dp20,top:S.Dp8,bottom:S.Dp12})
  }.height('100%').width('100%').backgroundColor($r('app.color.miuix_surface'))}
}
