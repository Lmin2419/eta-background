// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/pages/providers/ProviderHeadersEditor.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { FoundationController, ShellState, emptyShellState } from '../../../../core/foundation/controller';
import { ProviderHeaderDraft } from '../../../../core/eta/providerConfigDraft';
import { EtaComponentSizes as S, EtaProviderDefaults as P } from '../../../../core/eta/tokens';
import { ProviderSection, ProviderTextField } from './ProviderComponents';
import { EtaPreferenceRow } from '../../components/EtaPreferenceRow';
import { EtaPreferenceDivider } from '../../components/EtaPreferenceStyle';
@Component
export struct ProviderHeadersEditor {
  @Prop state:ShellState=emptyShellState();controller:FoundationController|undefined=undefined;
  @Builder private chevron(){Image($r('app.media.ic_rounded_expand_more')).width(S.Dp24).height(S.Dp24).rotate({angle:this.state.editor.headersExpanded?P.HeadersExpandedRotation:0}).fillColor($r('app.color.miuix_on_surface_variant_actions'))}
  @Builder private content(){Column(){
    EtaPreferenceRow({title:this.state.editor.draft.headerDrafts?.length?$r('app.string.eta_provider_headers_count',this.state.editor.draft.headerDrafts.length):$r('app.string.eta_provider_headers_none'),summary:$r('app.string.eta_provider_headers_summary'),hasActions:true,endActions:() => { this.chevron(); },onAction:()=>this.controller?.editor.toggleHeaders()})
    if(this.state.editor.headersExpanded){
      ForEach(this.state.editor.draft.headerDrafts||[],(h:ProviderHeaderDraft)=>{EtaPreferenceDivider({hasLeading:false})
        Row({space:S.Dp8}){Column({space:S.Dp8}){
          ProviderTextField({value:h.name,label:$r('app.string.eta_provider_header_name'),isEnabled:!this.state.editor.busy,onChange:(v:string)=>this.controller?.editor.header(h.id,'name',v)})
          ProviderTextField({value:h.value,label:$r('app.string.eta_provider_header_value'),secret:true,isEnabled:!this.state.editor.busy,onChange:(v:string)=>this.controller?.editor.header(h.id,'value',v)})
        }.layoutWeight(1)
          Button({type:ButtonType.Normal}){Image($r('app.media.ic_rounded_delete')).width(S.Dp24).height(S.Dp24).fillColor($r('app.color.miuix_on_surface_variant_actions'))}.width(S.Dp44).height(S.Dp44).padding(S.Dp0).backgroundColor($r('app.color.eta_transparent')).enabled(!this.state.editor.busy).accessibilityText($r('app.string.ui_delete_3755f5')).onClick(()=>this.controller?.editor.removeHeader(h.id))
        }.padding({left:S.Dp16,right:S.Dp6,top:S.Dp12,bottom:S.Dp12})
      },(h:ProviderHeaderDraft)=>h.id)
      EtaPreferenceDivider({hasLeading:false})
      EtaPreferenceRow({title:$r('app.string.eta_provider_header_add'),isEnabled:!this.state.editor.busy,onAction:()=>this.controller?.editor.addHeader()})
    }
  }}
  build(){ProviderSection({title:$r('app.string.eta_provider_headers'),content:() => { this.content(); }})}
}
