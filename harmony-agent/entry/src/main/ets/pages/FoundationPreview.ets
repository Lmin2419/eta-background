// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/components/EtaFonts.kt @ 3b1794d (font registration only).
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { previewController } from '../core/foundation/previewFixture';
import { ShellState, emptyShellState } from '../core/foundation/controller';
import { AssistantShell } from '../ui/foundation/AssistantShell';
import { registerEtaFonts } from '../platform/EtaFonts';

/** DevEco PreviewBuild only; not listed in the production main_pages.json. */
@Entry
@Component
struct FoundationPreview {
  @State private state: ShellState = emptyShellState();
  private controller = previewController();
  private unsubscribe: (() => void) | undefined;
  aboutToAppear(): void {
    registerEtaFonts(this.getUIContext());
    AppStorage.setOrCreate('darkTheme', false);
    this.unsubscribe = this.controller.subscribe((): void => { this.state = this.controller.snapshot(); });
    void this.controller.initialize();
  }
  aboutToDisappear(): void { this.unsubscribe?.(); }
  build() { Column() { AssistantShell({ state: this.state, controller: this.controller }) }.width('100%').height('100%') }
}
