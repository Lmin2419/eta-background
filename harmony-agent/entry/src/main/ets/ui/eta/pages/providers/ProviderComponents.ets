// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/pages/providers/ProviderComponents.kt,
// ModelProviderDetailScreen.kt @ 3b1794d. Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { EtaComponentSizes as S, EtaPageDefaults as P, MiuixFontSizes as F } from '../../../../core/eta/tokens';
import { EmptyEtaSlot } from '../../components/EtaCard';
import { EtaPreferenceGroup, EtaPreferenceGroupTitle } from '../../components/EtaPreferenceStyle';
import { AdaptiveTopAppBar } from '../../components/AdaptiveTopAppBar';
import { MiuixBackButton } from '../../components/MiuixBackButton';
import { TopBarBackdrop } from '../../components/TopBarBackdrop';
import { modelBrandResource } from '../../components/ModelBrandResources';
@Component
export struct ProviderSection {
  @Prop title:ResourceStr='';@BuilderParam content:()=>void=EmptyEtaSlot;
  build(){Column(){if(this.title!==''){EtaPreferenceGroupTitle({title:this.title})}EtaPreferenceGroup({content:() => { this.content(); }})}.width('100%')}
}
@Component
export struct ProviderBrandIcon {
  @Prop source:string='';@Prop model:string='';
  build(){Image(modelBrandResource(this.model,this.source)||$r('app.media.ic_rounded_dns')).width(S.Dp24).height(S.Dp24).borderRadius(S.Dp24).objectFit(ImageFit.Contain).accessibilityLevel('no')}
}
@Component
export struct ProviderPage {
  @Provide('etaPreferenceTheme') preferenceTheme:boolean=true;
  @Prop title:ResourceStr='';@Prop blurEnabled:boolean=true;
  @BuilderParam content:()=>void=EmptyEtaSlot;@BuilderParam actions:()=>void=EmptyEtaSlot;
  onBack:()=>void=()=>{};@State private widthVp:number=S.Dp0;
  @Builder private back(){MiuixBackButton({onAction:this.onBack})}
  @Builder private bar(){AdaptiveTopAppBar({title:this.title,wide:this.widthVp>=P.WideScreenMinWidth,navigationIcon:() => { this.back(); },actions:() => { this.actions(); }})}
  build(){Column(){
    TopBarBackdrop({isEnabled:this.blurEnabled,content:() => { this.bar(); }})
    Column(){this.content()}.width('100%').layoutWeight(1).padding({left:Math.max(S.Dp0,(this.widthVp-P.MaxPageContentWidth)/2),right:Math.max(S.Dp0,(this.widthVp-P.MaxPageContentWidth)/2)})
  }.width('100%').height('100%').backgroundColor($r('app.color.eta_settings_background')).onAreaChange((_old,area)=>{this.widthVp=Number(area.width);})}
}
@Component
export struct ProviderTextField {
  @Prop value:string='';@Prop label:ResourceStr='';@Prop isEnabled:boolean=true;@Prop secret:boolean=false;
  onChange:(value:string)=>void=()=>{};
  build(){Column({space:S.Dp4}){
    Text(this.label).fontSize(F.Footnote1).fontColor($r('app.color.miuix_on_surface_variant_summary')).width('100%')
    TextInput({text:this.value,placeholder:this.label}).type(this.secret?InputType.Password:InputType.Normal).showPasswordIcon(true)
      .fontSize(F.Body1).fontColor($r('app.color.miuix_on_surface')).backgroundColor($r('app.color.miuix_secondary')).enabled(this.isEnabled).onChange(this.onChange)
  }.width('100%')}
}
