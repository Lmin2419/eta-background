// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/components/ConversationSidePaneScaffold.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { curves } from '@kit.ArkUI';
import { inputMethod } from '@kit.IMEKit';
import { EtaPaneDefaults as P, EtaComponentSizes as S } from '../../../core/eta/tokens';
import { conversationPaneMotion } from '../../../core/eta/conversationPaneMotion';
import { EtaHaptic } from '../../../core/eta/hapticFeedback';
import { EtaHaptics } from '../../../platform/EtaHaptics';
import { EmptyEtaSlot } from './EtaCard';

@Component
export struct ConversationSidePaneScaffold {
  @Prop @Watch('settle') isOpen: boolean = false;
  @Prop isCurrent: boolean = true;
  @BuilderParam pane: () => void = EmptyEtaSlot;
  @BuilderParam content: () => void = EmptyEtaSlot;
  onOpenChange: (open: boolean) => void = () => {};
  @State private viewportWidth: number = S.Dp0;
  @State private paneOffset: number = S.Dp0;
  private dragStart: number = S.Dp0;
  private dragging: boolean = false;
  private paneWidth(): number { return Math.min(this.viewportWidth * P.PaneWidthFraction, P.PaneMaxWidth); }
  private revealed(): boolean { return this.paneOffset > P.SettleVisibilityThresholdPx; }
  private settle(): void {
    if (this.dragging) return;
    this.getUIContext().animateTo({ curve: curves.springCurve(0, 1, 2 * P.SettleDampingRatio * Math.sqrt(P.SettleStiffness), P.SettleStiffness) }, () => {
      this.paneOffset = this.isOpen ? this.paneWidth() : S.Dp0;
    });
  }
  private close(): void {
    this.getUIContext().getFocusController().clearFocus();
    try { inputMethod.getController().hideTextInput().catch(() => {}); } catch (_) {}
    EtaHaptics.perform(EtaHaptic.GestureEnd);
    this.onOpenChange(false);
  }
  private finishDrag(cancelled: boolean): void {
    if (!this.dragging) return;
    this.dragging = false;
    if (cancelled) { this.settle(); return; }
    EtaHaptics.perform(EtaHaptic.GestureEnd);
    const open = this.paneOffset > this.paneWidth() * P.SettlePositionThresholdFraction;
    if (open === this.isOpen) this.settle();
    else this.onOpenChange(open);
  }
  build() {
    Stack({ alignContent: Alignment.TopStart }) {
      Column() { this.pane() }.width(this.paneWidth()).height('100%')
        .translate({ x: conversationPaneMotion(this.paneOffset, this.paneWidth()).panelOffset })
        .enabled(this.isCurrent && this.revealed()).focusable(this.isCurrent && this.revealed())
        .accessibilityLevel(this.isCurrent && this.revealed() ? 'auto' : 'no-hide-descendants')
      Stack({ alignContent: Alignment.TopStart }) {
        Column() { this.content() }.width('100%').height('100%')
          .enabled(!this.revealed()).focusable(!this.revealed())
          .accessibilityLevel(this.revealed() ? 'no-hide-descendants' : 'auto')
        if (this.revealed()) {
          Column().width('100%').height('100%').backgroundColor($r('app.color.eta_pane_scrim'))
            .opacity(conversationPaneMotion(this.paneOffset,this.paneWidth()).progress)
            .accessibilityText($r('app.string.action_close')).onClick(() => { this.close(); })
        }
      }.width(this.viewportWidth).height('100%').backgroundColor($r('app.color.miuix_background'))
        .translate({ x: conversationPaneMotion(this.paneOffset, this.paneWidth()).foregroundOffset })
    }.width('100%').height('100%').clip(true).direction(Direction.Ltr)
      .onAreaChange((_old, area) => {
        this.viewportWidth = Number(area.width);
        this.dragging = false;
        this.paneOffset = this.isOpen ? this.paneWidth() : S.Dp0;
      })
      .gesture(PanGesture({ direction: PanDirection.Horizontal })
        .onActionStart(() => { if (this.isCurrent) { this.dragging = true; this.dragStart = this.paneOffset; } })
        .onActionUpdate((event: GestureEvent) => { if (this.dragging) this.paneOffset = Math.min(this.paneWidth(), Math.max(S.Dp0, this.dragStart + event.offsetX)); })
        .onActionEnd(() => { this.finishDrag(false); })
        .onActionCancel(() => { this.finishDrag(true); }), GestureMask.Normal)
  }
}
