// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/components/AdaptiveTopAppBar.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
// Layout and title transition also ported from Miuix 0.9.4 TopAppBar.kt @ 39c40f9 (Apache-2.0).
import { curves } from '@kit.ArkUI';
import { MiuixTopBarDefaults as T, EtaComponentSizes as S, MiuixFontSizes as F } from '../../../core/eta/tokens';
import { EmptyEtaSlot } from './EtaCard';

@Component
export struct AdaptiveTopAppBar {
  @Prop title: ResourceStr = '';
  @Prop wide: boolean = false;
  @Prop compact: boolean = false;
  @Prop scrollOffset: number = S.Dp0;
  @BuilderParam navigationIcon: () => void = EmptyEtaSlot;
  @BuilderParam actions: () => void = EmptyEtaSlot;
  @BuilderParam bottomContent: () => void = EmptyEtaSlot;
  @State private expansion: number = F.Title1;
  @State private navigationWidth: number = S.Dp0;
  @State private actionsWidth: number = S.Dp0;
  private fraction(): number { return this.expansion > 0 ? Math.min(1, Math.max(0, this.scrollOffset / this.expansion)) : 0; }
  private smallVisible(): boolean { return this.wide || this.compact || this.fraction() * T.TitleFadeMultiplier >= 1; }
  build() {
    Column() {
      Stack({ alignContent: Alignment.TopStart }) {
        Row() {
          Row() { this.navigationIcon() }.padding({ left: T.NavigationIconPadding })
            .onAreaChange((_old, area) => { this.navigationWidth = Number(area.width); })
          Blank()
          Row() { this.actions() }.padding({ right: T.ActionIconPadding })
            .onAreaChange((_old, area) => { this.actionsWidth = Number(area.width); })
        }.width('100%').height(T.CollapsedHeight)
        Text(this.title).fontSize(F.Title3).fontWeight(FontWeight.Medium).maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis }).fontColor($r('app.color.miuix_on_background'))
          .textAlign(TextAlign.Center).width('100%').height(T.SmallTopAppBarCenterHeight)
          .padding({ left: Math.max(this.navigationWidth, this.actionsWidth) + T.TitlePadding,
            right: Math.max(this.navigationWidth, this.actionsWidth) + T.TitlePadding })
          .opacity(this.smallVisible() ? 1 : 0)
          .translate({ y: this.smallVisible() ? S.Dp0 : T.SmallTitleTranslation })
          .animation({ curve: curves.springMotion(this.smallVisible() ? T.TitleShowResponse : T.TitleHideResponse, T.TitleDamping) })
          .hitTestBehavior(HitTestMode.None)
        if (!this.wide && !this.compact) {
          Text(this.title).fontSize(F.Title1).fontWeight(FontWeight.Normal)
            .fontColor($r('app.color.miuix_on_background')).width('100%')
            .padding({ left: T.TitlePadding, right: T.TitlePadding })
            .opacity(1 - Math.min(1, this.fraction() * T.TitleFadeMultiplier))
            .translate({ y: T.CollapsedHeight - this.expansion * this.fraction() })
            .onAreaChange((_old, area) => { this.expansion = Number(area.height); })
            .hitTestBehavior(HitTestMode.None)
        }
      }.width('100%').height(T.CollapsedHeight + ((this.wide || this.compact) ? S.Dp0 : this.expansion * (1 - this.fraction())) + T.LargeTitleBottomPadding)
        .clip(true)
      this.bottomContent()
    }.width('100%')
  }
}
