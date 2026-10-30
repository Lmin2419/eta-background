// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/components/EtaPreferenceStyle.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { EtaPreferenceDefaults as P, EtaComponentSizes as S, MiuixFontSizes as F } from '../../../core/eta/tokens';
import { EtaCard, EmptyEtaSlot } from './EtaCard';

export enum EtaPreferenceCategory { Blue, Green, Orange, Yellow, Default }
export function preferenceCategoryColor(category: EtaPreferenceCategory): ResourceColor {
  switch (category) {
    case EtaPreferenceCategory.Blue: return $r('app.color.eta_preference_blue');
    case EtaPreferenceCategory.Green: return $r('app.color.eta_preference_green');
    case EtaPreferenceCategory.Orange: return $r('app.color.eta_preference_orange');
    case EtaPreferenceCategory.Yellow: return $r('app.color.eta_preference_yellow');
    default: return $r('app.color.miuix_on_background');
  }
}
@Component
export struct EtaPreferenceTheme {
  @Provide('etaPreferenceTheme') preferenceTheme: boolean = true;
  @BuilderParam content: () => void = EmptyEtaSlot;
  build() { Column() { this.content() }.width('100%').backgroundColor($r('app.color.eta_settings_background')) }
}
@Component
export struct EtaPreferenceGroup {
  @BuilderParam content: () => void = EmptyEtaSlot;
  build() {
    Column() { EtaCard({ content: this.content }) }.width('100%')
      .padding({ left: P.SidePadding, right: P.SidePadding, bottom: P.GroupSpacing })
  }
}
@Component
export struct EtaPreferenceGroupTitle {
  @Prop title: ResourceStr = '';
  build() {
    Text(this.title).width('100%').fontSize(F.Subtitle).fontColor($r('app.color.miuix_on_surface_variant_summary'))
      .padding({ left: S.Dp32, right: S.Dp32, top: S.Dp4, bottom: S.Dp8 })
  }
}
@Component
export struct EtaPreferenceIcon {
  @Prop icon: Resource = $r('app.media.ic_rounded_settings');
  @Prop optical: boolean = false;
  @Prop isEnabled: boolean = true;
  @Prop color: ResourceColor = $r('app.color.miuix_on_background');
  build() {
    Stack() {
      Image(this.icon).width(this.optical ? P.OpticalIconSize : P.IconSize)
        .height(this.optical ? P.OpticalIconSize : P.IconSize)
        .fillColor(this.isEnabled ? this.color : $r('app.color.miuix_disabled_on_surface'))
    }.width(P.IconSize).height(P.IconSize).accessibilityLevel('no')
  }
}
@Component
export struct EtaPreferenceDivider {
  @Prop hasLeading: boolean = true;
  build() {
    Divider().strokeWidth(P.DividerThickness).color($r('app.color.eta_preference_divider'))
      .margin({ left: P.contentStart(this.hasLeading), right: P.SidePadding })
  }
}
