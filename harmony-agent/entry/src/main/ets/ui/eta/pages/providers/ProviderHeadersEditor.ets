// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/pages/providers/ProviderHeadersEditor.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { FoundationController, ShellState, emptyShellState } from '../../../../core/foundation/controller';
import { ProviderHeaderDraft } from '../../../../core/eta/providerConfigDraft';
import { syncSlots } from '../../../../core/eta/renderKey';
import { EtaComponentSizes as S, EtaProviderDefaults as P } from '../../../../core/eta/tokens';
import { ProviderSection, ProviderTextField } from './ProviderComponents';
import { EtaPreferenceRow } from '../../components/EtaPreferenceRow';
import { EtaPreferenceDivider } from '../../components/EtaPreferenceStyle';
@Observed
class ProviderHeaderSlot {
  value: ProviderHeaderDraft;
  constructor(value: ProviderHeaderDraft) { this.value = value; }
}
@Component
struct ProviderHeaderRow {
  @ObjectLink slot: ProviderHeaderSlot;
  controller: FoundationController | undefined = undefined;
  @Prop busy: boolean = false;
  build(){Column(){EtaPreferenceDivider({hasLeading:false})
    Row({space:S.Dp8}){Column({space:S.Dp8}){
      ProviderTextField({value:this.slot.value.name,label:$r('app.string.eta_provider_header_name'),isEnabled:!this.busy,onChange:(v:string)=>this.controller?.editor.header(this.slot.value.id,'name',v)})
      ProviderTextField({value:this.slot.value.value,label:$r('app.string.eta_provider_header_value'),secret:true,isEnabled:!this.busy,onChange:(v:string)=>this.controller?.editor.header(this.slot.value.id,'value',v)})
    }.layoutWeight(1)
      Button({type:ButtonType.Normal}){Image($r('app.media.ic_rounded_delete')).width(S.Dp24).height(S.Dp24).fillColor($r('app.color.miuix_on_surface_variant_actions'))}.width(S.Dp44).height(S.Dp44).padding(S.Dp0).backgroundColor($r('app.color.eta_transparent')).enabled(!this.busy).accessibilityText($r('app.string.ui_delete_3755f5')).onClick(()=>this.controller?.editor.removeHeader(this.slot.value.id))
    }.padding({left:S.Dp16,right:S.Dp6,top:S.Dp12,bottom:S.Dp12})
  }}
}
@Component
export struct ProviderHeadersEditor {
  @Prop state:ShellState=emptyShellState();controller:FoundationController|undefined=undefined;
  private slots:ProviderHeaderSlot[]=[];
  private slotsFor():ProviderHeaderSlot[]{return syncSlots(this.slots,this.state.editor.draft.headerDrafts||[],(h:ProviderHeaderDraft)=>h.id,(v:ProviderHeaderDraft)=>new ProviderHeaderSlot(v));}
  @Builder private chevron(){Image($r('app.media.ic_rounded_expand_more')).width(S.Dp24).height(S.Dp24).rotate({angle:this.state.editor.headersExpanded?P.HeadersExpandedRotation:0}).fillColor($r('app.color.miuix_on_surface_variant_actions'))}
  @Builder private content(){Column(){
    EtaPreferenceRow({title:this.state.editor.draft.headerDrafts?.length?$r('app.string.eta_provider_headers_count',this.state.editor.draft.headerDrafts.length):$r('app.string.eta_provider_headers_none'),summary:$r('app.string.eta_provider_headers_summary'),hasActions:true,endActions:() => { this.chevron(); },onAction:()=>this.controller?.editor.toggleHeaders()})
    if(this.state.editor.headersExpanded){
      ForEach(this.slots=this.slotsFor(),(slot:ProviderHeaderSlot)=>{ProviderHeaderRow({slot:slot,controller:this.controller,busy:this.state.editor.busy})},(slot:ProviderHeaderSlot)=>slot.value.id)
      EtaPreferenceDivider({hasLeading:false})
      EtaPreferenceRow({title:$r('app.string.eta_provider_header_add'),isEnabled:!this.state.editor.busy,onAction:()=>this.controller?.editor.addHeader()})
    }
  }}
  build(){ProviderSection({title:$r('app.string.eta_provider_headers'),content:() => { this.content(); }})}
}
