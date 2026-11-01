// 随身助手原创代码（D0b3）：把原生 HDC 客户端包装成 D0b1 的 HdcTransport。
// 原生库加载失败时绝不抛出：所有调用前检查模块与方法是否存在。
import { common } from '@kit.AbilityKit';
import nativeHdc from 'libagent_hdc.so';
import { HdcConnectionSnapshot, HdcConnectOutcome, HdcTransport, HdcTransportEvent, validateHdcTarget } from '../core/eta/device/hdcConnection';

interface NativeHdcModule {
  start(path: string): Promise<void>;
  command(command: string, target: string, timeoutMs: number): Promise<string>;
  cancel(): void;
}

const nativeModule = nativeHdc as NativeHdcModule | undefined;
let startPromise: Promise<void> | undefined = undefined;

export function nativeHdcAvailable(): boolean {
  return (
    nativeModule !== undefined &&
    nativeModule !== null &&
    typeof nativeModule.start === 'function' &&
    typeof nativeModule.command === 'function' &&
    typeof nativeModule.cancel === 'function'
  );
}

function requireNative(): NativeHdcModule {
  if (nativeModule === undefined) throw new Error('native module unavailable');
  return nativeModule;
}

const CONNECT_TIMEOUT_MS = 20000;
const SHELL_TIMEOUT_MS = 15000;

/** 连接结果分类：HDC 的错误是以文本返回的，不抛异常。 */
export function classifyHdcFailure(output: string): HdcConnectOutcome {
  const line = (output.split('\n')[0] ?? '').trim().slice(0, 120);
  const lower = line.toLowerCase();
  console.error('[phoneOps] hdc status: ' + line);
  if (lower.includes('unauthorized')) return 'authorizing';
  if (lower.includes('refused') || lower.includes('connect failed') || lower.includes('not found')) return 'refused';
  if (lower.includes('timeout')) return 'timeout';
  return 'unknown';
}

export class HdcConnectionTransport implements HdcTransport {
  readonly nativeAvailable = true;
  identity = '';
  uitestReady = false;
  target = '';
  private listener: ((event: HdcTransportEvent) => void) | null = null;
  private shellSequence = 0;

  private readonly context: common.UIAbilityContext;
  constructor(context: common.UIAbilityContext) { this.context = context; }

  async connect(target: string, attempt: number): Promise<HdcConnectOutcome> {
    if (!nativeHdcAvailable()) {
      console.error('[phoneOps] native module unavailable');
      return 'protocol';
    }
    try {
      await this.ensureStarted();
      const valid = validateHdcTarget(target);
      if (valid === null) return 'refused';
      const connected = await requireNative().command('tconn ' + valid, '', CONNECT_TIMEOUT_MS);
      const first = (connected.split('\n')[0] ?? '').toLowerCase();
      if (!first.includes('connected')) {
        return classifyHdcFailure(connected);
      }
      this.target = valid;
      this.identity = await this.shell('id');
      if (!/uid=\d+/.test(this.identity)) return classifyHdcFailure(this.identity);
      const help = await this.shell('uitest help');
      this.uitestReady = help.includes('dumpLayout') && help.includes('uiInput');
      if (!this.uitestReady) return 'protocol';
      this.emit({ attempt: attempt, kind: 'connected' });
      return 'connected';
    } catch (error) {
      console.error('[phoneOps] hdc error: ' + String(error));
      return 'unknown';
    }
  }

  async close(): Promise<void> {
    this.target = '';
    if (!nativeHdcAvailable()) return;
    try {
      requireNative().cancel();
    } catch (error) {
      console.error('[phoneOps] close error: ' + String(error));
    }
  }

  onEvent(listener: (event: HdcTransportEvent) => void): () => void {
    this.listener = listener;
    return () => { this.listener = null; };
  }

  snapshotProbe(): HdcConnectionSnapshot {
    return { state: 'connected', reason: null, attempt: 0, target: this.target };
  }

  /** 与 HarmonyDevice.shell 相同的写法：sh -c 包一层并带退出码标记。 */
  private async shell(command: string, timeoutMs: number = SHELL_TIMEOUT_MS): Promise<string> {
    if (this.target === '') throw new Error('not connected');
    const marker = '__HARMONY_AGENT_EXIT_' + String(++this.shellSequence) + '__';
    const wrapped = '(\n' + command + '\n)\nagent_rc=$?\nprintf "\\n' + marker + '%s\\n" "$agent_rc"';
    const output = await requireNative().command('shell sh -c ' + quote(wrapped), this.target, timeoutMs);
    const at = output.lastIndexOf(marker);
    if (at < 0) return output;
    const code = Number(output.slice(at + marker.length).trim());
    if (code !== 0) throw new Error('shell exit ' + String(code));
    return output.slice(0, at);
  }

  /** 只读取控件数量：dumpLayout 写到设备文件，再 cat 回来，原文不落到界面。 */
  async dumpLayout(): Promise<string> {
    const path = '/data/local/tmp/agent_phone_ops_layout.json';
    return this.shell('uitest dumpLayout -p ' + path + ' >/dev/null && cat ' + path + '; rm -f ' + path, 20000);
  }

  private ensureStarted(): Promise<void> {
    if (startPromise === undefined) {
      startPromise = requireNative().start(this.context.filesDir + '/hdc').catch((error: Object) => {
        startPromise = undefined;
        console.error('[phoneOps] start error: ' + String(error));
        throw error as Error;
      });
    }
    return startPromise;
  }

  private emit(event: HdcTransportEvent): void { this.listener?.(event); }
}

function quote(value: string): string {
  return "'" + value.replace(/'/g, "'\\''") + "'";
}
