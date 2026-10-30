// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/components/TopBarBackdrop.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { EtaBlurDefaults as B, EtaComponentSizes as S } from '../../../core/eta/tokens';
import { EmptyEtaSlot } from './EtaCard';
@Component
export struct TopBarBackdrop {
  @Prop isEnabled: boolean = true;
  // ArkUI backdropBlur implements the uniform branch. Progressive texture sampling has
  // no identical public ArkUI counterpart; use Eta's solid fallback until it is validated.
  @Prop progressive: boolean = false;
  @BuilderParam content: () => void = EmptyEtaSlot;
  build() {
    Column() { this.content() }.width('100%')
      .backgroundColor(this.isEnabled && !this.progressive ? $r('app.color.eta_top_bar_surface') : $r('app.color.miuix_surface'))
      .backdropBlur(this.isEnabled && !this.progressive ? B.TopBarBlurRadius : S.Dp0)
  }
}
