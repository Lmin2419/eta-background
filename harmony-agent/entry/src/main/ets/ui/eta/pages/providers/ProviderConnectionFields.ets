// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/pages/providers/ProviderConnectionFields.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { FoundationController, ShellState, emptyShellState } from '../../../../core/foundation/controller';
import { EtaComponentSizes as S } from '../../../../core/eta/tokens';
import { ProviderTextField } from './ProviderComponents';
import { EtaDropdownPreference, EtaDropdownItem } from '../../components/EtaDropdownPreference';
import { EtaPreferenceDivider } from '../../components/EtaPreferenceStyle';
import { EtaSwitchPreference } from '../../components/EtaSelectionPreferences';
@Component
export struct ProviderConnectionFields {
  @Prop state:ShellState=emptyShellState();controller:FoundationController|undefined=undefined;
  private protocols():EtaDropdownItem[]{return[new EtaDropdownItem('chat_completions',$r('app.string.eta_provider_chat_api')),new EtaDropdownItem('responses',$r('app.string.eta_provider_responses_api'))];}
  build(){Column(){
    Column({space:S.Dp12}){
      ProviderTextField({value:this.state.editor.draft.name,label:$r('app.string.ui_name_1be7ae'),isEnabled:!this.state.editor.busy,onChange:(v:string)=>this.controller?.editor.text('name',v)})
      ProviderTextField({value:this.state.editor.draft.baseUrl,label:$r('app.string.eta_provider_base_url'),isEnabled:!this.state.editor.busy,onChange:(v:string)=>this.controller?.editor.text('baseUrl',v)})
      ProviderTextField({value:this.state.editor.draft.apiKey,label:$r('app.string.eta_provider_api_key'),secret:true,isEnabled:!this.state.editor.busy,onChange:(v:string)=>this.controller?.editor.text('apiKey',v)})
    }.padding(S.Dp16)
    EtaPreferenceDivider({hasLeading:false})
    EtaDropdownPreference({title:$r('app.string.ui_endpoint_mode_3c8546'),summary:this.state.editor.draft.protocol==='responses'?$r('app.string.page_using_typed_items_with_semantic_streaming_events_f9c906'):$r('app.string.page_use_standard_chat_completions_ee4b1a'),isEnabled:!this.state.editor.busy,items:this.protocols(),selectedIndex:this.state.editor.draft.protocol==='responses'?1:0,onSelectedIndexChange:(index:number)=>this.controller?.editor.protocol(index===1?'responses':'chat_completions')})
    EtaPreferenceDivider({hasLeading:false})
    EtaSwitchPreference({title:$r('app.string.eta_provider_http'),checked:this.state.editor.draft.allowInsecureHttp,isEnabled:!this.state.editor.busy,onCheckedChange:(v:boolean)=>this.controller?.editor.flag('allowInsecureHttp',v)})
    EtaPreferenceDivider({hasLeading:false})
    EtaSwitchPreference({title:$r('app.string.eta_provider_secure_key'),summary:$r('app.string.eta_provider_secure_key_summary'),checked:this.state.editor.draft.rememberKey,isEnabled:!this.state.editor.busy,onCheckedChange:(v:boolean)=>this.controller?.editor.flag('rememberKey',v)})
  }}
}
