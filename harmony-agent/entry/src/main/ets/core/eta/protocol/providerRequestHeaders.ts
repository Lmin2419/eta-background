// Ported from app/src/main/kotlin/io/github/mangi/eta/agent/model/ProviderRequestHeaders.kt,
// CustomHeaderFilter.kt @ 3b1794d. Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { CustomHeader } from './providerTypes';
import { customHeaderError } from '../providerConfigDraft';
const forbidden = new Set(['host', 'content-length', 'connection', 'transfer-encoding', 'content-encoding', 'accept-encoding', 'expect', 'keep-alive', 'proxy-connection', 'upgrade', 'authorization', 'x-api-key', 'x-goog-api-key', 'anthropic-version']);
export function isForbiddenHeader(name: string): boolean { return !name.trim() || forbidden.has(name.trim().toLowerCase()); }
export function validateProviderHeaders(headers: CustomHeader[]): string | null { return customHeaderError(headers.map((h, i) => ({ ...h, id: String(i) }))); }
function set(headers: Record<string, string>, name: string, value: string): void {
  for (const key of Object.keys(headers)) if (key.toLowerCase() === name.toLowerCase()) delete headers[key];
  Object.defineProperty(headers, name, { value, enumerable: true, configurable: true, writable: true });
}
function managed(name: string): boolean {
  const n = name.trim().toLowerCase(); return isForbiddenHeader(n) || ['cookie', 'proxy-authorization', 'api-key', 'user-agent', 'origin', 'referer', 'originator', 'session-id', 'x-authenticateresponse'].includes(n) || ['x-grok-', 'x-xai-', 'openai-', 'x-openai-', 'chatgpt-'].some(prefix => n.startsWith(prefix));
}
/** java.util.UUID.nameUUIDFromBytes: MD5 + v3/variant bits. Routing identity only, not cryptography. */
export function sessionNameUuid(text: string): string {
  const bytes: number[] = [];
  for (const ch of text) {
    let cp = ch.codePointAt(0)!; if (cp >= 0xd800 && cp <= 0xdfff) cp = 0x3f; // Java UTF-8 encoder replacement.
    if (cp < 128) bytes.push(cp); else if (cp < 2048) bytes.push(192 | cp >> 6, 128 | cp & 63);
    else if (cp < 65536) bytes.push(224 | cp >> 12, 128 | cp >> 6 & 63, 128 | cp & 63);
    else bytes.push(240 | cp >> 18, 128 | cp >> 12 & 63, 128 | cp >> 6 & 63, 128 | cp & 63);
  }
  const bits = bytes.length * 8; bytes.push(128); while (bytes.length % 64 !== 56) bytes.push(0);
  for (let i = 0; i < 8; i++) bytes.push(Math.floor(bits / 2 ** (8 * i)) & 255);
  const shifts = [[7, 12, 17, 22], [5, 9, 14, 20], [4, 11, 16, 23], [6, 10, 15, 21]];
  let a0 = 0x67452301, b0 = 0xefcdab89, c0 = 0x98badcfe, d0 = 0x10325476;
  for (let start = 0; start < bytes.length; start += 64) {
    const words = Array.from({ length: 16 }, (_, i) => bytes[start + i * 4] | bytes[start + i * 4 + 1] << 8 | bytes[start + i * 4 + 2] << 16 | bytes[start + i * 4 + 3] << 24);
    let a = a0, b = b0, c = c0, d = d0;
    for (let i = 0; i < 64; i++) {
      const round = Math.floor(i / 16), f = round === 0 ? b & c | ~b & d : round === 1 ? d & b | ~d & c : round === 2 ? b ^ c ^ d : c ^ (b | ~d);
      const g = round === 0 ? i : round === 1 ? (5 * i + 1) % 16 : round === 2 ? (3 * i + 5) % 16 : 7 * i % 16;
      const sum = (a + f + Math.floor(Math.abs(Math.sin(i + 1)) * 2 ** 32) + words[g]) | 0, shift = shifts[round][i % 4];
      a = d; d = c; c = b; b = (b + (sum << shift | sum >>> (32 - shift))) | 0;
    }
    a0 = (a0 + a) | 0; b0 = (b0 + b) | 0; c0 = (c0 + c) | 0; d0 = (d0 + d) | 0;
  }
  const out = [a0, b0, c0, d0].flatMap(w => [w & 255, w >>> 8 & 255, w >>> 16 & 255, w >>> 24 & 255]);
  out[6] = out[6] & 15 | 48; out[8] = out[8] & 63 | 128;
  const hex = out.map(b => b.toString(16).padStart(2, '0')).join(''); return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}
export function mergeProviderHeaders(headers: Record<string, string>, baseUrl: string, custom: CustomHeader[], sessionId: string, authMode = 'api_key', model?: string): void {
  if (authMode !== 'api_key' && custom.some(h => managed(h.name))) throw Error('订阅连接的认证、Cookie 与路由请求头由随身助手管理，不能自定义覆盖');
  // The HTTP product token must be ASCII; app-facing branding remains 随身助手.
  set(headers, 'User-Agent', 'Harmony-Agent');
  for (const h of custom) if (!isForbiddenHeader(h.name)) {
    const error = validateProviderHeaders([h]); if (error) throw Error(error); set(headers, h.name.trim(), h.value);
  }
  if (authMode === 'xai_grok') { set(headers, 'X-XAI-Token-Auth', 'xai-grok-cli'); if (model !== undefined) set(headers, 'x-grok-model-override', model); }
  if (authMode === 'openai_chatgpt') { set(headers, 'originator', 'eta'); set(headers, 'OpenAI-Beta', 'responses=experimental'); set(headers, 'session-id', sessionId); }
  const host = /^https?:\/\/([^/:?#]+)(?::\d+)?(?:\/|$)/i.exec(baseUrl.trim())?.[1].toLowerCase();
  if (host === 'opencode.ai') set(headers, 'x-opencode-session', sessionNameUuid(sessionId));
}
