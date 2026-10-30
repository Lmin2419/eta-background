import { fileIo as fs } from '@kit.CoreFileKit';
import { common } from '@kit.AbilityKit';
import { display } from '@kit.ArkUI';
import nativeHdc from 'libagent_hdc.so';
import { HdcTransport, ScreenSize } from '../core/device';

export class NativeHdcTransport implements HdcTransport {
  async start(context: common.UIAbilityContext): Promise<void> {
    const path = context.filesDir + '/hdc';
    if (!fs.accessSync(path)) fs.mkdirSync(path, true);
    await nativeHdc.start(path);
  }
  async command(command: string, target: string, timeoutMs: number): Promise<string> {
    return nativeHdc.command(command, target, timeoutMs);
  }
  cancel(): void { nativeHdc.cancel(); }
}

export { HarmonyHttpTransport } from './ModelHttp';

export function screenSize(): ScreenSize {
  const screen = display.getDefaultDisplaySync();
  return { width: screen.width, height: screen.height, displayId:screen.id, rotation:screen.rotation };
}
