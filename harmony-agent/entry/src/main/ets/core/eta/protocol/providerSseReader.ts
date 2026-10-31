// Ported from app/src/main/kotlin/io/github/mangi/eta/agent/model/ProviderSseReader.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { ProviderRunControl } from './providerTypes';
export interface ProviderTextStream { read(): Promise<string | null>; close(): Promise<void>; }
/** Incremental equivalent of BufferedReader.readLine, including CR/LF split across network chunks. */
export class ProviderSseReader {
  private buffer = ''; private event = ''; private lines: string[] = []; private skipLf = false; private stopped = false;
  constructor(private control: ProviderRunControl, private onEvent: (event: string, data: string) => boolean) {}
  private dispatch(): boolean {
    const name = this.event, data = this.lines.join('\n'); this.event = ''; this.lines = [];
    if (!data.trim()) return true; this.control.check(); if (!this.onEvent(name, data)) this.stopped = true; return !this.stopped;
  }
  private line(line: string): boolean {
    this.control.check();
    if (!line) return this.dispatch();
    if (line.startsWith('event:')) this.event = line.slice(6).trim();
    else if (line.startsWith('data:')) this.lines.push(line.slice(5).replace(/^ /, ''));
    return true;
  }
  feed(chunk: string): boolean {
    if (this.stopped) return false; this.control.check();
    if (this.skipLf && chunk.length) { this.skipLf = false; if (chunk[0] === '\n') chunk = chunk.slice(1); }
    this.buffer += chunk; let start = 0;
    for (let i = 0; i < this.buffer.length; i++) {
      const c = this.buffer[i]; if (c !== '\r' && c !== '\n') continue;
      if (!this.line(this.buffer.slice(start, i))) { this.buffer = ''; return false; }
      if (c === '\r') { if (this.buffer[i + 1] === '\n') i++; else if (i === this.buffer.length - 1) this.skipLf = true; }
      start = i + 1;
    }
    this.buffer = this.buffer.slice(start); return true;
  }
  finish(): void { if (this.stopped) return; this.control.check(); if (this.buffer) this.line(this.buffer); this.buffer = ''; if (!this.stopped) this.dispatch(); }
}
export async function readProviderSse(stream: ProviderTextStream, control: ProviderRunControl, onEvent: (event: string, data: string) => boolean): Promise<void> {
  const reader = new ProviderSseReader(control, onEvent);
  try { for (;;) { control.check(); const chunk = await stream.read(); if (chunk === null) { reader.finish(); return; } if (!reader.feed(chunk)) return; } }
  finally { await stream.close(); }
}
