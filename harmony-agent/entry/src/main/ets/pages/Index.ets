import { common, ConfigurationConstant } from '@kit.AbilityKit';
import { KeyboardAvoidMode } from '@kit.ArkUI';
import { FoundationHost } from '../platform/FoundationHost';
import { FoundationController, ShellState, emptyShellState } from '../core/foundation/controller';
import { AssistantShell } from '../ui/foundation/AssistantShell';

/** Subscription lifetime only. Business actions live in the controller. */
@Entry
@Component
struct Index {
  @State private state: ShellState = emptyShellState();
  private controller: FoundationController | undefined;
  private unsubscribe: (() => void) | undefined;
  private timer: number = -1;
  aboutToAppear(): void {
    const context = this.getUIContext().getHostContext() as common.UIAbilityContext;
    this.getUIContext().setKeyboardAvoidMode(KeyboardAvoidMode.RESIZE);
    context.getApplicationContext().setColorMode(ConfigurationConstant.ColorMode.COLOR_MODE_LIGHT);
    AppStorage.setOrCreate('darkTheme', false);
    this.controller = FoundationHost.get(context).controller;
    this.unsubscribe = this.controller.subscribe((): void => {
      if (this.timer >= 0) return;
      this.timer = setTimeout((): void => { this.timer = -1; if (this.controller) this.state = this.controller.snapshot(); }, 32);
    });
    this.state = this.controller.snapshot();
    void this.controller.initialize();
  }
  aboutToDisappear(): void { this.unsubscribe?.(); clearTimeout(this.timer); this.timer = -1; this.controller?.editor.close(); void this.controller?.flush(); }
  onBackPress(): boolean { return this.controller?.back() || false; }
  build() { Column() { AssistantShell({ state: this.state, controller: this.controller }) }.width('100%').height('100%') }
}
