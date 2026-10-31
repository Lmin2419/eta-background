// Copyright © 2026 蛮吉 (Mangi-11). PolyForm Noncommercial 1.0.0.
// D0a 无障碍扩展实验：只记录前台包名与控件数量，不读取、不记录任何文字。
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
} from '../core/eta/device/accessibilityProbe';

const PROBE_FILE = 'a11y-probe.json';
const GESTURE_FILE = 'a11y-gesture-request.json';
const MAX_NODES = 500;
const EVENT_INTERVAL_MS = 1000;
const GESTURE_DURATION_MS = 100;
const TEST_BUTTON_TEXT = '无障碍注入测试按钮';

interface GestureRequest {
  requestedAt?: number;
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

  onAccessibilityEvent(event: AccessibilityEvent): void {
    const now = Date.now();
    if (now - this.lastEventAt < EVENT_INTERVAL_MS) return;
    this.lastEventAt = now;
    this.probe.lastEventAt = now;
    const bundle = event.bundleName;
    if (typeof bundle === 'string' && bundle !== '') this.probe.foregroundBundle = bundle;
    this.context
      .getWindowRootElement()
      .then((root: AccessibilityElement) => {
        this.probe.nodeCount = this.countNodes(root, MAX_NODES);
        this.maybeInjectGesture();
        this.writeProbe(false);
      })
      .catch((_error: BusinessError) => {
        this.probe.nodeCount = 0;
        this.writeProbe(false);
      });
  }

  private countNodes(root: AccessibilityElement, limit: number): number {
    let count = 0;
    const stack: AccessibilityElement[] = [root];
    while (stack.length > 0 && count < limit) {
      const node = stack.pop();
      if (node === undefined) break;
      count += 1;
      const children = node.children;
      if (children !== undefined && children !== null) {
        for (let index = 0; index < children.length && count + stack.length < limit; index++) {
          stack.push(children[index]);
        }
      }
    }
    return Math.min(count, limit);
  }

  private maybeInjectGesture(): void {
    const requestText = this.readText(GESTURE_FILE);
    if (requestText.trim() === '') return;
    let request: GestureRequest = {};
    try {
      request = JSON.parse(requestText) as GestureRequest;
    } catch (_) {
      request = {};
    }
    const requestedAt = typeof request.requestedAt === 'number' ? request.requestedAt : Date.now();
    this.probe.gesture.requestedAt = requestedAt;
    if (this.probe.foregroundBundle !== this.context.abilityInfo.bundleName) {
      this.probe.gesture.result = normalizeGestureResult('error:not-foreground');
      this.probe.gesture.at = Date.now();
      this.removeFile(GESTURE_FILE);
      return;
    }
    this.context
      .getWindowRootElement()
      .then((root: AccessibilityElement) => {
        return root.findElement('content', TEST_BUTTON_TEXT);
      })
      .then((target: AccessibilityElement) => {
        const rect = target.rect;
        const centerX = Math.floor((rect.left + rect.right) / 2);
        const centerY = Math.floor((rect.top + rect.bottom) / 2);
        const path = new GesturePath(GESTURE_DURATION_MS);
        const point = new GesturePoint(centerX, centerY);
        path.addPoint(point);
        this.context.injectGestureSync(path);
        this.probe.gesture.result = 'ok';
        this.probe.gesture.at = Date.now();
        this.removeFile(GESTURE_FILE);
        this.writeProbe(true);
      })
      .catch((error: BusinessError) => {
        const code = error !== undefined && error.code !== undefined ? String(error.code) : 'unknown';
        this.probe.gesture.result = normalizeGestureResult('error:' + code);
        this.probe.gesture.at = Date.now();
        this.removeFile(GESTURE_FILE);
        this.writeProbe(true);
      });
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
        if (size <= 0 || size > 64 * 1024) return '';
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
      // 扩展与界面不在同一进程，写失败时下一帧再试；这里不抛给系统。
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
