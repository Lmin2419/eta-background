// Ported from app/src/main/kotlin/io/github/mangi/eta/data/db/ChunkedTextDao.kt,
// ChunkedTextInputStream.kt and data/repository/ConversationSearchRepository.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { ConversationDao } from './conversationDao';
export const CHUNK_CHARS = 16_384;
export const REFERENCE_PREFIX = '@eta:chunks:v1:';
export async function storeText(dao: ConversationDao, table: string, owner: string, field: string, text: string): Promise<string> {
  await dao.deleteTextChunks(table, owner, field);
  if (text.length <= CHUNK_CHARS && !text.startsWith(REFERENCE_PREFIX)) return text;
  let count = 0;
  for (let offset = 0; offset < text.length;) {
    let end = Math.min(offset + CHUNK_CHARS, text.length);
    const code = text.charCodeAt(end - 1);
    if (end < text.length && code >= 0xd800 && code <= 0xdbff) end--;
    await dao.putTextChunk({ ownerTable: table, ownerId: owner, field, chunkIndex: count++, content: text.slice(offset, end) });
    offset = end;
  }
  return `${REFERENCE_PREFIX}${count}:${text.length}`;
}
/** Constant-sized source buffer; callers may retain only their own required result. */
export class TextReader {
  private count = 0; private length = 0; private offset = 0; private readLength = 0; private direct = false;
  constructor(private dao: ConversationDao, private table: string, private owner: string, private field: string, private stored: string) {
    this.direct = !stored.startsWith(REFERENCE_PREFIX);
    if (!this.direct) {
      const match = /^@eta:chunks:v1:(\d+):(\d+)$/.exec(stored);
      if (!match || !Number.isSafeInteger(this.count = Number(match[1])) || !Number.isSafeInteger(this.length = Number(match[2])) || this.count <= 0 || this.length <= 0) throw Error('Invalid history payload reference');
    }
  }
  async next(): Promise<string | null> {
    if (this.direct) { if (this.offset++) return null; return this.stored; }
    if (this.offset === this.count) {
      if (this.readLength !== this.length) throw Error('History payload length mismatch');
      return null;
    }
    const page = await this.dao.textChunks(this.table, this.owner, this.field, 1, this.offset);
    if (page.length !== 1 || page[0].chunkIndex !== this.offset++) throw Error('History payload chunk missing or out of order');
    this.readLength += page[0].content.length;
    if (this.readLength > this.length) throw Error('History payload length mismatch');
    return page[0].content;
  }
}
export async function restoreText(dao: ConversationDao, table: string, owner: string, field: string, stored: string): Promise<string> {
  const reader = new TextReader(dao, table, owner, field, stored), parts: string[] = [];
  for (let part = await reader.next(); part !== null; part = await reader.next()) parts.push(part);
  return parts.join('');
}
// Kotlin Char.equals(ignoreCase): upper then lower; U+0130's lowercase expands in JS.
function fold(text: string): string {
  let out = '';
  for (const c of text) { const up = c.toUpperCase(); const lower = (up.length === c.length ? up : c).toLowerCase(); out += lower.length === c.length ? lower : c === 'İ' ? 'i' : c; }
  return out;
}
export function containsText(text: string, query: string): boolean { return fold(text).includes(fold(query)); }
export async function streamContains(reader: TextReader, query: string, checkCancelled: () => void): Promise<boolean> {
  let overlap = '';
  for (let part = await reader.next(); part !== null; part = await reader.next()) {
    checkCancelled(); const candidate = overlap + part;
    if (containsText(candidate, query)) return true;
    overlap = query.length > 1 ? candidate.slice(-(query.length - 1)) : '';
  }
  return false;
}
