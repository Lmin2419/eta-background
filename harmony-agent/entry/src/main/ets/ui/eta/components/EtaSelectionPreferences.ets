// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/components/EtaSelectionPreferences.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { EtaComponentSizes as S } from '../../../core/eta/tokens';
import { EmptyEtaSlot } from './EtaCard';
import { EtaPreferenceRow } from './EtaPreferenceRow';
import { EtaSwitch } from './EtaControls';

// Miuix RadioButton is a checkmark, not a circular Android RadioButton.
@Component
export struct EtaSelectionIndicator {
  @Consume('etaPreferenceTheme') preferenceTheme: boolean = false;
  @Prop selected: boolean = false;
  @Prop isEnabled: boolean = true;
  @Prop checkbox: boolean = false;
  build() {
    Stack() {
      if (this.selected) {
        Image(this.checkbox ? $r('app.media.ic_miuix_checkbox_check') : $r('app.media.ic_miuix_radio_check'))
          .width(S.Dp26).height(S.Dp26)
          .fillColor(this.checkbox ? (this.isEnabled ? $r('app.color.miuix_on_primary') : $r('app.color.miuix_disabled_on_primary')) : this.isEnabled ? (this.preferenceTheme ? $r('app.color.eta_settings_primary') : $r('app.color.miuix_primary')) : $r('app.color.miuix_disabled_primary'))
      }
    }.width(S.Dp26).height(S.Dp26).borderRadius(S.Dp26)
      .backgroundColor(this.checkbox ? (this.selected ? (this.isEnabled ? (this.preferenceTheme ? $r('app.color.eta_settings_primary') : $r('app.color.miuix_primary')) : $r('app.color.miuix_disabled_primary')) : (this.isEnabled ? $r('app.color.miuix_secondary') : $r('app.color.miuix_disabled_secondary'))) : $r('app.color.eta_transparent'))
      .accessibilityLevel('no').hitTestBehavior(HitTestMode.None)
  }
}
@Component
export struct EtaSwitchPreference {
  @Prop title: ResourceStr = '';
  @Prop summary: ResourceStr = '';
  @Prop checked: boolean = false;
  @Prop isEnabled: boolean = true;
  @Prop hasLeading: boolean = false;
  @Prop hasBottom: boolean = false;
  @BuilderParam startAction: () => void = EmptyEtaSlot;
  @BuilderParam bottomAction: () => void = EmptyEtaSlot;
  onCheckedChange: (checked: boolean) => void = () => {};
  @Builder private trailing() {
    EtaSwitch({ checked: this.checked, isEnabled: this.isEnabled })
      .hitTestBehavior(HitTestMode.None).accessibilityLevel('no')
  }
  build() {
    EtaPreferenceRow({ title: this.title, summary: this.summary, isEnabled: this.isEnabled,
      hasLeading: this.hasLeading, hasBottom: this.hasBottom, hasActions: true,
      startAction: this.startAction, bottomAction: this.bottomAction, endActions: this.trailing,
      onAction: () => { this.onCheckedChange(!this.checked); } })
      .accessibilityRole(AccessibilityRoleType.SWITCH).accessibilityChecked(this.checked)
  }
}
@Component
export struct EtaRadioButtonPreference {
  @Prop title: ResourceStr = '';
  @Prop summary: ResourceStr = '';
  @Prop selected: boolean = false;
  @Prop isEnabled: boolean = true;
  @Prop hasLeading: boolean = false;
  @Prop hasBottom: boolean = false;
  @BuilderParam startAction: () => void = EmptyEtaSlot;
  @BuilderParam bottomAction: () => void = EmptyEtaSlot;
  onAction: () => void = () => {};
  @Builder private trailing() { EtaSelectionIndicator({ selected: this.selected, isEnabled: this.isEnabled }) }
  build() {
    EtaPreferenceRow({ title: this.title, summary: this.summary, isEnabled: this.isEnabled,
      hasLeading: this.hasLeading, hasBottom: this.hasBottom, hasActions: true,
      startAction: this.startAction, bottomAction: this.bottomAction, endActions: this.trailing, onAction: this.onAction })
      .accessibilityRole(AccessibilityRoleType.RADIO).accessibilitySelected(this.selected)
  }
}
@Component
export struct EtaCheckboxPreference {
  @Prop title: ResourceStr = '';
  @Prop summary: ResourceStr = '';
  @Prop checked: boolean = false;
  @Prop isEnabled: boolean = true;
  @Prop checkboxAtStart: boolean = false;
  @Prop hasLeading: boolean = false;
  @Prop hasBottom: boolean = false;
  @BuilderParam startAction: () => void = EmptyEtaSlot;
  @BuilderParam bottomAction: () => void = EmptyEtaSlot;
  onCheckedChange?: (checked: boolean) => void;
  @Builder private checkbox() { EtaSelectionIndicator({ selected: this.checked, isEnabled: this.isEnabled, checkbox: true }) }
  @Builder private leading() {
    Row({ space: S.Dp8 }) {
      if (this.checkboxAtStart) { this.checkbox() }
      if (this.hasLeading) { this.startAction() }
    }
  }
  build() {
    EtaPreferenceRow({ title: this.title, summary: this.summary, isEnabled: this.isEnabled,
      hasLeading: this.checkboxAtStart || this.hasLeading, hasBottom: this.hasBottom, hasActions: !this.checkboxAtStart,
      startAction: this.leading, bottomAction: this.bottomAction, endActions: this.checkbox,
      onAction: () => { this.onCheckedChange?.(!this.checked); } })
      .accessibilityRole(AccessibilityRoleType.CHECKBOX).accessibilityChecked(this.checked)
  }
}
