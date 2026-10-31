// Ported text-only execution seam from app/src/main/kotlin/io/github/mangi/eta/agent/model/AgentLoop.kt,
// AgentModelClient.kt @ 3b1794d. Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
// No tool executor, retries or background entry is registered by this adapter.
import { JsonObject, ProviderRequest, ProviderRunControl, ProviderEventSink, string } from '../protocol/providerTypes';
import { ConversationMessage, AgentContextSnapshot } from '../model/conversation';
import { AgentContextSession, ContextCompactionEvent } from './agentContextSession';
import { ContextProvider } from './agentContextCompactor';
import { transcript, toWire, toolCallIds } from './conversationWireCodec';
import { AgentModelFailure } from '../protocol/agentModelFailure';
export interface TextContextOptions { operationId: string; onSnapshot: (snapshot: AgentContextSnapshot) => Promise<void>; onCompaction?: (event: ContextCompactionEvent) => void; compactOnly?: boolean; }
export interface TextContextResult { assistant: JsonObject | null; transcript: ConversationMessage[]; contextSnapshot: AgentContextSnapshot | null; }
export class ContextExecutionFailure extends Error { constructor(readonly cause: unknown, readonly transcript: ConversationMessage[]) { super(cause instanceof Error ? cause.message : '模型请求未完成'); } }
export async function runTextContext(request: ProviderRequest, control: ProviderRunControl, provider: ContextProvider, emit: ProviderEventSink, options: TextContextOptions): Promise<TextContextResult> {
  const messages = request.messages.slice(); let systemCount = 0; while (messages[systemCount]?.role === 'system') systemCount++;
  const incremental: ConversationMessage[] = [], session = new AgentContextSession(request.config, messages, systemCount, options.operationId, provider, control, () => new Set(), options.onCompaction || (() => {}), options.onSnapshot, () => incremental.length);
  if (options.compactOnly) { await session.compact(true); return { assistant: null, transcript: [], contextSnapshot: session.snapshot() }; }
  try {
    await session.compact(); let inputTokens: number | null = null;
    const assistant = await provider({ ...request, messages }, control, event => { if (event.type === 'Usage') inputTokens = event.contextInputTokens ?? event.usage.inputTokens ?? inputTokens; emit(event); });
    control.check(); if (toolCallIds(assistant).length) throw new AgentModelFailure('TEXT_MODE_TOOL_CALL', false, '当前聊天未启用工具，返回的工具调用未执行');
    if (!['stop', 'end_turn'].includes(string(assistant.finish_reason)) || !string(assistant.content).trim()) throw new AgentModelFailure('TEXT_RESPONSE_INCOMPLETE', false, '模型回复未完整结束，请检查模型配置或输出长度');
    session.observeInputTokens(inputTokens); const durable = transcript([assistant])[0]; incremental.push(durable); messages.push(toWire(durable));
    await session.compact(false, true); return { assistant, transcript: incremental, contextSnapshot: session.snapshot() };
  } catch (failure) { control.check(); throw new ContextExecutionFailure(failure, incremental); }
}
