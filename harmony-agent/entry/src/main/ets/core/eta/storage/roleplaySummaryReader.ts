// Ported from AgentConversationStore.kt loadCharacterSummary (Android JsonReader) @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { TextReader } from './chunkedText';
/** Streaming JSON scanner: skip the card/user description without allocating their strings. */
class JsonCursor {
  private buffer = ''; private at = 0; private ended = false;
  constructor(private source: TextReader) {}
  async peek(): Promise<string> {
    while (this.at === this.buffer.length && !this.ended) {
      const next = await this.source.next(); this.ended = next === null; this.buffer = next ?? ''; this.at = 0;
    }
    return this.buffer[this.at] || '';
  }
  async take(): Promise<string> { const c = await this.peek(); if (c) this.at++; return c; }
  async space(): Promise<void> { while (' \t\r\n'.includes(await this.peek()) && await this.peek()) await this.take(); }
  async expect(c: string): Promise<void> { await this.space(); if (await this.take() !== c) throw Error('Invalid roleplay payload'); }
  async string(retain: boolean): Promise<string> {
    await this.expect('"'); let raw = '"';
    while (true) {
      const c = await this.take(); if (!c || c.charCodeAt(0) < 32) throw Error('Invalid roleplay string');
      if (retain) raw += c;
      if (c === '"') return retain ? JSON.parse(raw) as string : '';
      if (c === '\\') {
        const e = await this.take(); if (!'"\\/bfnrtu'.includes(e) || !e) throw Error('Invalid JSON escape');
        if (retain) raw += e;
        if (e === 'u') for (let i = 0; i < 4; i++) { const h = await this.take(); if (!/^[a-fA-F0-9]$/.test(h)) throw Error('Invalid Unicode escape'); if (retain) raw += h; }
      }
    }
  }
  async skip(depth = 0): Promise<void> {
    if (depth > 256) throw Error('Roleplay JSON nesting limit');
    await this.space(); const c = await this.peek();
    if (c === '"') { await this.string(false); return; }
    if (c === '{' || c === '[') {
      await this.take(); await this.space(); const close = c === '{' ? '}' : ']';
      if (await this.peek() === close) { await this.take(); return; }
      while (true) {
        if (c === '{') { await this.string(false); await this.expect(':'); }
        await this.skip(depth + 1); await this.space(); const sep = await this.take();
        if (sep === close) return; if (sep !== ',') throw Error('Invalid roleplay JSON separator');
      }
    }
    let token = '';
    while (await this.peek() && !',]} \t\r\n'.includes(await this.peek())) { token += await this.take(); if (token.length > 128) throw Error('Invalid JSON literal'); }
    if (!/^(null|true|false|-?(0|[1-9]\d*)(\.\d+)?([eE][+-]?\d+)?)$/.test(token)) throw Error('Invalid roleplay JSON literal');
  }
}
export async function readRoleplaySummary(reader: TextReader): Promise<{ name: string | null; avatarPath: string | null }> {
  const c = new JsonCursor(reader), result: { name: string | null; avatarPath: string | null } = { name: null, avatarPath: null };
  await c.expect('{'); await c.space();
  if (await c.peek() !== '}') while (true) {
    const key = await c.string(true); await c.expect(':'); await c.space();
    if (key === 'characterName' || key === 'name' || key === 'avatarPath') {
      let value: string | null = null;
      if (await c.peek() === '"') value = await c.string(true); else await c.skip();
      if (key === 'avatarPath') result.avatarPath = value; else result.name = value;
    } else await c.skip();
    await c.space(); const sep = await c.peek(); if (sep === '}') break; await c.expect(',');
  }
  await c.expect('}'); await c.space(); if (await c.peek()) throw Error('Invalid roleplay payload trailing data');
  return result;
}
