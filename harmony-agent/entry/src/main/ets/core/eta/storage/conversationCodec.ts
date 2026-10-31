// Ported from app/src/main/kotlin/io/github/mangi/eta/agent/model/AgentConversationCodec.kt,
// ui/app/AgentConversationStore.kt serialization helpers @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { AgentChatMessageUi, ConversationMessage, SystemNoticeCode, agentMessage, userMessage, thinkingMessage, noticeMessage, toolActivityMessage, conversationMessage } from '../model/conversation';
import { RoleplayMessageState, emptyRoleplayMessages } from '../model/runtimeProjection';
import { MessageRow, messageRow } from './conversationDao';
export const noticeCodes: Record<SystemNoticeCode, string> = { Stopped: 'stopped', EmptyResult: 'empty_result', RuntimeFailed: 'runtime_failed', ModelRetry: 'model_retry', ContextCompaction: 'context_compaction', Interrupted: 'interrupted' };
export function parseEffort(value: string): string {
  const v = value.trim().toLowerCase(); if (v === 'none') return 'off'; if (['x-high', 'extra_high', 'extra-high'].includes(v)) return 'xhigh';
  return ['off', 'default', 'minimal', 'low', 'medium', 'high', 'xhigh', 'max'].includes(v) ? v : 'default';
}
export function strings(raw: string): string[] { try { const v: unknown = JSON.parse(raw); return Array.isArray(v) ? v.filter((x): x is string => typeof x === 'string' && !!x.trim()) : []; } catch (_) { return []; } }
export function encodeRevisions(state: RoleplayMessageState): string {
  return JSON.stringify({ links: Object.fromEntries(state.links), revisions: Object.fromEntries(state.revisions), pendingRewrites: Object.fromEntries(state.pendingRewrites) });
}
export function decodeRevisions(raw: string): RoleplayMessageState {
  if (!raw) return emptyRoleplayMessages();
  const v = JSON.parse(raw); return { links: new Map(Object.entries(v.links || {})), revisions: new Map(Object.entries(v.revisions || {})), pendingRewrites: new Map(Object.entries(v.pendingRewrites || {})) };
}
export function valueEqual(a: unknown, b: unknown): boolean { return stableJson(a) === stableJson(b); }
export function stableJson(value: unknown): string {
  return JSON.stringify(value, (_key, v: unknown) => v instanceof Map ? Object.fromEntries([...v].sort(([a], [b]) => String(a) < String(b) ? -1 : 1)) :
    v !== null && typeof v === 'object' && !Array.isArray(v) ? Object.fromEntries(Object.entries(v).sort(([a], [b]) => a < b ? -1 : 1)) : v);
}
function sanitizeContent(raw: string): string {
  if (!raw.trim()) return '';
  try {
    const v: unknown = JSON.parse(raw);
    const clean = (o: Record<string, unknown>): Record<string, unknown> => { const copy = { ...o }; delete copy.image_url; delete copy.source; if ('text' in copy) copy.text = String(copy.text ?? ''); return copy; };
    if (Array.isArray(v)) {
      let omitted = false; const out: Record<string, unknown>[] = [];
      for (const item of v) {
        if (!item || typeof item !== 'object' || Array.isArray(item)) continue;
        if (['image_url', 'input_image', 'image'].includes(item.type) || 'source' in item) omitted = true; else out.push(clean(item));
      }
      if (omitted) out.push({ type: 'text', text: '[图片观察已在当前回合使用，未写入持久会话]' }); return JSON.stringify(out);
    }
    return v && typeof v === 'object' ? JSON.stringify(clean(v as Record<string, unknown>)) : '';
  } catch (_) { return ''; }
}
export function encodeTranscript(messages: ConversationMessage[]): string { return JSON.stringify(messages.map(m => ({ ...m, contentJson: sanitizeContent(m.contentJson) }))); }
export function decodeTranscript(raw: string): ConversationMessage[] {
  if (!raw.trim()) return []; const v: unknown = JSON.parse(raw); if (!Array.isArray(v)) throw Error('Invalid conversation checkpoint');
  return v.map(m => { if (!m || typeof m.role !== 'string') throw Error('Invalid conversation message'); return { ...conversationMessage(m.role), ...m }; });
}
export function toMessageRow(message: AgentChatMessageUi, id: string, sortIndex: number): MessageRow | null {
  const r = messageRow(message.id, id, sortIndex, message.kind, '');
  switch (message.kind) {
    case 'user': return { ...r, content: message.content, imagesJson: JSON.stringify(message.images), isEdited: message.isEdited };
    case 'assistant': return !message.content.trim() && message.isStreaming ? null : { ...r, content: message.content, renderMarkdown: message.renderMarkdown, ...(message.usage || {}) };
    case 'thinking': return { ...r, content: message.content, elapsedSeconds: message.elapsedSeconds };
    case 'notice': return { ...r, type: 'system_notice', content: noticeCodes[message.code], resultSummary: message.detail, contextTokens: message.contextTokens, renderMarkdown: false };
    case 'tool': return { ...r, content: message.command || '', toolName: message.toolName, toolStatus: message.status, argumentsSummary: message.argumentsSummary, resultSummary: message.resultSummary, imageCount: message.imageCount };
    case 'toolSummary': return { ...r, type: 'tool_summary', toolsJson: JSON.stringify(message.tools) };
    default: return null;
  }
}
export function fromMessageRow(r: MessageRow): AgentChatMessageUi | null {
  switch (r.type) {
    case 'user': return userMessage(r.id, r.content, strings(r.imagesJson), r.isEdited);
    case 'assistant': {
      const usage = { contextTokens: r.contextTokens, inputTokens: r.inputTokens, outputTokens: r.outputTokens, reasoningTokens: r.reasoningTokens, cachedTokens: r.cachedTokens };
      return { ...agentMessage(r.id, r.content), renderMarkdown: r.renderMarkdown ?? true, usage: Object.values(usage).some(v => v !== null) ? usage : null };
    }
    case 'thinking': return { ...thinkingMessage(r.id, r.content), elapsedSeconds: r.elapsedSeconds, collapsed: true };
    case 'system_notice': { const code = (Object.keys(noticeCodes) as SystemNoticeCode[]).find(k => noticeCodes[k] === r.content); return code ? { ...noticeMessage(r.id, code, r.resultSummary), contextTokens: r.contextTokens } : null; }
    case 'tool': return toolActivityMessage(r.id, r.toolName || '', r.toolStatus === 'Success' || r.toolStatus === 'Failed' ? r.toolStatus : 'Unknown', r.argumentsSummary || '', r.content.trim() ? r.content : null, r.resultSummary, r.imageCount);
    case 'tool_summary': return { kind: 'toolSummary', id: r.id, tools: strings(r.toolsJson) };
    default: return null;
  }
}
