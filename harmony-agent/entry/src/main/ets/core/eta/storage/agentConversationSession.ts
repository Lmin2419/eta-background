// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/app/AgentAppState.kt:
// select/ensureLoaded, paging, create/delete, result settlement and checkpoint prompt recovery @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
// Explicit runtime/archive/macro ports; not wired into FoundationController until E07b3.
import { AgentChatUiState, ConversationMessage, SystemNoticeCode, emptyChatState, userMessage, agentMessage, conversationMessage, queuedMessage } from '../model/conversation';
import { RoleplayBinding, RuntimeRunResult, emptyRoleplayMessages, runResult } from '../model/runtimeProjection';
import { AgentRunStore } from '../agentRunStore';
import { AgentRuntimeHistoryReducer } from '../agentRuntimeHistoryReducer';
import { AgentMessageQueueReducer } from '../agentMessageQueueReducer';
import { AgentConversationRevisionReducer } from '../agentConversationRevisionReducer';
import { AgentRunMessageProjector } from '../agentRunMessageProjector';
import { AssistantConversationSeed } from '../assistantConversationSeed';
import { RoleplayConversationReducer } from '../roleplayConversationReducer';
import { ConversationMarkdownExporter, ExportLabels } from '../conversationMarkdownExporter';
import { AgentConversationStore, ConversationSnapshot, emptyConversationSnapshot } from './agentConversationStore';
import { AgentConversationPersistence } from './agentConversationPersistence';
import { SerialGate } from './conversationDao';
import { valueEqual } from './conversationCodec';
export interface PendingConversationPrompt { conversationId: string; runId: string; messageId: string; text: string; }
export interface ArchivedConversation { conversationId: string; title: string; prompt: string; result: RuntimeRunResult; }
export interface ConversationSessionPorts {
  uuid(): string;
  pendingPrompts?(): Promise<PendingConversationPrompt[]>;
  archives?(): Promise<ArchivedConversation[]>;
  expandGreeting?(binding: RoleplayBinding, greeting: string): string;
  dispatchQueued?(conversationId: string, content: string, history: ConversationMessage[]): Promise<void>;
}
function canReplace(current: AgentChatUiState, saved: AgentChatUiState): boolean {
  return current.isConversationLoaded && !current.isStreaming && !current.messageEdit && current.messages === saved.messages &&
    current.messageStartIndex === saved.messageStartIndex && current.history === saved.history && current.journal === saved.journal && valueEqual(current.roleplayMessages, saved.roleplayMessages);
}
export class AgentConversationSession {
  snapshot = emptyConversationSnapshot(); private emptyHome = emptyChatState();
  isLoadingConversation = false; conversationLoadFailed = false;
  historyLoadState: 'Idle' | 'Loading' | 'Failed' = 'Idle';
  readonly runs = new AgentRunStore(() => {});
  private writer: AgentConversationPersistence; private contentGate = new SerialGate();
  private selectionVersion = 0; private pageVersion = 0; private releasesEarlier = false;
  private pendingDeletes = new Set<string>(); private queryVersion = 0;
  searchMatches: Set<string> | null = null;
  constructor(readonly store: AgentConversationStore, private ports: ConversationSessionPorts) { this.writer = new AgentConversationPersistence(store); }
  async initialize(): Promise<void> { this.snapshot = await this.store.load(); this.writer = new AgentConversationPersistence(this.store, this.snapshot); await this.refreshRuntimeResults(); }
  get homeState(): AgentChatUiState { return this.snapshot.conversationsById.get(this.snapshot.selectedConversationId || '') ?? this.emptyHome; }
  get isLoadingEarlierMessages(): boolean { return this.historyLoadState === 'Loading'; }
  private update(id: string, value: AgentChatUiState): void { this.snapshot.conversationsById.set(id, value); }
  private capture(): ConversationSnapshot { return { ...this.snapshot, conversationsById: new Map(this.snapshot.conversationsById), titles: new Map(this.snapshot.titles), updatedAt: new Map(this.snapshot.updatedAt), unalignedConversationIds: new Set(this.snapshot.unalignedConversationIds) }; }
  async persist(): Promise<void> {
    const saved = this.capture(); await this.writer.save(saved);
    for (const [id, state] of saved.conversationsById) {
      const current = this.snapshot.conversationsById.get(id);
      if (!current?.isConversationLoaded) { await this.writer.forgetConversation(id); continue; }
      if (!state.isConversationLoaded || id === this.snapshot.selectedConversationId || state.isStreaming ||
          state.messageEdit || this.runs.activeRunId(id) || current !== state) continue;
      await this.writer.forgetConversation(id);
      if (id !== this.snapshot.selectedConversationId && !this.runs.activeRunId(id) && this.snapshot.conversationsById.get(id) === state) {
        this.update(id, AgentConversationStore.unloadConversation(state));
      }
    }
  }
  private cancelPage(): void { this.pageVersion++; this.releasesEarlier = false; this.historyLoadState = 'Idle'; }
  private ensureLoaded(id: string): Promise<boolean> {
    return this.contentGate.run(async () => {
      const current = this.snapshot.conversationsById.get(id); if (!current) return false; if (current.isConversationLoaded) return true;
      await this.persist(); const loaded = await this.store.loadConversation(id); const after = this.snapshot.conversationsById.get(id);
      if (!loaded || !after) return false;
      const restored = { ...loaded, input: after.input, pendingImages: after.pendingImages, pendingFileReferences: after.pendingFileReferences };
      await this.writer.adoptConversation(id, restored, this.snapshot.titles.get(id) || '', this.snapshot.updatedAt.get(id) || 0);
      const latest = this.snapshot.conversationsById.get(id); if (!latest) return false;
      this.update(id, { ...restored, input: latest.input, pendingImages: latest.pendingImages, pendingFileReferences: latest.pendingFileReferences }); return true;
    });
  }
  async selectConversation(id: string): Promise<boolean> {
    if (!this.snapshot.conversationsById.has(id)) return false;
    const version = ++this.selectionVersion; this.cancelPage(); this.snapshot.selectedConversationId = id;
    this.isLoadingConversation = !this.homeState.isConversationLoaded; this.conversationLoadFailed = false;
    try { const loaded = await this.ensureLoaded(id); if (version !== this.selectionVersion) return false; this.conversationLoadFailed = !loaded; if (loaded) await this.persist(); return loaded; }
    catch (_) { if (version === this.selectionVersion) this.conversationLoadFailed = true; return false; }
    finally { if (version === this.selectionVersion) this.isLoadingConversation = false; }
  }
  async openConversationById(id: string): Promise<boolean> { await this.refreshRuntimeResults(); return this.snapshot.conversationsById.has(id) ? this.selectConversation(id) : false; }
  loadEarlierMessages(): Promise<void> { return this.changeWindow(true); }
  updateHistoryViewport(atLatest: boolean): Promise<void> {
    if (!atLatest) { if (this.releasesEarlier) this.cancelPage(); return Promise.resolve(); }
    return AgentConversationStore.shouldReleaseEarlierMessages(this.homeState.messages.length) ? this.changeWindow(false) : Promise.resolve();
  }
  private async changeWindow(earlier: boolean): Promise<void> {
    const id = this.snapshot.selectedConversationId, home = this.homeState;
    if (!id || this.isLoadingEarlierMessages || !home.isConversationLoaded || (earlier && !home.hasEarlierMessages) || home.isStreaming || home.messageEdit) return;
    const version = ++this.pageVersion; this.releasesEarlier = !earlier; this.historyLoadState = 'Loading';
    try {
      await this.contentGate.run(async () => {
        if (version !== this.pageVersion) return;
        const saved = this.snapshot.conversationsById.get(id); if (!saved) return;
        await this.persist(); if (version !== this.pageVersion) return;
        const page = earlier ? await this.store.loadEarlierMessages(id, saved.messageStartIndex) : await this.store.loadRecentMessages(id);
        const current = this.snapshot.conversationsById.get(id); if (version !== this.pageVersion || !current || !canReplace(current, saved)) return;
        let window = RoleplayConversationReducer.decorate({ ...saved, messages: earlier ? [...page.messages, ...saved.messages] : page.messages, messageStartIndex: page.startIndex, hasEarlierMessages: page.hasEarlierMessages });
        window = { ...window, roleplayOriginalContents: await this.store.loadRoleplayMessageContents(id, RoleplayConversationReducer.requiredOriginalContentIds(window)) };
        if (version !== this.pageVersion) return;
        await this.writer.adoptConversation(id, window, this.snapshot.titles.get(id) || '', this.snapshot.updatedAt.get(id) || 0);
        const latest = this.snapshot.conversationsById.get(id); if (version !== this.pageVersion || !latest || !canReplace(latest, saved)) return;
        this.update(id, { ...latest, messages: window.messages, messageStartIndex: window.messageStartIndex, hasEarlierMessages: window.hasEarlierMessages, roleplayOriginalContents: window.roleplayOriginalContents });
      });
    } catch (_) { if (version === this.pageVersion && earlier) this.historyLoadState = 'Failed'; }
    finally { if (version === this.pageVersion) { this.releasesEarlier = false; if (this.historyLoadState === 'Loading') this.historyLoadState = 'Idle'; } }
  }
  async createConversation(): Promise<void> { this.selectionVersion++; this.cancelPage(); this.isLoadingConversation = false; this.conversationLoadFailed = false; this.snapshot.selectedConversationId = null; this.emptyHome = emptyChatState(); this.searchMatches = null; await this.persist(); }
  async startCharacterConversation(binding: RoleplayBinding, greeting: string): Promise<void> {
    if (!this.ports.expandGreeting) throw Error('Character macro port unavailable');
    await this.createConversation(); const id = this.ports.uuid(), messageId = `greeting-${id}`, text = this.ports.expandGreeting(binding, greeting);
    const history = text.trim() ? [{ ...conversationMessage('assistant', text), messageId }] : [];
    const state = { ...emptyChatState(), roleplay: binding, history, journal: history,
      messages: text.trim() ? [{ ...agentMessage(messageId, text), characterEditable: true }] : [],
      roleplayMessages: { ...emptyRoleplayMessages(), links: new Map(text.trim() ? [[messageId, { transcriptMessageId: messageId, blockOrder: 0 }]] : []) } };
    this.update(id, state); this.snapshot.selectedConversationId = id; this.snapshot.titles.set(id, binding.characterName ?? binding.name); this.snapshot.updatedAt.set(id, Date.now()); await this.persist();
  }
  async deleteConversation(id: string): Promise<void> {
    const run = this.runs.activeRunId(id);
    if (run) { this.pendingDeletes.add(id); this.runs.requestStop(run, true); return; }
    const before = this.capture(), wasSelected = this.snapshot.selectedConversationId === id;
    this.snapshot.conversationsById.delete(id); this.snapshot.titles.delete(id); this.snapshot.updatedAt.delete(id);
    if (wasSelected) { this.selectionVersion++; this.cancelPage(); this.snapshot.selectedConversationId = [...this.snapshot.conversationsById.keys()][0] ?? null; }
    try { await this.persist(); } catch (error) { this.snapshot = before; throw error; }
    if (wasSelected && this.snapshot.selectedConversationId) await this.selectConversation(this.snapshot.selectedConversationId);
  }
  async deleteMessageTurn(id: string): Promise<void> {
    const selected = this.snapshot.selectedConversationId; if (!selected || this.homeState.isStreaming) return;
    const previous = this.homeState, changed = AgentConversationRevisionReducer.deleteFromTurn(previous, id); if (!changed) return;
    this.update(selected, changed); try { await this.persist(); } catch (error) { this.update(selected, previous); throw error; }
  }
  async search(query: string, noticeCodes: SystemNoticeCode[] = []): Promise<void> {
    const version = ++this.queryVersion; if (!query.trim()) { this.searchMatches = null; return; }
    const found = await this.store.searchConversationIds(query, noticeCodes); if (version === this.queryVersion) this.searchMatches = found;
  }
  async exportMarkdown(id: string, labels: ExportLabels): Promise<string | null> {
    if (!this.snapshot.conversationsById.has(id)) return null; await this.persist();
    return ConversationMarkdownExporter.export(this.snapshot.titles.get(id) || '新对话', await this.store.loadAllMessages(id), labels);
  }
  async refreshRuntimeResults(): Promise<void> {
    let changed = false;
    for (const prompt of await this.ports.pendingPrompts?.() || []) {
      if (!await this.ensureLoaded(prompt.conversationId)) continue; const state = this.snapshot.conversationsById.get(prompt.conversationId)!;
      if (!AgentRuntimeHistoryReducer.wasApplied(state, prompt.runId) && !state.messages.some(m => m.id === prompt.messageId)) { this.update(prompt.conversationId, { ...state, messages: [...state.messages, userMessage(prompt.messageId, prompt.text)] }); changed = true; }
    }
    for (const archive of await this.ports.archives?.() || []) {
      const existing = this.snapshot.conversationsById.get(archive.conversationId);
      if (existing) await this.ensureLoaded(archive.conversationId);
      let state = this.snapshot.conversationsById.get(archive.conversationId) || emptyChatState();
      if (AgentRuntimeHistoryReducer.wasApplied(state, archive.result.runId)) continue;
      state = AssistantConversationSeed.apply(state, archive.result.runId, archive.prompt);
      state = AgentRuntimeHistoryReducer.apply(state, archive.result.runId, archive.result.transcript).state;
      this.update(archive.conversationId, { ...state, messages: AgentRunMessageProjector.applyResult(archive.result.runId, state.messages, archive.result.content, null) });
      this.snapshot.titles.set(archive.conversationId, archive.title); this.snapshot.updatedAt.set(archive.conversationId, Date.now()); changed = true;
    }
    if (changed) await this.persist();
  }
  registerRun(runId: string, conversationId: string): boolean {
    const state = this.snapshot.conversationsById.get(conversationId); if (!state?.isConversationLoaded || !this.runs.register(runId, conversationId)) return false;
    this.update(conversationId, { ...state, isStreaming: true }); return true;
  }
  stopCurrentRun(): void { const id = this.runs.activeRunId(this.snapshot.selectedConversationId); if (id) this.runs.requestStop(id, true); }
  enqueue(content: string): void { const id = this.snapshot.selectedConversationId; if (id) this.update(id, AgentMessageQueueReducer.enqueue(this.homeState, queuedMessage(this.ports.uuid(), content))); }
  async completeRun(result: RuntimeRunResult): Promise<void> {
    const runId = result.runId, id = this.runs.conversationId(runId); if (!id || !this.runs.beginSettlement(runId)) return;
    const existing = this.snapshot.conversationsById.get(id); if (!existing) { this.runs.remove(runId); return; }
    const applied = AgentRuntimeHistoryReducer.apply(existing, runId, result.transcript, result.contextSnapshot, !result.ok || result.contextSnapshot !== null).state;
    const notice = result.ok ? (result.content.trim() ? null : 'EmptyResult') : 'RuntimeFailed';
    this.update(id, { ...applied, messages: AgentRunMessageProjector.applyResult(runId, applied.messages, result.content, notice), isStreaming: true });
    let saved = false;
    try { await this.persist(); saved = true; }
    finally {
      const stopped = this.runs.wasStoppedByUser(runId); this.runs.remove(runId);
      const current = this.snapshot.conversationsById.get(id); if (current) this.update(id, { ...current, isStreaming: false });
      if (this.pendingDeletes.delete(id)) { if (saved) await this.deleteConversation(id); }
      else if (saved && current && this.ports.dispatchQueued) {
        const next = AgentMessageQueueReducer.pollNextToSendAfterRun(this.snapshot.conversationsById.get(id)!, stopped);
        if (next.item) {
          const messageId = `user-${next.item.id}`, entry = { ...conversationMessage('user', next.item.content), messageId };
          const state = { ...next.state, messages: [...next.state.messages, userMessage(messageId, next.item.content)], history: [...next.state.history, entry], journal: [...next.state.journal, entry] };
          this.update(id, state); await this.persist(); await this.ports.dispatchQueued(id, next.item.content, state.history);
        } else this.update(id, next.state);
      }
    }
  }
}
