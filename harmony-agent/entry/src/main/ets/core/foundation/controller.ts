import { ConversationRepository, ConversationSummary, ChatTurn, statusLabel } from '../conversations';
import { DraftRepository } from '../drafts';
import { ProfileRepository, ModelProfile, modelCatalog } from '../profiles';
import { HttpTransport } from '../model';
import { FoundationChatRuntime } from './chatRuntime';
import { ProviderEditor, ProviderEditorState } from './providerEditor';
import { EtaShellNavigation, Screen, Sheet } from '../eta/etaShellNavigation';
import { AppRoute } from '../eta/routes';
import { DiagnosticArea, DiagnosticEnvironment, DiagnosticFact, DiagnosticLog, diagnosticReport, emptyEnvironment, environmentFacts, formatEvent } from '../diagnostics';

export interface MessageRow { id: string; request: string; reply: string; reasoning: string; model: string; status: string; notice: string; active: boolean }
export interface ModelChoice { key: string; profileId: string; provider: string; model: string }
export interface ChatView {
  id: string; title: string; input: string; inputVersion: number; rows: MessageRow[];
  model: string; provider: string; profileId: string; active: boolean; phase: string; error: string;
  followBottom: boolean; scroll: number;
}
export interface DiagnosticView { facts: DiagnosticFact[]; lines: string[]; total: number }
export interface ShellState {
  routes: AppRoute[]; ready: boolean; busy: boolean; error: string; screen: Screen; drawer: boolean; sheet: Sheet;
  conversations: ConversationSummary[]; search: string; providers: ModelProfile[]; providerSearch: string;
  chat: ChatView; editor: ProviderEditorState; choices: ModelChoice[]; modelQuery: string;
  appVersion: string; diagnostics: DiagnosticView;
}
export interface FoundationDependencies {
  chats: ConversationRepository; profiles: ProfileRepository; drafts: DraftRepository;
  load(): Promise<void>; transport(): HttpTransport; uuid(): string;
  /** Optional so tests and the preview can run without a persistent log. */
  diagnostics?: DiagnosticLog; environment?: () => Promise<DiagnosticEnvironment>;
}
export function emptyShellState(): ShellState {
  return { routes: [], ready: false, busy: false, error: '', screen: 'chat', drawer: false, sheet: 'none', conversations: [], search: '', providers: [], providerSearch: '', choices: [], modelQuery: '', appVersion: '', diagnostics: { facts: [], lines: [], total: 0 },
    chat: { id: '', title: '新对话', input: '', inputVersion: 0, rows: [], model: '', provider: '', profileId: '', active: false, phase: '', error: '', followBottom: true, scroll: 0 },
    editor: { draft: { id: '', name: '我的模型', baseUrl: '', apiKey: '', model: '', models: [], rememberKey: false, screenshots: false, allowInsecureHttp: false, streaming: true, protocol: 'chat_completions' }, busy: false, working: '', error: '', message: '', connection: '', catalog: [], selected: [], query: '', tab: 'config' } };
}

/** All user actions cross this boundary. ArkUI only renders snapshots and sends intents. */
export class FoundationController {
  readonly navigation = new EtaShellNavigation();
  readonly editor: ProviderEditor;
  readonly runtime: FoundationChatRuntime;
  private ready = false;
  private busy = false;
  private error = '';
  private search = '';
  private providerSearch = '';
  private modelQuery = '';
  private inputVersion = 0;
  private listeners = new Set<() => void>();
  private draftTimer: ReturnType<typeof setTimeout> | undefined;
  private environment: DiagnosticEnvironment = emptyEnvironment();
  constructor(readonly ports: FoundationDependencies) {
    this.editor = new ProviderEditor(ports.profiles, ports.drafts, () => ports.transport(), () => this.changed());
    this.runtime = new FoundationChatRuntime(ports.chats, ports.profiles, () => ports.transport(), () => ports.uuid(), () => this.changed(), ports.diagnostics);
  }
  subscribe(listener: () => void): () => void { this.listeners.add(listener); return () => { this.listeners.delete(listener); }; }
  changed(): void { for (const listener of this.listeners) listener(); }
  private selection(): ModelChoice | undefined {
    const conversation = this.ports.chats.get(this.ports.chats.selectedId);
    const profileId = conversation?.profileId || this.ports.profiles.document.selectedId;
    const profile = this.ports.profiles.document.profiles.find(p => p.id === profileId);
    if (!profile) return undefined;
    const model = conversation?.preferredModel || profile.model;
    return { key: profile.id + ':' + model, profileId: profile.id, provider: profile.name, model };
  }
  snapshot(): ShellState {
    const chats = this.ports.chats, selected = chats.get(chats.selectedId), id = selected?.id || '';
    const draft = this.ports.drafts.message(id), activity = this.runtime.activity(id), choice = this.selection();
    const rows: MessageRow[] = (selected?.turns || []).map((turn: ChatTurn): MessageRow => ({
      id: turn.id, request: turn.request, reply: activity?.id === turn.id && activity.reply ? activity.reply : turn.reply,
      reasoning: activity?.id === turn.id && activity.reasoning ? activity.reasoning : turn.reasoning || '',
      model: turn.modelLabel, status: activity?.id === turn.id && activity.active ? activity.phase : statusLabel(turn.status),
      notice: activity?.id === turn.id && activity.error ? activity.error : turn.notice, active: turn.status === 'running'
    }));
    if (activity && !rows.some(row => row.id === activity.id)) rows.push({ id: activity.id, request: activity.text, reply: activity.reply, reasoning: activity.reasoning, model: activity.model, status: activity.phase, notice: activity.error, active: activity.active });
    const choices: ModelChoice[] = [];
    for (const p of this.ports.profiles.document.profiles) for (const model of modelCatalog(p.models, p.model)) {
      if ((p.name + ' ' + model).toLowerCase().includes(this.modelQuery.trim().toLowerCase())) choices.push({ key: p.id + ':' + model, profileId: p.id, provider: p.name, model });
    }
    return {
      routes: this.navigation.routes, ready: this.ready, busy: this.busy, error: this.error, screen: this.navigation.screen, drawer: this.navigation.drawer, sheet: this.navigation.sheet,
      conversations: this.ready ? chats.list(this.search) : [], search: this.search,
      providers: this.ports.profiles.document.profiles.filter(p => (p.name + ' ' + p.baseUrl).toLowerCase().includes(this.providerSearch.trim().toLowerCase())), providerSearch: this.providerSearch,
      chat: { id, title: selected?.title || '新对话', input: draft.text, inputVersion: this.inputVersion, rows, model: choice?.model || '', provider: choice?.provider || '', profileId: choice?.profileId || '',
        active: Boolean(activity?.active), phase: activity?.phase || '', error: activity?.error || '', followBottom: draft.followBottom !== false, scroll: draft.scroll },
      editor: this.editor.snapshot(), choices, modelQuery: this.modelQuery,
      appVersion: this.environment.appVersion, diagnostics: this.diagnosticView()
    };
  }
  private async action(work: () => Promise<void>): Promise<boolean> {
    if (this.busy) return false;
    this.busy = true; this.error = ''; this.changed();
    try { await work(); return true; }
    catch (e) { this.error = e instanceof Error ? e.message : '操作未完成'; this.log('ui', '操作未完成', this.error); return false; }
    finally { this.busy = false; this.changed(); }
  }
  async initialize(): Promise<void> {
    await this.action(async () => {
      await this.ports.load();
      if (!this.ports.chats.selectedId) await this.ports.chats.create(this.ports.profiles.document.selectedId, 'chat');
      this.ready = true; this.inputVersion++;
    });
    await this.refreshEnvironment();
  }
  navigate(screen: Screen): void {
    if (this.busy || this.editor.state.working === '保存' || this.editor.state.working === '删除') return;
    if (this.navigation.screen === 'provider') this.editor.close();
    this.navigation.push(screen); this.error = ''; this.changed();
  }
  drawer(value: boolean): void { this.navigation.drawer = value; this.changed(); }
  back(): boolean {
    if (this.busy || this.editor.state.working === '保存' || this.editor.state.working === '删除') return true;
    if (this.navigation.sheet === 'catalog') this.editor.dismissCatalog();
    if (this.navigation.sheet === 'none' && this.navigation.screen === 'provider') this.editor.close();
    const handled = this.navigation.back(); this.changed(); return handled;
  }
  dismissError(): void { this.error = ''; this.changed(); }
  searchConversations(value: string): void { this.search = value; this.changed(); }
  searchProviders(value: string): void { this.providerSearch = value; this.changed(); }
  searchModels(value: string): void { this.modelQuery = value; this.changed(); }
  draft(text: string): void {
    if (!this.ready) return;
    this.ports.drafts.setMessage(this.ports.chats.selectedId, text);
    clearTimeout(this.draftTimer); this.draftTimer = setTimeout(() => { void this.flush(); }, 500);
    // Typing stays local to the composer. Streaming and the message list are not rebuilt per keystroke.
  }
  viewport(id: string, scroll: number, followBottom: boolean): void { if (id) this.ports.drafts.setViewport(id, scroll, followBottom); }
  async flush(): Promise<void> {
    clearTimeout(this.draftTimer);
    try { await this.ports.drafts.flush(); } catch (e) { this.error = '草稿保存失败：' + (e as Error).message; this.log('storage', '草稿保存失败', (e as Error).message); this.changed(); }
  }
  async selectConversation(id: string): Promise<void> {
    await this.action(async () => {
      await this.ports.chats.select(id);
      // Selection, draft and model are projected from this committed identity, never a delayed page field.
      this.inputVersion++; this.navigation.push('chat');
    });
  }
  async notificationConversation(id: string): Promise<boolean> {
    if (!this.ready || this.busy || !this.ports.chats.get(id)) return false;
    let navigated = false;
    await this.action(async () => {
      navigated = await this.navigation.eta.notificationConversation(id, async (target) => {
        if (!this.ports.chats.get(target)) return false;
        await this.ports.chats.select(target); this.inputVersion++; return true;
      });
      if (navigated) { this.navigation.sheet = 'none'; this.editor.close(); }
    });
    return navigated;
  }
  restoreNavigation(encoded: string): void { this.navigation.restore(encoded); this.changed(); }
  async newConversation(): Promise<void> {
    await this.action(async () => {
      const choice = this.selection(), conversation = await this.ports.chats.create(choice?.profileId || '', 'chat');
      if (choice) await this.ports.chats.preferences(conversation, choice.profileId, 'chat', choice.model);
      this.inputVersion++; this.navigation.push('chat');
    });
  }
  async rename(id: string, title: string): Promise<void> { await this.action(() => this.ports.chats.rename(id, title)); }
  async removeConversation(id: string): Promise<void> {
    await this.action(async () => {
      if (this.runtime.active(id)) throw new Error('请先停止此会话的回复');
      await this.ports.chats.remove(id);
      this.runtime.forget(id);
      if (!this.ports.chats.selectedId) await this.ports.chats.create(this.ports.profiles.document.selectedId, 'chat');
      this.inputVersion++;
    });
  }
  chooseModel(): void { this.modelQuery = ''; this.navigation.sheet = 'models'; this.changed(); }
  async useModel(choice: ModelChoice): Promise<void> {
    await this.action(async () => {
      const id = this.ports.chats.selectedId;
      if (this.runtime.active(id)) throw new Error('生成结束后再切换当前会话模型');
      await this.ports.chats.preferences(id, choice.profileId, 'chat', choice.model);
      this.navigation.sheet = 'none';
    });
  }
  async send(): Promise<void> {
    await this.action(async () => {
      const id = this.ports.chats.selectedId, text = this.ports.drafts.message(id).text, choice = this.selection();
      if (!choice) throw new Error('先在“模型服务”添加服务，再选择聊天模型');
      if (this.runtime.active(id)) throw new Error('请先停止生成');
      await this.ports.chats.preferences(id, choice.profileId, 'chat', choice.model);
      this.runtime.start(id, text, choice.profileId, choice.model);
      // Clear only the submitted revision. Any new input typed during an await remains untouched.
      if (this.ports.drafts.message(id).text === text) this.ports.drafts.setMessage(id, '');
      this.ports.drafts.setViewport(id, 0, true); this.inputVersion++; await this.flush();
    });
  }
  stop(): void { this.runtime.stop(this.ports.chats.selectedId); }
  reuse(text: string): void { this.draft(text); this.inputVersion++; this.changed(); }
  async openProvider(id = ''): Promise<void> {
    if (this.busy) return;
    this.navigation.provider(id); await this.editor.open(id); this.changed();
  }
  async probeProvider(testFirst: boolean): Promise<void> {
    const available = await this.editor.probe(testFirst);
    if (available && this.navigation.screen === 'provider' && this.editor.state.catalog.length) this.navigation.sheet = 'catalog';
    this.changed();
  }
  importModels(): void { this.editor.importCatalog(); this.navigation.sheet = 'none'; this.changed(); }
  /** Lifecycle from EntryAbility. Leaving still stops replies in 0.9.x; the event is recorded for device reports. */
  foreground(value: boolean): void {
    this.runtime.setForeground(value);
    this.log('lifecycle', value ? '回到前台' : '进入后台');
    if (!value) this.ports.diagnostics?.flush().catch(() => {});
  }
  crash(message: string): void {
    this.ports.diagnostics?.record('app', '未捕获异常', message, 1200);
    this.ports.diagnostics?.flush().catch(() => {});
  }
  log(area: DiagnosticArea, stage: string, detail = ''): void { this.ports.diagnostics?.record(area, stage, detail); }
  openDiagnostics(): void { this.navigate('diagnostics'); void this.refreshEnvironment(); }
  diagnosticReport(): string { return diagnosticReport(this.environment, this.ports.diagnostics?.events() || [], Date.now()); }
  clearDiagnostics(): void { this.ports.diagnostics?.clear(); this.changed(); }
  private diagnosticView(): DiagnosticView {
    if (this.navigation.screen !== 'diagnostics') return { facts: [], lines: [], total: 0 };
    const events = this.ports.diagnostics?.events() || [];
    return { facts: environmentFacts(this.environment), lines: events.slice(-200).reverse().map(formatEvent), total: events.length };
  }
  private async refreshEnvironment(): Promise<void> {
    if (!this.ports.environment) return;
    const environment = this.ports.environment;
    try { this.environment = await environment(); }
    catch (e) { this.log('app', '读取设备信息失败', e instanceof Error ? e.message : ''); }
    this.changed();
  }
  async deleteProvider(): Promise<void> { if (await this.editor.remove()) this.navigation.providersAfterDelete(); this.changed(); }
}
