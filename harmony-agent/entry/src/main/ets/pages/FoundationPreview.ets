import { previewController } from '../core/foundation/previewFixture';
import { ShellState, emptyShellState } from '../core/foundation/controller';
import { AssistantShell } from '../ui/foundation/AssistantShell';

/** DevEco PreviewBuild only; not listed in the production main_pages.json. */
@Entry
@Component
struct FoundationPreview {
  @State private state: ShellState = emptyShellState();
  private controller = previewController();
  private unsubscribe: (() => void) | undefined;
  aboutToAppear(): void {
    AppStorage.setOrCreate('darkTheme', false);
    this.unsubscribe = this.controller.subscribe((): void => { this.state = this.controller.snapshot(); });
    void this.controller.initialize();
  }
  aboutToDisappear(): void { this.unsubscribe?.(); }
  build() { Column() { AssistantShell({ state: this.state, controller: this.controller }) }.width('100%').height('100%') }
}
