// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/components/EtaFonts.kt @ 3b1794d (font registration only).
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { common } from '@kit.AbilityKit';
import { KeyboardAvoidMode, window } from '@kit.ArkUI';
import { FoundationHost } from '../platform/FoundationHost';
import { FoundationController, ShellState, emptyShellState } from '../core/foundation/controller';
import { AgentAppShell } from '../ui/eta/navigation/AgentAppShell';
import { registerEtaFonts } from '../platform/EtaFonts';

/** Subscription lifetime only. Business actions live in the controller. */
@Entry
@Component
struct Index {
  @State private keyboardVisible: boolean = false;
  private keyboardWindow: window.Window | undefined;
  private attached: boolean = false;
  private keyboardChanged: (height:number)=>void = (height:number):void => { this.keyboardVisible=height>0; };
  @State private state: ShellState = emptyShellState();
  private controller: FoundationController | undefined;
  private unsubscribe: (() => void) | undefined;
  private timer: number = -1;
  aboutToAppear(): void {
    this.attached=true;
    registerEtaFonts(this.getUIContext());
    const context = this.getUIContext().getHostContext() as common.UIAbilityContext;
    window.getLastWindow(context).then((main:window.Window)=>{if(this.attached){this.keyboardWindow=main;main.on('keyboardHeightChange',this.keyboardChanged);}}).catch(()=>{});
    this.getUIContext().setKeyboardAvoidMode(KeyboardAvoidMode.RESIZE);
    this.controller = FoundationHost.get(context).controller;
    this.unsubscribe = this.controller.subscribe((): void => {
      if (this.timer >= 0) return;
      this.timer = setTimeout((): void => { this.timer = -1; if (this.controller) this.state = this.controller.snapshot(); }, 32);
    });
    this.state = this.controller.snapshot();
    void this.controller.initialize();
  }
  aboutToDisappear(): void { this.attached=false;this.keyboardWindow?.off('keyboardHeightChange',this.keyboardChanged);this.keyboardWindow=undefined; this.unsubscribe?.(); clearTimeout(this.timer); this.timer = -1; this.controller?.editor.close(); void this.controller?.flush(); }
  onBackPress(): boolean { return this.controller?.back() || false; }
  build() { Column() { AgentAppShell({ state: this.state, keyboardVisible: this.keyboardVisible, controller: this.controller }) }.width('100%').height('100%') }
}
