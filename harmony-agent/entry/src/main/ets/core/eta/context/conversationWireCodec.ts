// Ported from app/src/main/kotlin/io/github/mangi/eta/agent/model/AgentConversationCodec.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { ConversationMessage, conversationMessage } from '../model/conversation';
import { encodeTranscript, decodeTranscript } from '../storage/conversationCodec';
import { JsonObject, object, array, string, integer, copy } from '../protocol/providerTypes';
import { sensitiveToolNames } from './sensitiveToolNames';
export function toWire(m: ConversationMessage): JsonObject {
  return { role: m.role, content: m.contentJson.trim() ? JSON.parse(m.contentJson) : m.content,
    ...(m.messageId ? { _eta_message_id: m.messageId } : {}), ...(m.contextSummary ? { _eta_context_summary: true } : {}),
    ...(m.compactedUserTurns > 0 ? { _eta_compacted_users: m.compactedUserTurns } : {}), ...(m.summaryThroughUserTurn > 0 ? { _eta_summary_through_user: m.summaryThroughUserTurn } : {}),
    ...(m.toolCallId ? { tool_call_id: m.toolCallId } : {}), ...(m.reasoningContent ? { reasoning_content: m.reasoningContent } : {}), ...(m.toolCallsJson ? { tool_calls: JSON.parse(m.toolCallsJson) } : {}) };
}
export function fromWire(m: JsonObject): ConversationMessage {
  return { ...conversationMessage(string(m.role), typeof m.content === 'string' ? m.content : ''), contentJson: m.content != null && typeof m.content !== 'string' ? JSON.stringify(m.content) : '',
    messageId: string(m._eta_message_id), contextSummary: m._eta_context_summary === true, compactedUserTurns: integer(m._eta_compacted_users, 0), summaryThroughUserTurn: integer(m._eta_summary_through_user, 0),
    toolCallId: string(m.tool_call_id), reasoningContent: string(m.reasoning_content), toolCallsJson: Array.isArray(m.tool_calls) ? JSON.stringify(m.tool_calls) : '' };
}
export function transcript(messages: JsonObject[], start = 0, sensitiveIds: Set<string> = new Set()): ConversationMessage[] {
  const ids = new Set(sensitiveIds), rows = messages.slice(start);
  for (const m of rows) for (const call of array(m.tool_calls).map(object)) { const name = string(object(call?.function)?.name); if (name.startsWith('mcp_') || sensitiveToolNames.has(name)) ids.add(string(call?.id)); }
  return decodeTranscript(encodeTranscript(rows.map(m => { const value = copy(m); if (value.role === 'tool' && ids.has(string(value.tool_call_id))) value.content = '[敏感工具参数与原始结果仅供当前回合使用，未写入持久会话]';
    for (const call of array(value.tool_calls).map(object)) { const fn = object(call?.function); if (fn && ids.has(string(call?.id))) fn.arguments = '{"redacted":true}'; }
    return fromWire(value); })));
}
export function toolCallIds(message: JsonObject): string[] { return array(message.tool_calls).map(object).map(c => string(c?.id)).filter(Boolean); }
export function completeInterrupted(messages: ConversationMessage[]): ConversationMessage[] {
  const open = new Set<string>(), recovered: ConversationMessage[] = [];
  const close = (): void => { for (const id of open) recovered.push({ ...conversationMessage('tool', JSON.stringify({ ok: false, code: 'TOOL_INTERRUPTED', message: '工具批次已中断，未取得该调用的完整结果；执行状态未知，请先核实，不要自动重放。' })), toolCallId: id }); open.clear(); };
  for (const message of messages) { if (message.role !== 'tool') close(); recovered.push(message); if (message.toolCallsJson) for (const call of array(JSON.parse(message.toolCallsJson))) open.add(string(object(call)?.id)); if (message.role === 'tool') open.delete(message.toolCallId); } close(); return recovered;
}
