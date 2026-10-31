// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/components/EtaFonts.kt @ 3b1794d (font registration only).
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { previewController } from '../core/foundation/previewFixture';
import { ShellState, emptyShellState } from '../core/foundation/controller';
import { AgentAppShell } from '../ui/eta/navigation/AgentAppShell';
import { registerEtaFonts } from '../platform/EtaFonts';

/** DevEco preview and headless UI tests (tests/ui): the production Eta shell with in-memory data and a fake model. Not in main_pages.json. */
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
  build() { Column() { AgentAppShell({ state: this.state, keyboardVisible: false, controller: this.controller }) }.width('100%').height('100%') }
}
