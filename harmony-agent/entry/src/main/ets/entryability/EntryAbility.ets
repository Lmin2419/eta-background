import { UIAbility, Want, AbilityConstant } from '@kit.AbilityKit';
import { window } from '@kit.ArkUI';
import { FoundationHost } from '../platform/FoundationHost';

export default class EntryAbility extends UIAbility {
  private receive(want: Want): void {
    if (want.uri === 'harmonyagent://assistant' || want.uri === 'harmonyagent://assistant/') {
      void FoundationHost.get(this.context).route(want).catch(() => {});
    }
  }
  onCreate(want: Want, _launch: AbilityConstant.LaunchParam): void { this.receive(want); }
  onNewWant(want: Want, _launch: AbilityConstant.LaunchParam): void { this.receive(want); }
  onForeground(): void { FoundationHost.get(this.context).controller.runtime.setForeground(true); }
  onBackground(): void {
    const controller = FoundationHost.get(this.context).controller;
    controller.runtime.setForeground(false); void controller.flush();
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
