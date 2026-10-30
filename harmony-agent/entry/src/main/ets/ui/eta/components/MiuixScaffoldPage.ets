// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/components/MiuixScaffoldPage.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { EtaPageDefaults as P, EtaComponentSizes as S } from '../../../core/eta/tokens';
import { EmptyEtaSlot } from './EtaCard';
import { TopBarBackdrop } from './TopBarBackdrop';
import { AdaptiveTopAppBar } from './AdaptiveTopAppBar';
import { MiuixBackButton } from './MiuixBackButton';

@Component
export struct MiuixPageBottomSpacer {
  @Prop bottomInset: number = S.Dp0;
  build() { Blank().height(S.Dp24 + this.bottomInset).width('100%') }
}
@Component
export struct MiuixScaffoldPage {
  @Provide('etaPreferenceTheme') preferenceTheme: boolean = true;
  @Prop title: ResourceStr = '';
  @Prop compactTitle: boolean = false;
  @Prop blurEnabled: boolean = true;
  @Prop progressiveBlur: boolean = false;
  // Insets are supplied once by the window host. Do not add a second safe-area margin.
  @Prop topInset: number = S.Dp0;
  @Prop bottomInset: number = S.Dp0;
  @Prop leftInset: number = S.Dp0;
  @Prop rightInset: number = S.Dp0;
  @BuilderParam actions: () => void = EmptyEtaSlot;
  @BuilderParam content: () => void = EmptyEtaSlot;
  @BuilderParam overlayContent: () => void = EmptyEtaSlot;
  onBack: () => void = () => {};
  private scroller: Scroller = new Scroller();
  @State private widthVp: number = S.Dp0;
  @State private barHeight: number = S.Dp0;
  @State private scrollOffset: number = S.Dp0;
  @Builder private back() { MiuixBackButton({ onAction: this.onBack }) }
  @Builder private topBar() {
    Column() {
      Blank().height(this.topInset)
      AdaptiveTopAppBar({ title: this.title, wide: this.widthVp >= P.WideScreenMinWidth,
        compact: this.compactTitle, scrollOffset: this.scrollOffset, navigationIcon: this.back, actions: this.actions })
    }.width('100%').onAreaChange((_old, area) => { this.barHeight = Number(area.height); })
  }
  build() {
    Stack({ alignContent: Alignment.Top }) {
      Scroll(this.scroller) {
        Column() {
          this.content()
          MiuixPageBottomSpacer({ bottomInset: this.bottomInset })
        }.width('100%').padding({ top: this.barHeight,
          left: Math.max(S.Dp0, (this.widthVp - P.MaxPageContentWidth) / 2) + this.leftInset,
          right: Math.max(S.Dp0, (this.widthVp - P.MaxPageContentWidth) / 2) + this.rightInset })
      }.width('100%').height('100%').scrollBar(BarState.Auto).edgeEffect(EdgeEffect.Spring, { alwaysEnabled: true })
        .onDidScroll(() => { this.scrollOffset = Math.max(S.Dp0, this.scroller.currentOffset().yOffset); })
      TopBarBackdrop({ isEnabled: this.blurEnabled, progressive: this.progressiveBlur, content: this.topBar })
      this.overlayContent()
    }.width('100%').height('100%').backgroundColor($r('app.color.eta_settings_background'))
      .onAreaChange((_old, area) => { this.widthVp = Number(area.width); })
  }
}
