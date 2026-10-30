// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/components/TopBarBackdrop.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { EtaBlurDefaults as B, EtaComponentSizes as S } from '../../../core/eta/tokens';
import { EmptyEtaSlot } from './EtaCard';
@Component
export struct TopBarBackdrop {
  @Prop isEnabled: boolean = true;
  // Progressive blur is applied to the underlying content by the scaffold, never to toolbar text.
  @Prop progressive: boolean = false;
  @Prop progressiveContentReady: boolean = false;
  @BuilderParam content: () => void = EmptyEtaSlot;
  build() {
    Column() { this.content() }.width('100%')
      .backgroundColor(this.isEnabled ? (this.progressive ? (this.progressiveContentReady ? $r('app.color.eta_top_bar_progressive_surface') : $r('app.color.miuix_surface')) : $r('app.color.eta_top_bar_surface')) : $r('app.color.miuix_surface'))
      .backdropBlur(this.isEnabled && !this.progressive ? B.TopBarBlurRadius : S.Dp0)
  }
}
