// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/app/AgentAppShell.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { AppRouteName } from '../../../core/eta/routes';
import { EtaComponentSizes as S, MiuixIconButtonDefaults as I } from '../../../core/eta/tokens';
import { AdaptiveTopAppBar } from '../components/AdaptiveTopAppBar';
import { MiuixBackButton } from '../components/MiuixBackButton';
import { TopBarBackdrop } from '../components/TopBarBackdrop';
import { etaRouteTitle } from './RouteTitles';

@Component
export struct AgentTopBar {
  @Prop route: AppRouteName = AppRouteName.Home;
  @Prop isWide: boolean = false;
  @Prop diagnostics: boolean = false;
  @Prop isEnabled: boolean = true;
  onBack: () => void = () => {};
  onOpenPane: () => void = () => {};
  onNewConversation: () => void = () => {};
  @State private menuOpen: boolean = false;
  @Builder private navigationIcon() {
    if (this.route === AppRouteName.Home && !this.diagnostics) {
      Button({ type: ButtonType.Normal }) {
        Image($r('app.media.ic_outlined_short_text')).width(S.Dp24).height(S.Dp24)
          .fillColor($r('app.color.miuix_on_background')).matchTextDirection(true)
      }.width(I.MinWidth).height(I.MinHeight).padding(S.Dp0).borderRadius(I.CornerRadius)
        .backgroundColor($r('app.color.eta_transparent')).accessibilityText($r('app.string.action_conversation_history'))
        .enabled(this.isEnabled).onClick(this.onOpenPane)
    } else { MiuixBackButton({ onAction: this.onBack }) }
  }
  @Builder private menu() {
    Menu() {
      MenuItem({ content: $r('app.string.action_new_conversation'), startIcon: $r('app.media.ic_rounded_add_comment') })
        .enabled(this.isEnabled).onClick(() => { this.menuOpen = false; this.onNewConversation(); })
      // Terminal / Kimi Web require the C-class Linux environment and are hidden.
      MenuItem({ content: $r('app.string.action_open_browser'), labelInfo: $r('app.string.eta_port_unavailable'), startIcon: $r('app.media.ic_rounded_language') }).enabled(false)
      // Auto-read only exists when a speech provider is configured; speech is not connected yet.
    }
  }
  @Builder private actions() {
    if (this.route === AppRouteName.Home && !this.diagnostics) {
      Button({ type: ButtonType.Normal }) {
        Image($r('app.media.ic_outlined_more_horiz')).width(S.Dp24).height(S.Dp24).fillColor($r('app.color.miuix_on_background'))
      }.width(I.MinWidth).height(I.MinHeight).padding(S.Dp0).borderRadius(I.CornerRadius)
        .backgroundColor($r('app.color.eta_transparent')).accessibilityText($r('app.string.action_more'))
        .enabled(this.isEnabled).onClick(() => { this.menuOpen = true; })
        .bindMenu(this.menuOpen, this.menu, { onDisappear: () => { this.menuOpen = false; } })
    }
  }
  @Builder private bar() {
    AdaptiveTopAppBar({ title: this.diagnostics ? $r('app.string.background_diagnostics_title') : etaRouteTitle(this.route),
      wide: this.isWide, compact: this.route === AppRouteName.Home && !this.diagnostics, navigationIcon: () => { this.navigationIcon(); }, actions: () => { this.actions(); } })
  }
  build() { TopBarBackdrop({ content: () => { this.bar(); } }) }
}
