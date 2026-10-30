// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/components/EtaControls.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { EtaControlDefaults as C, MiuixButtonDefaults as B, MiuixFontSizes as F } from '../../../core/eta/tokens';

import { EtaHaptic } from '../../../core/eta/hapticFeedback';
import { EtaHaptics } from '../../../platform/EtaHaptics';

@Component
export struct EtaTextButton {
  @Consume('etaPreferenceTheme') preferenceTheme: boolean = false;
  @Prop text: ResourceStr = '';
  @Prop isEnabled: boolean = true;
  @Prop primary: boolean = false;
  @Prop destructive: boolean = false;
  onAction: () => void = () => {};
  private containerColor(): ResourceColor {
    if (!this.isEnabled) return this.primary ? $r('app.color.miuix_disabled_primary_button') : $r('app.color.miuix_disabled_secondary_variant');
    return this.destructive ? $r('app.color.miuix_error') : this.primary ? (this.preferenceTheme ? $r('app.color.eta_settings_primary') : $r('app.color.miuix_primary')) : $r('app.color.miuix_secondary_variant');
  }
  private foreground(): ResourceColor {
    if (!this.isEnabled) return this.primary ? $r('app.color.miuix_disabled_on_primary_button') : $r('app.color.miuix_disabled_on_secondary_variant');
    return this.destructive ? $r('app.color.miuix_on_error') : this.primary ? $r('app.color.miuix_on_primary') : $r('app.color.miuix_on_secondary_variant');
  }
  build() {
    Button({ type: ButtonType.Normal }) {
      Text(this.text).fontSize(F.Button).fontColor(this.foreground()).textAlign(TextAlign.Center)
    }.constraintSize({ minWidth: B.MinWidth, minHeight: C.ButtonMinHeight }).borderRadius(C.ButtonCornerRadius)
      .padding({ left: C.ButtonPadding.horizontal, right: C.ButtonPadding.horizontal,
        top: C.ButtonPadding.vertical, bottom: C.ButtonPadding.vertical })
      .backgroundColor(this.containerColor()).enabled(this.isEnabled)
      .onClick(() => { if (this.isEnabled) { this.onAction(); } })
  }
}
@Component
export struct EtaSwitch {
  @Consume('etaPreferenceTheme') preferenceTheme: boolean = false;
  @Prop checked: boolean = false;
  @Prop isEnabled: boolean = true;
  onCheckedChange: (checked: boolean) => void = () => {};
  build() {
    Toggle({ type: ToggleType.Switch, isOn: this.checked })
      .width(C.SwitchWidth).height(C.SwitchHeight).enabled(this.isEnabled)
      .selectedColor((this.preferenceTheme ? $r('app.color.eta_settings_primary') : $r('app.color.miuix_primary'))).switchPointColor($r('app.color.miuix_on_primary'))
      .backgroundColor($r('app.color.miuix_secondary_variant'))
      .onChange((checked: boolean) => { if (this.isEnabled && checked !== this.checked) { this.onCheckedChange(checked); EtaHaptics.perform(checked ? EtaHaptic.ToggleOn : EtaHaptic.ToggleOff); } })
  }
}
