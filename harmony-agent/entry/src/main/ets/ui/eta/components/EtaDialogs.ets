// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/components/EtaControls.kt, MiuixDialogActions.kt, ItemDescriptionDialog.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { EtaControlDefaults as C, EtaComponentSizes as S, MiuixFontSizes as F, MiuixDialogDefaults as D } from '../../../core/eta/tokens';
import { EmptyEtaSlot } from './EtaCard';
import { EtaTextButton } from './EtaControls';

// The host uses CustomDialogController.open/close; its cancel callback is onDismissRequest,
// and onDidDisappear is onDismissFinished. Both Eta window/overlay variants use the same panel.
@Component
export struct EtaDialogContent {
  @Provide('etaPreferenceTheme') preferenceTheme: boolean = true;
  @Prop title: ResourceStr = '';
  @Prop summary: ResourceStr = '';
  @BuilderParam content: () => void = EmptyEtaSlot;
  build() {
    Column() {
      if (this.title !== '') {
        Text(this.title).width('100%').fontSize(F.Title4).fontWeight(FontWeight.Medium).textAlign(TextAlign.Center)
          .fontColor($r('app.color.miuix_on_background')).margin({ bottom: S.Dp12 })
      }
      if (this.summary !== '') {
        Text(this.summary).width('100%').fontSize(F.Body2).textAlign(TextAlign.Center)
          .fontColor($r('app.color.miuix_on_surface_secondary')).margin({ bottom: S.Dp12 })
      }
      this.content()
    }.width('100%').constraintSize({ maxWidth: D.MaxWidth })
      .padding({ left: C.DialogInsideMargin.width, right: C.DialogInsideMargin.width,
        top: C.DialogInsideMargin.height, bottom: C.DialogInsideMargin.height })
      .backgroundColor($r('app.color.miuix_surface_container')).borderRadius(D.CornerRadius).clip(true)
  }
}
@CustomDialog
export struct EtaWindowDialog {
  controller?: CustomDialogController;
  @Prop title: ResourceStr = '';
  @Prop summary: ResourceStr = '';
  @BuilderParam content: () => void = EmptyEtaSlot;
  build() {
    EtaDialogContent({ title: this.title, summary: this.summary, content: this.content })
      .margin({ left: C.DialogOutsideMargin.width, right: C.DialogOutsideMargin.width, bottom: C.DialogOutsideMargin.height })
  }
}
@CustomDialog
export struct EtaOverlayDialog {
  controller?: CustomDialogController;
  @Prop title: ResourceStr = '';
  @Prop summary: ResourceStr = '';
  @BuilderParam content: () => void = EmptyEtaSlot;
  build() {
    EtaDialogContent({ title: this.title, summary: this.summary, content: this.content })
      .margin({ left: C.DialogOutsideMargin.width, right: C.DialogOutsideMargin.width, bottom: C.DialogOutsideMargin.height })
  }
}
@Component
export struct MiuixDialogActions {
  @Prop confirmText: ResourceStr = $r('app.string.action_confirm');
  @Prop cancelText: ResourceStr = $r('app.string.action_cancel');
  @Prop cancelEnabled: boolean = true;
  @Prop confirmEnabled: boolean = true;
  @Prop destructive: boolean = false;
  onCancel: () => void = () => {};
  onConfirm: () => void = () => {};
  build() {
    Row({ space: S.Dp8 }) {
      EtaTextButton({ text: this.cancelText, isEnabled: this.cancelEnabled, onAction: this.onCancel }).layoutWeight(1)
      EtaTextButton({ text: this.confirmText, isEnabled: this.confirmEnabled, destructive: this.destructive,
        primary: true, onAction: this.onConfirm }).layoutWeight(1)
    }.width('100%')
  }
}
@CustomDialog
export struct ItemDescriptionDialog {
  controller?: CustomDialogController;
  @Prop title: ResourceStr = '';
  @Prop description: ResourceStr = '';
  onDismiss: () => void = () => {};
  @Builder private body() {
    Column({ space: S.Dp12 }) {
      Scroll() {
        Text(this.description).width('100%').fontSize(F.Body2).fontColor($r('app.color.miuix_on_surface_variant_summary'))
      }.constraintSize({ maxHeight: S.Dp360 - C.ButtonMinHeight - S.Dp12 }).scrollBar(BarState.Auto).width('100%')
      EtaTextButton({ text: $r('app.string.action_close'), onAction: () => {
        this.controller?.close(); this.onDismiss();
      } }).width('100%')
    }.constraintSize({ maxHeight: S.Dp360 }).width('100%')
  }
  build() {
    EtaDialogContent({ title: this.title, content: this.body })
      .margin({ left: C.DialogOutsideMargin.width, right: C.DialogOutsideMargin.width, bottom: C.DialogOutsideMargin.height })
  }
}
