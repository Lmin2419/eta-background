// Ported startup/navigation from app/src/main/kotlin/io/github/mangi/eta/ui/app/StartupSplash.kt, AgentAppRoot.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { UIAbility, Want, AbilityConstant, errorManager, ConfigurationConstant } from '@kit.AbilityKit';
import { window } from '@kit.ArkUI';
import { StartupContentHost } from '../core/eta/startupSplash';
import { FoundationHost } from '../platform/FoundationHost';

export default class EntryAbility extends UIAbility {
  private startup: StartupContentHost = new StartupContentHost();
  private errorObserver: number = -1;
  private receive(want: Want): void {
    if (want.uri === 'harmonyagent://assistant' || want.uri === 'harmonyagent://assistant/') {
      void FoundationHost.get(this.context).route(want).catch(() => {});
    }
  }
  onCreate(want: Want, _launch: AbilityConstant.LaunchParam): void {
    // Unhandled exceptions go to the local diagnostic log so a device report can show them after restart.
    const context = this.context;
    const observer: errorManager.ErrorObserver = {
      onUnhandledException(message: string): void { FoundationHost.get(context).controller.crash(message); }
    };
    try { this.errorObserver = errorManager.on('error', observer); } catch (_) {}
    const restored = want.parameters?.etaNavigation;
    if (typeof restored === 'string') FoundationHost.get(this.context).controller.restoreNavigation(restored);
    this.receive(want);
  }
  onSaveState(_reason: AbilityConstant.StateType, parameters: Record<string, Object>): AbilityConstant.OnSaveResult {
    parameters.etaNavigation = FoundationHost.get(this.context).controller.navigation.eta.saveStack();
    return AbilityConstant.OnSaveResult.ALL_AGREE;
  }
  onDestroy(): void {
    this.startup.destroy();
    if (this.errorObserver >= 0) { try { void errorManager.off('error', this.errorObserver); } catch (_) {} this.errorObserver = -1; }
  }
  onNewWant(want: Want, _launch: AbilityConstant.LaunchParam): void { this.receive(want); }
  onForeground(): void { FoundationHost.get(this.context).controller.foreground(true); }
  onBackground(): void {
    const controller = FoundationHost.get(this.context).controller;
    controller.foreground(false); void controller.flush();
  }
  onWindowStageCreate(stage: window.WindowStage): void { void this.prepare(stage); }
  private async prepare(stage: window.WindowStage): Promise<void> {
    await this.startup.load(async () => {
      // E05 replaces this explicit bootstrap with persisted appearance settings.
      this.context.getApplicationContext().setColorMode(ConfigurationConstant.ColorMode.COLOR_MODE_LIGHT);
      AppStorage.setOrCreate('darkTheme', false);
      try {
        const main = await stage.getMainWindow();
        await main.setWindowLayoutFullScreen(false);
        const resources = this.context.resourceManager;
        const surface = '#' + (resources.getColorSync($r('app.color.miuix_background').id) >>> 0).toString(16).padStart(8, '0');
        const foreground = '#' + (resources.getColorSync($r('app.color.miuix_on_background').id) >>> 0).toString(16).padStart(8, '0');
        await main.setWindowSystemBarProperties({ statusBarColor: surface, navigationBarColor: surface,
          statusBarContentColor: foreground, navigationBarContentColor: foreground });
      } catch (_) {}
    }, async () => { await stage.loadContent('pages/Index'); });
  }
}
