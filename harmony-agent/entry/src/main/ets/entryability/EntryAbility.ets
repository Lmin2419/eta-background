import { UIAbility, Want, AbilityConstant, errorManager } from '@kit.AbilityKit';
import { window } from '@kit.ArkUI';
import { FoundationHost } from '../platform/FoundationHost';

export default class EntryAbility extends UIAbility {
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
    this.receive(want);
  }
  onDestroy(): void {
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
    try {
      const main = await stage.getMainWindow();
      // System-owned safe area, exactly once; no competing manual ArkUI inset calculations.
      await main.setWindowLayoutFullScreen(false);
      await main.setWindowSystemBarProperties({ statusBarColor: '#F7F8FC', navigationBarColor: '#F7F8FC', statusBarContentColor: '#273247', navigationBarContentColor: '#273247' });
    } catch (_) {}
    await stage.loadContent('pages/Index');
  }
}
