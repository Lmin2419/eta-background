// Copyright © 2026 蛮吉 (Mangi-11). PolyForm Noncommercial 1.0.0.
// D0a：界面进程读写扩展进程留下的探针文件（同一 filesDir，两个小 JSON）。
import { fileIo as fs } from '@kit.CoreFileKit';
import { util } from '@kit.ArkTS';
import { AccessibilityProbe, parseAccessibilityProbe } from '../core/eta/device/accessibilityProbe';

const PROBE_FILE = 'a11y-probe.json';
const GESTURE_FILE = 'a11y-gesture-request.json';

export function readAccessibilityProbe(filesDir: string): AccessibilityProbe {
  return parseAccessibilityProbe(readText(filesDir, PROBE_FILE));
}

export function requestAccessibilityGesture(filesDir: string): boolean {
  return writeText(filesDir, GESTURE_FILE, JSON.stringify({ requestedAt: Date.now() }));
}

function readText(filesDir: string, name: string): string {
  if (filesDir === '') return '';
  try {
    const file = fs.openSync(filesDir + '/' + name, fs.OpenMode.READ_ONLY);
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

function writeText(filesDir: string, name: string, text: string): boolean {
  if (filesDir === '') return false;
  try {
    const file = fs.openSync(filesDir + '/' + name, fs.OpenMode.CREATE | fs.OpenMode.WRITE_ONLY | fs.OpenMode.TRUNC);
    try {
      fs.writeSync(file.fd, text);
    } finally {
      fs.closeSync(file);
    }
    return true;
  } catch (_) {
    return false;
  }
}
