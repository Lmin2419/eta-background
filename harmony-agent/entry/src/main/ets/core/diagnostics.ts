import { ConversationStorage } from './conversations';
import { HttpTransport } from './model';

export type DiagnosticArea = 'app' | 'lifecycle' | 'http' | 'chat' | 'storage' | 'ui';
export interface DiagnosticEvent { at: number; area: DiagnosticArea; stage: string; detail: string }
export interface DiagnosticEnvironment {
  appVersion: string; versionCode: string; device: string; os: string; api: string; display: string; fold: string; fontScale: string;
}
export interface DiagnosticFact { label: string; value: string }
interface DiagnosticDocument { version: 1; events: DiagnosticEvent[] }

const AREA_LABELS: Record<DiagnosticArea, string> = { app: '应用', lifecycle: '生命周期', http: '网络', chat: '回复', storage: '存储', ui: '界面' };

export function emptyEnvironment(): DiagnosticEnvironment {
  return { appVersion: '', versionCode: '', device: '', os: '', api: '', display: '', fold: '', fontScale: '' };
}

/** Scheme and host only. Paths and query strings may carry deployment names or keys. */
export function hostOf(url: string): string {
  const match = /^(https?:\/\/)([^/?#\s]+)/i.exec(url);
  return match ? match[2].replace(/^.*@/, '') : '[地址]';
}

/**
 * Everything written to the diagnostic log passes through here first. The report is meant to be
 * pasted into an issue or a chat, so keys, authorization values and URL paths never survive.
 */
export function redact(text: string, secrets: string[] = [], limit = 300): string {
  let out = String(text === undefined || text === null ? '' : text);
  for (const secret of secrets.filter(s => s && s.length >= 4).sort((a, b) => b.length - a.length)) out = out.split(secret).join('[已隐藏]');
  out = out.replace(/(https?:\/\/)([^/?#\s"'<>]+)([^\s"'<>]*)/gi, (_all: string, scheme: string, host: string, rest: string): string =>
    scheme + host.replace(/^.*@/, '') + (rest && rest !== '/' ? '/…' : ''));
  out = out.replace(/\bBearer\s+[^\s"',;]+/gi, 'Bearer [已隐藏]');
  out = out.replace(/\b(sk|rk|pk)-[A-Za-z0-9_\-]{8,}/g, '$1-[已隐藏]');
  out = out.replace(/\bAIza[0-9A-Za-z_\-]{20,}/g, 'AIza[已隐藏]');
  out = out.replace(/((?:api[_-]?key|access[_-]?token|token|secret|password|authorization|x-api-key)["']?\s*[:=]\s*["']?(?:Bearer\s+)?)[^\s"',;}&]+/gi, '$1[已隐藏]');
  out = out.replace(/\r?\n/g, ' ⏎ ').replace(/[\u0000-\u001f\u007f]+/g, ' ').trim();
  return out.length > limit ? out.slice(0, limit) + '…' : out;
}

function validEvent(value: unknown): value is DiagnosticEvent {
  const e = value as DiagnosticEvent;
  return Boolean(e) && typeof e.at === 'number' && Number.isFinite(e.at) && typeof e.area === 'string' && e.area in AREA_LABELS
    && typeof e.stage === 'string' && e.stage.length <= 200 && typeof e.detail === 'string' && e.detail.length <= 2000;
}

/** Bounded event log. Writes are debounced; a broken record never blocks the app. */
export class DiagnosticLog {
  private list: DiagnosticEvent[] = [];
  private timer: ReturnType<typeof setTimeout> | undefined;
  private tail: Promise<void> = Promise.resolve();
  constructor(private storage?: ConversationStorage, private limit = 300, private now: () => number = Date.now, private delay = 1500) {}
  async load(): Promise<void> {
    if (!this.storage) return;
    try {
      const raw = await this.storage.read();
      if (!raw) return;
      const data = JSON.parse(raw) as DiagnosticDocument;
      if (data.version !== 1 || !Array.isArray(data.events)) throw new Error('格式无效');
      // Events recorded while loading stay after the restored ones.
      this.list = data.events.filter(validEvent).concat(this.list).slice(-this.limit);
    } catch (e) {
      this.record('storage', '诊断记录无法读取，已重新开始', e instanceof Error ? e.message : '');
    }
  }
  record(area: DiagnosticArea, stage: string, detail = '', limit = 300): void {
    this.list.push({ at: this.now(), area, stage: redact(stage, [], 60), detail: redact(detail, [], limit) });
    if (this.list.length > this.limit) this.list.splice(0, this.list.length - this.limit);
    this.schedule();
  }
  events(): DiagnosticEvent[] { return this.list.map(e => ({ ...e })); }
  clear(): void { this.list = []; this.schedule(); }
  flush(): Promise<void> {
    if (this.timer !== undefined) { clearTimeout(this.timer); this.timer = undefined; }
    const storage = this.storage;
    if (!storage) return Promise.resolve();
    const document: DiagnosticDocument = { version: 1, events: this.list };
    const raw = JSON.stringify(document);
    const write = this.tail.then(() => storage.write(raw));
    this.tail = write.catch(() => {});
    return write;
  }
  private schedule(): void {
    if (!this.storage || this.timer !== undefined) return;
    this.timer = setTimeout(() => { this.timer = undefined; this.flush().catch(() => {}); }, this.delay);
  }
}

function pad(value: number, size = 2): string { return String(value).padStart(size, '0'); }
export function formatTime(at: number, withDate = false): string {
  const d = new Date(at);
  const time = pad(d.getHours()) + ':' + pad(d.getMinutes()) + ':' + pad(d.getSeconds()) + '.' + pad(d.getMilliseconds(), 3);
  return withDate ? d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()) + ' ' + time : pad(d.getMonth() + 1) + '-' + pad(d.getDate()) + ' ' + time;
}
export function formatEvent(e: DiagnosticEvent): string {
  return formatTime(e.at) + ' [' + AREA_LABELS[e.area] + '] ' + e.stage + (e.detail ? ' · ' + e.detail : '');
}
export function environmentFacts(env: DiagnosticEnvironment): DiagnosticFact[] {
  return [
    { label: '应用版本', value: env.appVersion ? env.appVersion + (env.versionCode ? '（' + env.versionCode + '）' : '') : '' },
    { label: '设备', value: env.device }, { label: '系统', value: env.os }, { label: 'API 版本', value: env.api },
    { label: '屏幕', value: env.display }, { label: '折叠状态', value: env.fold }, { label: '字体缩放', value: env.fontScale }
  ].map(f => ({ label: f.label, value: redact(f.value, [], 120) || '未知' }));
}
export function diagnosticReport(env: DiagnosticEnvironment, events: DiagnosticEvent[], now: number): string {
  return [
    '# 随身助手诊断报告', '',
    '- 生成时间：' + formatTime(now, true),
    ...environmentFacts(env).map(f => '- ' + f.label + '：' + f.value), '',
    '## 最近事件（' + events.length + ' 条，按时间先后）', '',
    '```', ...(events.length ? events.map(formatEvent) : ['（暂无记录）']), '```', '',
    '报告已自动隐去 API Key、授权信息、网址路径和消息内容。'
  ].join('\n');
}

/** Records timing and outcome of each model request. Bodies, headers and replies are never logged. */
export class DiagnosticTransport implements HttpTransport {
  get?: (url: string, headers: Record<string, string>) => Promise<string>;
  stream?: (url: string, headers: Record<string, string>, body: string, onChunk: (chunk: string) => void) => Promise<void>;
  constructor(private inner: HttpTransport, private log: DiagnosticLog, private now: () => number = Date.now) {
    const get = inner.get, stream = inner.stream;
    if (get) this.get = (url, headers) => this.measure('GET', url, () => get.call(inner, url, headers));
    if (stream) this.stream = async (url, headers, body, onChunk) => {
      const started = this.now(); let first = -1, chunks = 0, chars = 0;
      try {
        await stream.call(inner, url, headers, body, (chunk: string) => {
          if (first < 0) first = this.now() - started;
          chunks++; chars += chunk.length; onChunk(chunk);
        });
        this.log.record('http', '流式 ' + hostOf(url), '成功 · 首包 ' + (first < 0 ? '无' : first + 'ms') + ' · 总计 ' + (this.now() - started) + 'ms · ' + chunks + ' 段 · ' + chars + ' 字符');
      } catch (e) {
        this.failed('流式', url, started, e, '已收到 ' + chunks + ' 段');
        throw e;
      }
    };
  }
  post(url: string, headers: Record<string, string>, body: string): Promise<string> {
    return this.measure('POST', url, () => this.inner.post(url, headers, body));
  }
  cancel(): void { this.inner.cancel(); }
  private async measure(method: string, url: string, work: () => Promise<string>): Promise<string> {
    const started = this.now();
    try {
      const result = await work();
      this.log.record('http', method + ' ' + hostOf(url), '成功 · ' + (this.now() - started) + 'ms · ' + result.length + ' 字符');
      return result;
    } catch (e) {
      this.failed(method, url, started, e, '');
      throw e;
    }
  }
  private failed(method: string, url: string, started: number, e: unknown, extra: string): void {
    const message = e instanceof Error ? e.message : String(e);
    const outcome = message === 'CANCELLED' ? '已取消' : '失败 · ' + message;
    this.log.record('http', method + ' ' + hostOf(url), outcome + ' · ' + (this.now() - started) + 'ms' + (extra ? ' · ' + extra : ''));
  }
}
