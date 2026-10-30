// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/components/EtaCard.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { curves } from '@kit.ArkUI';
import { EtaCardDefaults, EtaComponentSizes as S, EtaComponentBehavior as B } from '../../../core/eta/tokens';

@Builder
export function EmptyEtaSlot() {}

@Component
export struct EtaCard {
  @BuilderParam content: () => void = EmptyEtaSlot;
  @Prop isEnabled: boolean = true;
  @Prop clickable: boolean = false;
  @Prop holding: boolean = false;
  @Prop sinkFeedback: boolean = false;
  @Prop showIndication: boolean = false;
  @State private pressed: boolean = false;
  @Prop insidePadding: number = S.Dp0;
  @Prop color: ResourceColor = $r('app.color.miuix_surface_container');
  onAction: () => void = () => {};
  onLongClick: () => void = () => {};

  @Builder private pressOverlay() {
    if (this.holding || (this.showIndication && this.pressed)) {
      Rect().fill($r('app.color.eta_preference_held')).width('100%').height('100%').hitTestBehavior(HitTestMode.None)
    }
  }
  build() {
    Column() { this.content() }
      .width('100%').padding(this.insidePadding).backgroundColor(this.color)
      .overlay(this.pressOverlay, { align: Alignment.Center })
      .borderRadius(EtaCardDefaults.CornerRadius).clip(true)
      .scale({ x: this.sinkFeedback && this.pressed ? B.SinkScale : 1, y: this.sinkFeedback && this.pressed ? B.SinkScale : 1 })
      .animation({ curve: curves.springCurve(0, 1, B.SinkStiffness, 2 * B.SinkDamping * Math.sqrt(B.SinkStiffness)) })
      .onTouch((event: TouchEvent) => { this.pressed = this.isEnabled && this.clickable && event.type === TouchType.Down; })
      .onClick(() => { if (this.isEnabled && this.clickable) { this.onAction(); } })
      .gesture(LongPressGesture().onAction(() => {
        if (this.isEnabled && this.clickable) this.onLongClick();
      }))
  }
}
