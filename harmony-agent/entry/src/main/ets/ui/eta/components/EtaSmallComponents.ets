// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/components/EtaFeatureCard.kt, EtaChoiceChip.kt, ToolChip.kt, SectionHeader.kt, ListEmptyState.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { EtaComponentSizes as S, EtaComponentBehavior as B, MiuixFontSizes as F } from '../../../core/eta/tokens';
import { EtaCard, EmptyEtaSlot } from './EtaCard';

@Component
export struct EtaFeatureCard {
  @Prop title: ResourceStr = '';
  @Prop summary: ResourceStr = '';
  @Prop isEnabled: boolean = true;
  @Prop clickable: boolean = true;
  @Prop hasSummary: boolean = true;
  @Prop hasFooter: boolean = false;
  @BuilderParam icon: () => void = EmptyEtaSlot;
  @BuilderParam actions: () => void = EmptyEtaSlot;
  @BuilderParam footer: () => void = EmptyEtaSlot;
  onAction: () => void = () => {};
  @Builder private body() {
    Column() {
      Row() { this.icon(); Blank(); this.actions() }.width('100%').constraintSize({ minHeight: S.Dp40 })
      Text(this.title).width('100%').fontSize(F.Body1).fontWeight(FontWeight.Medium).maxLines(2)
        .textOverflow({ overflow: TextOverflow.Ellipsis }).margin({ top: S.Dp8 })
        .fontColor(this.isEnabled ? $r('app.color.miuix_on_surface_container') : $r('app.color.miuix_disabled_on_secondary_variant'))
      if (this.hasSummary) {
        Text(this.summary).width('100%').fontSize(F.Body2).maxLines(3).textOverflow({ overflow: TextOverflow.Ellipsis })
          .margin({ top: S.Dp4 }).fontColor($r('app.color.miuix_on_surface_variant_summary'))
      }
      if (this.hasFooter) { this.footer() }
    }.width('100%').constraintSize({ minHeight: S.Dp136 - S.Dp16 - S.Dp16 }).alignItems(HorizontalAlign.Start)
  }
  build() {
    EtaCard({ content: this.body, insidePadding: S.Dp16, isEnabled: this.isEnabled, clickable: this.clickable, sinkFeedback: true, showIndication: true, onAction: this.onAction })
  }
}
@Component
export struct EtaChoiceChip {
  @Consume('etaPreferenceTheme') preferenceTheme: boolean = false;
  @Prop text: ResourceStr = '';
  @Prop selected: boolean = false;
  @Prop isEnabled: boolean = true;
  onAction: () => void = () => {};
  build() {
    Text(this.text).fontSize(F.Footnote1).maxLines(1).textOverflow({ overflow: TextOverflow.Ellipsis })
      .fontColor(!this.isEnabled ? $r('app.color.miuix_disabled_on_secondary_variant') : this.selected ? (this.preferenceTheme ? $r('app.color.eta_settings_primary') : $r('app.color.miuix_primary')) : $r('app.color.miuix_on_surface_variant_summary'))
      .padding({ left: S.Dp14, right: S.Dp14, top: S.Dp7, bottom: S.Dp7 })
      .backgroundColor(this.selected ? (this.preferenceTheme ? $r('app.color.eta_settings_chip_selected') : $r('app.color.eta_chip_selected')) : $r('app.color.eta_transparent'))
      .border({ width: S.Dp1, color: this.selected ? $r('app.color.eta_transparent') : $r('app.color.miuix_divider_line') })
      .borderRadius(S.Dp16).animation({ duration: B.ChoiceDuration }).enabled(this.isEnabled)
      .accessibilityRole(AccessibilityRoleType.BUTTON).accessibilitySelected(this.selected)
      .onClick(() => { if (this.isEnabled) { this.onAction(); } })
  }
}
@Component
export struct ToolChip {
  @Prop label: ResourceStr = '';
  build() {
    Text(this.label).fontSize(F.Footnote1).fontColor($r('app.color.miuix_on_surface_variant_summary'))
      .padding({ left: S.Dp8, right: S.Dp8, top: S.Dp4, bottom: S.Dp4 })
      .backgroundColor($r('app.color.miuix_surface_container_high')).borderRadius(S.Dp8)
  }
}
@Component
export struct SectionHeader {
  @Prop title: ResourceStr = '';
  build() {
    Text(this.title).width('100%').fontSize(F.Subtitle).fontColor($r('app.color.miuix_on_background_variant'))
      .padding({ left: S.Dp28, right: S.Dp28, top: S.Dp8, bottom: S.Dp8 })
  }
}
@Component
export struct ListEmptyState {
  @Prop title: ResourceStr = '';
  @Prop summary: ResourceStr = '';
  @Prop hasIcon: boolean = false;
  @Prop hasAction: boolean = false;
  @BuilderParam icon: () => void = EmptyEtaSlot;
  @BuilderParam action: () => void = EmptyEtaSlot;
  build() {
    Column({ space: S.Dp6 }) {
      if (this.hasIcon) {
        Stack() { this.icon() }.width(S.Dp48).height(S.Dp48)
          .backgroundColor($r('app.color.miuix_surface_container_high')).borderRadius(S.Dp16).margin({ bottom: S.Dp6 })
      }
      Text(this.title).fontSize(this.summary === '' ? F.Body2 : F.Body1)
        .fontWeight(this.summary === '' ? FontWeight.Regular : FontWeight.Medium)
        .fontColor(this.summary === '' ? $r('app.color.miuix_on_surface_variant_summary') : $r('app.color.miuix_on_surface'))
        .textAlign(TextAlign.Center)
      if (this.summary !== '') {
        Text(this.summary).fontSize(F.Body2).fontColor($r('app.color.miuix_on_surface_variant_summary')).textAlign(TextAlign.Center)
      }
      if (this.hasAction) { Column() { this.action() }.margin({ top: S.Dp10 }) }
    }.width('100%').padding({ left: S.Dp24, right: S.Dp24, top: S.Dp40, bottom: S.Dp40 })
  }
}
