// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/components/EtaPreferenceRow.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { EtaPreferenceDefaults as P, EtaComponentSizes as S, EtaComponentBehavior as B, MiuixFontSizes as F } from '../../../core/eta/tokens';
import { measurePreferenceRow } from '../../../core/eta/etaPreferenceRowLayout';
import { EmptyEtaSlot } from './EtaCard';

@Component
export struct EtaPreferenceRow {
  @Prop title: ResourceStr = '';
  @Prop summary: ResourceStr = '';
  @Prop isEnabled: boolean = true;
  @Prop holding: boolean = false;
  @Prop hasLeading: boolean = false;
  @Prop hasActions: boolean = false;
  @Prop hasBottom: boolean = false;
  @Prop hasCustomTitle: boolean = false;
  @BuilderParam startAction: () => void = EmptyEtaSlot;
  @BuilderParam endActions: () => void = EmptyEtaSlot;
  @BuilderParam bottomAction: () => void = EmptyEtaSlot;
  @BuilderParam titleAction: () => void = EmptyEtaSlot;
  onAction: () => void = () => {};
  onLongClick: () => void = () => {};
  @State private leadingWidth: number = S.Dp0;
  @State private availableWidth: number = S.Dp0;
  @State private pressed: boolean = false;

  build() {
    Column() {
      Row() {
        if (this.hasLeading) {
          Row() { this.startAction() }.margin({ right: P.IconTextGap })
            .onAreaChange((_old, area) => { this.leadingWidth = Number(area.width); })
        }
        Column() {
          if (this.hasCustomTitle) { this.titleAction() }
          else {
            Text(this.title).width('100%').fontSize(F.Body1).fontWeight(FontWeight.Medium)
              .fontColor(this.isEnabled ? $r('app.color.miuix_on_background') : $r('app.color.miuix_disabled_on_secondary_variant'))
          }
          if (!this.hasCustomTitle && this.summary !== '') {
            Text(this.summary).width('100%').fontSize(F.Body2)
              .fontColor(this.isEnabled ? $r('app.color.miuix_on_surface_variant_summary') : $r('app.color.miuix_disabled_on_secondary_variant'))
          }
        }.layoutWeight(1).alignItems(HorizontalAlign.Start)
        if (this.hasActions) {
          Row() { this.endActions() }.margin({ left: measurePreferenceRow(this.availableWidth, this.hasLeading ? this.leadingWidth : null, true).actionGap })
            .constraintSize({ maxWidth: measurePreferenceRow(this.availableWidth, this.hasLeading ? this.leadingWidth : null, true).actionMaxWidth })
        }
      }.width('100%').constraintSize({ minHeight: P.RowMinHeight })
        .padding({ left: P.SidePadding, right: P.SidePadding, top: S.Dp12, bottom: S.Dp12 })
        .onAreaChange((_old, area) => { this.availableWidth = Number(area.width) - P.SidePadding - P.SidePadding; })
      if (this.hasBottom) {
        Column() { this.bottomAction() }.width('100%')
          .padding({ left: P.SidePadding, right: P.SidePadding, bottom: S.Dp12 })
      }
    }.width('100%')
      .backgroundColor(this.holding || this.pressed ? $r('app.color.eta_preference_held') : $r('app.color.eta_transparent'))
      .enabled(this.isEnabled)
      .onTouch((event: TouchEvent) => { this.pressed = event.type === TouchType.Down; })
      .onClick(() => { if (this.isEnabled) this.onAction(); })
      .gesture(LongPressGesture().onAction(() => { if (this.isEnabled) this.onLongClick(); }))
  }
}
@Component
export struct EtaPreference {
  @Prop title: ResourceStr = '';
  @Prop summary: ResourceStr = '';
  @Prop isEnabled: boolean = true;
  @Prop hasLeading: boolean = false;
  @Prop hasBottom: boolean = false;
  @BuilderParam startAction: () => void = EmptyEtaSlot;
  @BuilderParam bottomAction: () => void = EmptyEtaSlot;
  onAction: () => void = () => {};
  onLongClick: () => void = () => {};
  build() {
    EtaPreferenceRow({ title: this.title, summary: this.summary, isEnabled: this.isEnabled,
      hasLeading: this.hasLeading, hasBottom: this.hasBottom,
      startAction: this.startAction, bottomAction: this.bottomAction,
      onAction: this.onAction, onLongClick: this.onLongClick })
  }
}
@Component
export struct EtaArrowPreference {
  @Prop title: ResourceStr = '';
  @Prop summary: ResourceStr = '';
  @Prop isEnabled: boolean = true;
  @Prop holding: boolean = false;
  @Prop hasLeading: boolean = false;
  @Prop hasEndActions: boolean = false;
  @Prop hasBottom: boolean = false;
  @BuilderParam startAction: () => void = EmptyEtaSlot;
  @BuilderParam endActions: () => void = EmptyEtaSlot;
  @BuilderParam bottomAction: () => void = EmptyEtaSlot;
  onAction: () => void = () => {};
  @Builder
  private trailing() {
    Row({ space: S.Dp8 }) {
      if (this.hasEndActions) { this.endActions() }
      Image($r('app.media.ic_miuix_basic_arrow_right')).width(S.Dp8).height(S.Dp14)
        .fillColor(this.isEnabled ? $r('app.color.eta_preference_arrow') : $r('app.color.miuix_disabled_on_surface')).matchTextDirection(true).accessibilityLevel('no')
    }
  }
  build() {
    EtaPreferenceRow({ title: this.title, summary: this.summary, isEnabled: this.isEnabled, holding: this.holding,
      hasLeading: this.hasLeading, hasBottom: this.hasBottom, hasActions: true,
      startAction: this.startAction, bottomAction: this.bottomAction, endActions: this.trailing, onAction: this.onAction })
  }
}
