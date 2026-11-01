// 随身助手原创代码（无障碍扩展实验，D0a）。
// 只记录前台包名与控件数量，不读取、不记录任何文字。
import { AccessibilityExtensionAbility, AccessibilityEvent, AccessibilityElement, GesturePath, GesturePoint } from '@kit.AccessibilityKit';
import { BusinessError } from '@kit.BasicServicesKit';
import { fileIo as fs } from '@kit.CoreFileKit';
import { util } from '@kit.ArkTS';
import {
  AccessibilityProbe,
  emptyAccessibilityProbe,
  normalizeGestureResult,
  parseAccessibilityProbe,
  serializeAccessibilityProbe,
  A11Y_TEST_BUTTON_TEXT,
} from '../core/eta/device/accessibilityProbe';

const PROBE_FILE = 'a11y-probe.json';
const GESTURE_FILE = 'a11y-gesture-request.json';
const MAX_NODES = 500;
const EVENT_INTERVAL_MS = 1000;
const GESTURE_DURATION_MS = 100;
const MAX_FILE_BYTES = 64 * 1024;

interface GestureRequest {
  requestedAt?: number;
}

interface NodeRect {
  left: number;
  top: number;
  width: number;
  height: number;
}

export default class AgentAccessibility extends AccessibilityExtensionAbility {
  private probe: AccessibilityProbe = emptyAccessibilityProbe();
  private lastEventAt = 0;
  private lastWriteAt = 0;

  onConnect(): void {
    this.probe = parseAccessibilityProbe(this.readText(PROBE_FILE));
    this.probe.connected = true;
    this.probe.connectedAt = Date.now();
    this.writeProbe(true);
  }

  onDisconnect(): void {
    this.probe = parseAccessibilityProbe(this.readText(PROBE_FILE));
    this.probe.connected = false;
    this.writeProbe(true);
  }

  onAccessibilityEvent(_event: AccessibilityEvent): void {
    const now = Date.now();
    if (now - this.lastEventAt < EVENT_INTERVAL_MS) return;
    this.lastEventAt = now;
    this.refreshProbeFromWindow(now).catch((error: BusinessError) => {
      this.probe.nodeCount = 0;
      this.probe.gesture.result = normalizeGestureResult('error:' + String(error.code));
      this.probe.gesture.at = Date.now();
      this.writeProbe(true);
    });
  }

  private async refreshProbeFromWindow(now: number): Promise<void> {
    this.probe.lastEventAt = now;
    const root = await this.context.getWindowRootElement();
    const bundle = await root.attributeValue('bundleName');
    if (typeof bundle === 'string' && bundle !== '') this.probe.foregroundBundle = bundle;
    this.probe.nodeCount = await this.countNodes(root, MAX_NODES);
    await this.maybeInjectGesture(root);
    this.writeProbe(false);
  }

  private async countNodes(root: AccessibilityElement, limit: number): Promise<number> {
    let count = 0;
    const stack: AccessibilityElement[] = [root];
    while (stack.length > 0 && count < limit) {
      const node = stack.pop();
      if (node === undefined) break;
      count += 1;
      const children = (await node.attributeValue('children')) as AccessibilityElement[];
      if (children !== undefined && children !== null) {
        for (let index = 0; index < children.length && count + stack.length < limit; index++) {
          stack.push(children[index]);
        }
      }
    }
    return Math.min(count, limit);
  }

  private async maybeInjectGesture(root: AccessibilityElement): Promise<void> {
    const requestText = this.readText(GESTURE_FILE);
    if (requestText.trim() === '') return;
    let request: GestureRequest = {};
    try {
      request = JSON.parse(requestText) as GestureRequest;
    } catch (_) {
      request = {};
    }
    this.probe.gesture.requestedAt = typeof request.requestedAt === 'number' ? request.requestedAt : Date.now();
    if (this.probe.foregroundBundle !== this.context.applicationInfo.name) {
      this.probe.gesture.result = normalizeGestureResult('error:not-foreground');
      this.probe.gesture.at = Date.now();
      this.removeFile(GESTURE_FILE);
      return;
    }
    const matches = (await root.findElement('content', A11Y_TEST_BUTTON_TEXT)) as AccessibilityElement[];
    if (matches === undefined || matches === null || matches.length === 0) {
      this.probe.gesture.result = 'not-found';
      this.probe.gesture.at = Date.now();
      this.removeFile(GESTURE_FILE);
      return;
    }
    const rect = (await matches[0].attributeValue('rect')) as NodeRect;
    if (rect === undefined || rect === null) {
      this.probe.gesture.result = normalizeGestureResult('error:no-rect');
      this.probe.gesture.at = Date.now();
      this.removeFile(GESTURE_FILE);
      return;
    }
    const centerX = Math.floor(rect.left + rect.width / 2);
    const centerY = Math.floor(rect.top + rect.height / 2);
    const path = new GesturePath(GESTURE_DURATION_MS);
    path.points.push(new GesturePoint(centerX, centerY));
    this.context.injectGestureSync(path);
    this.probe.gesture.result = 'ok';
    this.probe.gesture.at = Date.now();
    this.removeFile(GESTURE_FILE);
  }

  private writeProbe(force: boolean): void {
    const now = Date.now();
    if (!force && now - this.lastWriteAt < EVENT_INTERVAL_MS) return;
    this.lastWriteAt = now;
    this.writeText(PROBE_FILE, serializeAccessibilityProbe(this.probe));
  }

  private readText(name: string): string {
    const path = this.context.filesDir + '/' + name;
    try {
      const file = fs.openSync(path, fs.OpenMode.READ_ONLY);
      try {
        const size = fs.statSync(file.fd).size;
        if (size <= 0 || size > MAX_FILE_BYTES) return '';
        const buffer = new ArrayBuffer(size);
        const read = fs.readSync(file.fd, buffer);
        if (read <= 0) return '';
        return util.TextDecoder.create('utf-8').decodeToString(new Uint8Array(buffer, 0, read));
      } finally {
        fs.closeSync(file);
      }
    } catch (_) {
      return '';
    }
  }

  private writeText(name: string, text: string): void {
    const path = this.context.filesDir + '/' + name;
    try {
      const file = fs.openSync(path, fs.OpenMode.CREATE | fs.OpenMode.WRITE_ONLY | fs.OpenMode.TRUNC);
      try {
        fs.writeSync(file.fd, text);
      } finally {
        fs.closeSync(file);
      }
    } catch (_) {
      // 扩展与界面不在同一进程，写失败时下一帧再试。
    }
  }

  private removeFile(name: string): void {
    try {
      fs.unlinkSync(this.context.filesDir + '/' + name);
    } catch (_) {
      // 请求文件可能已被另一个事件处理掉。
    }
  }
}
