// 随身助手原创代码（D0b3）：把原生 HDC 客户端包装成 D0b1 的 HdcTransport。
// 原生库加载失败时绝不抛出：所有调用前检查模块与方法是否存在。
import { common } from '@kit.AbilityKit';
import nativeHdc from 'libagent_hdc.so';
import { HdcConnectOutcome, HdcTransport, HdcTransportEvent } from '../core/eta/device/hdcConnection';

interface NativeHdcModule {
  start(path: string): Promise<void>;
  command(command: string, target: string, timeoutMs: number): Promise<string>;
  cancel(): void;
}

const nativeModule = nativeHdc as NativeHdcModule | undefined;

export function nativeHdcAvailable(): boolean {
  return (
    nativeModule !== undefined &&
    nativeModule !== null &&
    typeof nativeModule.start === 'function' &&
    typeof nativeModule.command === 'function' &&
    typeof nativeModule.cancel === 'function'
  );
}

const CONNECT_TIMEOUT_MS = 20000;
const SHELL_TIMEOUT_MS = 15000;

export class HdcConnectionTransport implements HdcTransport {
  identity = '';
  uitestReady = false;
  private listener: ((event: HdcTransportEvent) => void) | null = null;

  constructor(private readonly context: common.UIAbilityContext) {}

  async connect(target: string, attempt: number): Promise<HdcConnectOutcome> {
    if (!nativeHdcAvailable()) {
      this.diagnose('native module unavailable');
      return 'protocol';
    }
    try {
      await nativeModule.start(this.context.filesDir + '/hdc');
      await nativeModule.command('tconn ' + target, '', CONNECT_TIMEOUT_MS);
      this.identity = await nativeModule.command('shell id', target, SHELL_TIMEOUT_MS);
      if (!/uid=\d+/.test(this.identity)) {
        this.emit({ attempt: attempt, kind: 'authorizing' });
        return 'authorizing';
      }
      const help = await nativeModule.command('shell uitest help', target, SHELL_TIMEOUT_MS);
      this.uitestReady = help.includes('dumpLayout') && help.includes('uiInput');
      this.emit({ attempt: attempt, kind: 'connected' });
      return 'connected';
    } catch (error) {
      this.diagnose(error);
      return 'unknown';
    }
  }

  async close(): Promise<void> {
    if (!nativeHdcAvailable()) return;
    try {
      nativeModule.cancel();
    } catch (error) {
      this.diagnose(error);
    }
  }

  onEvent(listener: (event: HdcTransportEvent) => void): () => void {
    this.listener = listener;
    return () => {
      this.listener = null;
    };
  }

  /** 只读取控件数量：调用 uitest dumpLayout，原文不落到界面。 */
  async dumpLayout(): Promise<string> {
    if (!nativeHdcAvailable()) throw new Error('native module unavailable');
    return nativeModule.command('shell uitest dumpLayout', '', SHELL_TIMEOUT_MS);
  }

  private emit(event: HdcTransportEvent): void {
    this.listener?.(event);
  }

  /** 原生库报错原文只写进诊断日志，不显示给用户。 */
  private diagnose(error: Object): void {
    console.error('[phoneOps] hdc error: ' + String(error));
  }
}
