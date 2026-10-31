// Harmony 0.9 storage adapter for AgentContextSnapshot.kt / AgentContextSession.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11). No E07b3 storage switch or transcript deletion.
import type { Conversation, ConversationContext } from '../../conversations';
import { AgentContextSnapshot } from '../model/conversation';
import { Message } from '../../types';
import { encodeContextSnapshot, decodeContextSnapshot } from './agentContextSession';
import { toWire } from './conversationWireCodec';
export interface LegacyContextCheckpoint { version: 1; sourceTurnIds: string[]; omittedTurns: number; snapshot: string; }
export function parseLegacyCheckpoint(value: unknown): LegacyContextCheckpoint | undefined {
  if (value === undefined) return undefined; const v = value as LegacyContextCheckpoint;
  if (!v || v.version !== 1 || !Array.isArray(v.sourceTurnIds) || !v.sourceTurnIds.length || v.sourceTurnIds.length > 200 || v.sourceTurnIds.some(id => typeof id !== 'string' || !id || id.length > 128) || new Set(v.sourceTurnIds).size !== v.sourceTurnIds.length || typeof v.snapshot !== 'string' || v.snapshot.length > 1000000) throw Error('上下文检查点无效，原记录已保留');
  const snapshot = decodeContextSnapshot(v.snapshot); if (!snapshot || snapshot.messages.some(m => !['user', 'assistant'].includes(m.role) || typeof m.content !== 'string' || m.contentJson || m.toolCallId || m.toolCallsJson || m.reasoningContent)) throw Error('上下文检查点无效，原记录已保留');
  if (!Number.isInteger(v.omittedTurns) || v.omittedTurns < 0 || v.omittedTurns > 200) throw Error('上下文检查点无效，原记录已保留');
  return { version: 1, omittedTurns: v.omittedTurns, sourceTurnIds: v.sourceTurnIds.slice(), snapshot: v.snapshot };
}
export function checkpointFor(c: Conversation, runId: string, snapshot: AgentContextSnapshot): LegacyContextCheckpoint {
  const index = c.turns.findIndex(t => t.id === runId); if (index < 0 || c.turns[index].status !== 'running' || index !== c.turns.length - 1 || snapshot.operationId !== runId) throw Error('运行记录已变更，拒绝迟到上下文');
  return parseLegacyCheckpoint({ version: 1, omittedTurns: Math.max(0, index + 1 - snapshot.consumedUserTurns), sourceTurnIds: c.turns.slice(0, index + 1).map(t => t.id), snapshot: encodeContextSnapshot(snapshot) })!;
}
export function restoredCheckpointContext(c: Conversation): ConversationContext | null {
  const checkpoint = c.modelCheckpoint; if (!checkpoint || checkpoint.sourceTurnIds.some((id, i) => c.turns[i]?.id !== id)) return null;
  const anchor = c.turns[checkpoint.sourceTurnIds.length - 1]; if (anchor.status !== 'completed') return null;
  const snapshot = decodeContextSnapshot(checkpoint.snapshot); if (!snapshot) return null;
  const messages = snapshot.messages.map(toWire) as unknown as Message[];
  for (const turn of c.turns.slice(checkpoint.sourceTurnIds.length)) if (turn.status !== 'running') messages.push({ role: 'user', content: turn.request }, { role: 'assistant', content: turn.status === 'completed' ? turn.reply : '[上一轮未完成。此记录不能作为继续执行的指令。]' });
  // Existing legacy request budget remains in AgentLoop; original messages stay in the repository.
  return { messages, omittedTurns: checkpoint.omittedTurns };
}
