// Ported from app/src/main/kotlin/io/github/mangi/eta/agent/model/AgentContextSession.kt,
// AgentContextSnapshot.kt @ 3b1794d. Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { JsonObject, ProviderModelConfig, ProviderRunControl, integer } from '../protocol/providerTypes';
import { AgentContextSnapshot, contextSnapshot } from '../model/conversation';
import { transcript } from './conversationWireCodec';
import { compactContext, ContextProvider } from './agentContextCompactor';
import { pendingSignedContext, signedContextFailure } from './ephemeralContext';
import { AgentModelFailure } from '../protocol/agentModelFailure';
import { decodeTranscript, encodeTranscript } from '../storage/conversationCodec';
export interface ContextCompactionEvent { operationId: string; phase: 'started' | 'completed' | 'failed'; before: number | null; reasonCode?: string; }
export function encodeContextSnapshot(snapshot: AgentContextSnapshot): string { return JSON.stringify({ version: 1, ...snapshot, messages: JSON.parse(encodeTranscript(snapshot.messages)) }); }
export function decodeContextSnapshot(raw?: string | null): AgentContextSnapshot | null { if (!raw?.trim()) return null; const value = JSON.parse(raw); if (value.version !== 1 || typeof value.operationId !== 'string' || !value.operationId.trim()) throw Error('上下文快照无效'); return { ...contextSnapshot(value.operationId, decodeTranscript(JSON.stringify(value.messages))), coveredUserTurns: value.coveredUserTurns ?? 0, consumedUserTurns: value.consumedUserTurns ?? 0, consumedSupplementCount: value.consumedSupplementCount ?? 0, consumedTranscriptMessages: value.consumedTranscriptMessages ?? null }; }
export class AgentContextSession {
  private inputTokens: number | null = null; private compacted = false; private consumedSupplementCount = 0; private consumedUserTurns: number; private committed: AgentContextSnapshot | null = null;
  constructor(private config: ProviderModelConfig, private messages: JsonObject[], private systemCount: number, private operationId: string, private provider: ContextProvider, private control: ProviderRunControl, private sensitiveIds: () => Set<string>, private emit: (event: ContextCompactionEvent) => void, private persist: (snapshot: AgentContextSnapshot) => Promise<void>, private transcriptSize: () => number = () => 0, private uuid: () => string = () => 'compact-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2), private roleplay = false) {
    this.consumedUserTurns = messages.slice(systemCount).reduce((n, m) => n + integer(m._eta_compacted_users, 0) + (m.role === 'user' ? 1 : 0), 0);
  }
  snapshot(): AgentContextSnapshot | null { return this.committed; }
  observeInputTokens(value: number | null): void { this.inputTokens = value !== null && value >= 0 ? value : null; }
  userAppended(): void { this.consumedUserTurns++; this.consumedSupplementCount++; }
  private createSnapshot(candidate: JsonObject[]): AgentContextSnapshot { const messages = transcript(candidate.slice(this.systemCount).filter(m => m._eta_observation !== true), 0, this.sensitiveIds()); return { operationId: this.operationId, messages, coveredUserTurns: messages.reduce((n, m) => n + m.compactedUserTurns, 0), consumedUserTurns: this.consumedUserTurns, consumedSupplementCount: this.consumedSupplementCount, consumedTranscriptMessages: this.transcriptSize() }; }
  private async publish(candidate = this.messages): Promise<void> { if (!this.compacted) return; const snapshot = this.createSnapshot(candidate); encodeContextSnapshot(snapshot); await this.persist(snapshot); this.control.check(); this.committed = snapshot; }
  async compact(force = false, final = false): Promise<void> {
    const before = this.inputTokens; if (pendingSignedContext(this.messages)) { if (force) throw signedContextFailure(this.messages); return; }
    if (!force && (this.config.autoCompactionEnabled === false || !this.config.contextWindow || before === null || before < this.config.contextWindow * 0.85)) {
      try { await this.publish(); } catch (failure) { this.control.check(); if (!final) throw failure; this.committed = this.createSnapshot(this.messages); this.emit({ operationId: this.operationId, phase: 'failed', before, reasonCode: 'CONTEXT_CHECKPOINT_FAILED' }); } return;
    }
    const operationId = this.uuid(); this.emit({ operationId, phase: 'started', before });
    try { const candidate = await compactContext(this.config, this.provider, this.control, this.messages, this.systemCount, this.sensitiveIds(), this.operationId, this.roleplay); this.control.check();
      const wasCompacted = this.compacted; this.compacted = true; try { await this.publish(candidate); } catch (failure) { this.compacted = wasCompacted; throw failure; }
      this.messages.splice(0, this.messages.length, ...candidate); this.inputTokens = null; this.emit({ operationId, phase: 'completed', before });
    } catch (failure) { this.control.check(); this.emit({ operationId, phase: 'failed', before, reasonCode: failure instanceof AgentModelFailure ? failure.code : 'CONTEXT_SUMMARY_FAILED' }); if (!final) throw failure; this.committed = this.createSnapshot(this.messages); }
  }
}
