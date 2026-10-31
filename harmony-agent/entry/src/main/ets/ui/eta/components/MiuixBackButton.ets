// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/components/MiuixBackButton.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { MiuixIconButtonDefaults as B, EtaComponentSizes as S } from '../../../core/eta/tokens';
@Component
export struct MiuixBackButton {
  onAction: () => void = () => {};
  build() {
    Button({ type: ButtonType.Normal }) {
      Image($r('app.media.ic_rounded_arrow_back')).width(S.Dp24).height(S.Dp24)
        .fillColor($r('app.color.miuix_on_background')).matchTextDirection(true)
    }.width(B.MinWidth).height(B.MinHeight).padding(S.Dp0).borderRadius(B.CornerRadius)
      .backgroundColor($r('app.color.eta_transparent')).accessibilityText($r('app.string.action_back'))
      .accessibilityRole(AccessibilityRoleType.BACK_BUTTON).onClick(this.onAction)
  }
}
