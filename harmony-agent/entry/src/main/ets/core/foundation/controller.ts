// Ported interaction adapter from app/src/main/kotlin/io/github/mangi/eta/ui/app/AgentAppState.kt, AgentAppRoot.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { AgentMessageQueueReducer as Queue } from '../eta/agentMessageQueueReducer';
import { AgentConversationRevisionReducer as Revision } from '../eta/agentConversationRevisionReducer';
import { foundationChatMessages } from '../eta/foundationChatMessages';
import { AgentChatMessageUi, AgentChatUiState, QueuedMessageUi, MessageEditUiState, emptyChatState, queuedMessage, conversationMessage } from '../eta/model/conversation';
import { ConversationRepository, ConversationSummary, ChatTurn, TurnStatus, statusLabel } from '../conversations';
import { DraftRepository } from '../drafts';
import { ProfileRepository, ModelProfile, modelCatalog } from '../profiles';
import { HttpTransport } from '../model';
import { FoundationChatRuntime } from './chatRuntime';
import { ProviderEditor, ProviderEditorState } from './providerEditor';
import { EtaShellNavigation, Screen, Sheet } from '../eta/etaShellNavigation';
import { AppRoute } from '../eta/routes';
import { AgentModelPickerUiState, AgentModelProviderGroupUi, AgentModelOptionUi, AgentContextUsageUi, ModelPickerSession, projectModelPicker, filterModelGroups, modelCount } from '../eta/modelPicker';
import { foundationPickerProviders } from '../eta/foundationModelPicker';
import { DiagnosticArea, DiagnosticEnvironment, DiagnosticFact, DiagnosticLog, diagnosticReport, emptyEnvironment, environmentFacts, formatEvent } from '../diagnostics';

export interface MessageRow { id: string; request: string; reply: string; reasoning: string; model: string; status: string; notice: string; active: boolean; resultStatus?: TurnStatus }
export interface ModelChoice { key: string; profileId: string; provider: string; model: string }
export interface MessageMutation { conversationId:string; messageId:string; turnId:string; laterTurns:number; kind:'delete'|'regenerate' }
export interface ChatView {
  queuedMessages:QueuedMessageUi[]; messageEdit:MessageEditUiState|null;
  id: string; title: string; input: string; inputVersion: number; rows: MessageRow[]; messages: AgentChatMessageUi[];
  model: string; provider: string; profileId: string; active: boolean; phase: string; error: string;
  followBottom: boolean; scroll: number;
}
export interface DiagnosticView { facts: DiagnosticFact[]; lines: string[]; total: number }
export interface ShellState {
  mutation:MessageMutation|null; routes: AppRoute[]; ready: boolean; busy: boolean; error: string; screen: Screen; drawer: boolean; sheet: Sheet;
  conversations: ConversationSummary[]; search: string; providers: ModelProfile[]; providerSearch: string;
  chat: ChatView; editor: ProviderEditorState; choices: ModelChoice[]; modelQuery: string;
  picker: AgentModelPickerUiState; pickerGroups: AgentModelProviderGroupUi[]; pickerFilter: string | null; pickerPending: string | null; contextUsage: AgentContextUsageUi;
  appVersion: string; diagnostics: DiagnosticView;
}
export interface FoundationDependencies {
  chats: ConversationRepository; profiles: ProfileRepository; drafts: DraftRepository;
  load(): Promise<void>; transport(): HttpTransport; uuid(): string;
  /** Optional so tests and the preview can run without a persistent log. */
  elapsed?:()=>number; diagnostics?: DiagnosticLog; environment?: () => Promise<DiagnosticEnvironment>;
}
export function emptyShellState(): ShellState {
  return { mutation:null,routes: [], ready: false, busy: false, error: '', screen: 'chat', drawer: false, sheet: 'none', conversations: [], search: '', providers: [], providerSearch: '', choices: [], modelQuery: '', picker: {providerGroups: [],selectedModel:null,isChanging:false},pickerGroups:[],pickerFilter:null,pickerPending:null,contextUsage:{contextTokens:null,contextWindow:null},appVersion: '', diagnostics: { facts: [], lines: [], total: 0 },
    chat: { queuedMessages:[],messageEdit:null,id: '', title: '新对话', input: '', inputVersion: 0, rows: [], messages: [], model: '', provider: '', profileId: '', active: false, phase: '', error: '', followBottom: true, scroll: 0 },
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
  private pickerSession = new ModelPickerSession();
  private modelQuery = '';
  private inputVersion = 0;
  private queueStates=new Map<string,QueuedMessageUi[]>();
  private edits=new Map<string,MessageEditUiState>();
  private mutation:MessageMutation|null=null;
  private isForeground=true;
  private messageCache = new Map<string,AgentChatMessageUi[]>();
  private listeners = new Set<() => void>();
  private draftTimer: ReturnType<typeof setTimeout> | undefined;
  private environment: DiagnosticEnvironment = emptyEnvironment();
  constructor(readonly ports: FoundationDependencies) {
    this.editor = new ProviderEditor(ports.profiles, ports.drafts, () => ports.transport(), () => this.changed());
    this.runtime = new FoundationChatRuntime(ports.chats, ports.profiles, () => ports.transport(), () => ports.uuid(), () => this.changed(), ports.diagnostics,ports.elapsed, (id,stopped,saved)=>this.runSettled(id,stopped,saved));
  }
  subscribe(listener: () => void): () => void { this.listeners.add(listener); return () => { this.listeners.delete(listener); }; }
  changed(): void { for (const listener of this.listeners) listener(); }
  private selection(id=this.ports.chats.selectedId): ModelChoice | undefined {
    const conversation = this.ports.chats.get(id);
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
      resultStatus:turn.status, model: turn.modelLabel, status: activity?.id === turn.id && activity.active ? activity.phase : statusLabel(turn.status),
      notice: activity?.id === turn.id && activity.error ? activity.error : turn.notice, active: turn.status === 'running'
    }));
    if (activity?.active && !rows.some(row => row.id === activity.id)) rows.push({ id: activity.id, request: activity.text, reply: activity.reply, reasoning: activity.reasoning, model: activity.model, status: activity.phase, notice: activity.error, active: activity.active });
    let messages = foundationChatMessages(rows,this.messageCache.get(id));
    const projected=this.runtime.projection(id);if(projected.length){const byId=new Map(projected.map(m=>[m.id,m]));messages=messages.map(m=>byId.get(m.id)||m);}
    const messageEdit=this.edits.get(id)||null;
    this.messageCache.set(id,messages);
    for(const key of this.messageCache.keys()) if(key!==id&&!chats.get(key))this.messageCache.delete(key);
    const choices: ModelChoice[] = [];
    for (const p of this.ports.profiles.document.profiles) for (const model of modelCatalog(p.models, p.model)) {
      if ((p.name + ' ' + model).toLowerCase().includes(this.modelQuery.trim().toLowerCase())) choices.push({ key: p.id + ':' + model, profileId: p.id, provider: p.name, model });
    }
    const picker = projectModelPicker(foundationPickerProviders(this.ports.profiles.document.profiles),choice?.profileId??null,choice?.key??null);
    picker.isChanging=this.busy;
    let contextTokens: number | null = null;
    for(let i=(selected?.turns.length||0)-1;i>=0;i--){const usage=selected?.turns[i].usage;if(usage){contextTokens=usage.input;break;}}
    return {
      picker,pickerGroups:filterModelGroups(picker,this.modelQuery,this.pickerSession.provider),pickerFilter:this.pickerSession.provider,pickerPending:this.pickerSession.pendingId,
      contextUsage:{contextTokens,contextWindow:picker.selectedModel?.contextWindow??null},
      mutation:this.mutation,routes: this.navigation.routes, ready: this.ready, busy: this.busy, error: this.error, screen: this.navigation.screen, drawer: this.navigation.drawer, sheet: this.navigation.sheet,
      conversations: this.ready ? chats.list(this.search) : [], search: this.search,
      providers: this.ports.profiles.document.profiles.filter(p => (p.name + ' ' + p.baseUrl).toLowerCase().includes(this.providerSearch.trim().toLowerCase())), providerSearch: this.providerSearch,
      chat: { queuedMessages:this.queueStates.get(id)||[],messageEdit,id, title: selected?.title || '新对话', input: draft.text, inputVersion: this.inputVersion, rows, messages:Revision.visibleMessagesForEdit(messages,messageEdit?.targetMessageId||null), model: choice?.model || '', provider: choice?.provider || '', profileId: choice?.profileId || '',
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
    this.pickerSession.cancel();
    this.navigation.push(screen); this.error = ''; this.changed();
  }
  drawer(value: boolean): void { this.navigation.drawer = value; this.changed(); }
  back(): boolean {
    if (this.busy || this.editor.state.working === '保存' || this.editor.state.working === '删除') return true;
    if (this.navigation.sheet === 'models') this.pickerSession.cancel();
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
      this.runtime.forget(id);this.queueStates.delete(id);this.edits.delete(id);
      if (!this.ports.chats.selectedId) await this.ports.chats.create(this.ports.profiles.document.selectedId, 'chat');
      this.inputVersion++;
    });
  }
  chooseModel(): void { if(!this.ready||this.busy||this.runtime.active(this.ports.chats.selectedId)||modelCount(this.snapshot().picker)===0)return; this.pickerSession.open(); this.modelQuery = ''; this.navigation.sheet = 'models'; this.changed(); }
  filterPickerProvider(id: string | null): void { this.pickerSession.provider=id; this.changed(); }
  confirmPickerModel(model: AgentModelOptionUi): void {
    if(this.navigation.sheet!=='models'||this.runtime.active(this.ports.chats.selectedId))return;
    if(this.pickerSession.choose(model.id,()=>{
      if(this.navigation.sheet==='models'&&!this.runtime.active(this.ports.chats.selectedId)) void this.useModel({key:model.id,profileId:model.providerId,provider:model.providerName,model:model.modelId});
    }))this.changed();
  }
  async useModel(choice: ModelChoice): Promise<void> {
    const saved = await this.action(async () => {
      const id = this.ports.chats.selectedId;
      if (this.runtime.active(id)) throw new Error('生成结束后再切换当前会话模型');
      await this.ports.chats.preferences(id, choice.profileId, 'chat', choice.model);
      this.navigation.sheet = 'none'; this.pickerSession.cancel();
    });
    if(!saved) {this.pickerSession.cancel();this.changed();}
  }
  private etaState(id:string):AgentChatUiState {
    const c=this.ports.chats.get(id),state=emptyChatState();
    state.input=this.ports.drafts.message(id).text;state.isStreaming=this.runtime.active(id);
    state.queuedMessages=this.queueStates.get(id)||[];state.messageEdit=this.edits.get(id)||null;
    const rows:MessageRow[]=(c?.turns||[]).map(t=>({id:t.id,request:t.request,reply:t.reply,reasoning:t.reasoning||'',model:t.modelLabel,status:t.status,notice:t.notice,active:t.status==='running',resultStatus:t.status}));
    state.messages=foundationChatMessages(rows);
    for(const turn of c?.turns||[]){state.history.push(conversationMessage('user',turn.request));if(turn.reply)state.history.push(conversationMessage('assistant',turn.reply));}
    state.journal=state.history;return state;
  }
  private queueState(id:string,state:AgentChatUiState):void {this.queueStates.set(id,state.queuedMessages);this.changed();}
  private async submit(id:string,text:string,reviseFromTurnId=''):Promise<void>{
    const choice=this.selection(id);if(!choice)throw new Error('先在“模型服务”添加服务，再选择聊天模型');
    await this.ports.chats.preferences(id,choice.profileId,'chat',choice.model);
    this.runtime.start(id,text,choice.profileId,choice.model,reviseFromTurnId);
    await this.runtime.admitted(id);
    this.ports.drafts.setViewport(id,0,true);
  }
  async send(immediateInterrupt=false):Promise<void> {
    await this.action(async()=>{
      const id=this.ports.chats.selectedId,text=this.ports.drafts.message(id).text;
      if(!text.trim()||text.length>8000)throw new Error('请输入消息，最多 8000 字符');
      if(this.runtime.active(id)){
        const state=Queue.enqueue(this.etaState(id),queuedMessage('queue-'+this.ports.uuid(),text,[],[],immediateInterrupt));
        this.queueState(id,state);if(immediateInterrupt)this.runtime.stop(id,undefined,false);
      }else{
        const edit=this.edits.get(id),target=edit?this.revisionTarget(id,edit.targetMessageId,'regenerate'):null;
        if(edit&&!target)throw new Error('要编辑的消息不存在');
        await this.submit(id,text,target?.turnId||'');this.edits.delete(id);
      }
      if(this.ports.drafts.message(id).text===text)this.ports.drafts.setMessage(id,'');
      this.inputVersion++;await this.flush();
    });
  }
  private async runSettled(id:string,stopped:boolean,saved:boolean):Promise<void>{
    if(!saved||!this.isForeground)return;
    const next=Queue.pollNextToSendAfterRun(this.etaState(id),stopped);this.queueState(id,next.state);
    if(next.item)await this.submitQueued(id,next.item);
  }
  private async submitQueued(id:string,item:QueuedMessageUi):Promise<void>{
    try{await this.submit(id,item.content);}
    catch(e){this.queueStates.set(id,[{...item,immediateInterrupt:false},...(this.queueStates.get(id)||[])]);this.error=e instanceof Error?e.message:'排队消息未发送';this.changed();}
  }
  async steerQueued(id:string):Promise<void>{await this.action(async()=>{
    const conversationId=this.ports.chats.selectedId,state=this.etaState(conversationId);
    const next=Queue.promoteToInterrupt(state,id);this.queueState(conversationId,next.state);
    if(!next.item)return;if(state.isStreaming)this.runtime.stop(conversationId,undefined,false);else await this.submitQueued(conversationId,next.item);
  });}
  recallQueued(id:string,currentDraft:string):void{
    if(this.busy)return;const conversationId=this.ports.chats.selectedId;
    const next=Queue.recallToDraft(this.etaState(conversationId),currentDraft,id);if(!next)return;
    if(next.input.length>8000){this.error='合并后的草稿超过 8000 字符，请先缩短输入内容';this.changed();return;}
    this.ports.drafts.setMessage(conversationId,next.input);this.queueState(conversationId,next);this.inputVersion++;this.changed();void this.flush();
  }
  removeQueued(id:string):void{if(this.busy)return;const conversationId=this.ports.chats.selectedId;this.queueState(conversationId,Queue.remove(this.etaState(conversationId),id));}
  private revisionTarget(id:string,messageId:string,kind:'delete'|'regenerate'):MessageMutation|null{
    const boundary=Revision.boundary(this.etaState(id),messageId);if(!boundary)return null;
    const turn=this.ports.chats.get(id)?.turns.find(t=>t.id+'-user'===boundary.userMessage.id);if(!turn)return null;
    return {conversationId:id,messageId,turnId:turn.id,laterTurns:boundary.laterTurnCount,kind};
  }
  editMessage(messageId:string):void {
    const id=this.ports.chats.selectedId;if(this.busy||this.runtime.active(id)||this.edits.has(id))return;
    const boundary=Revision.boundary(this.etaState(id),messageId);if(!boundary)return;
    this.edits.set(id,{targetMessageId:boundary.userMessage.id,previousInput:this.ports.drafts.message(id).text,previousImages:[],previousFileReferences:[],hasLaterTurns:boundary.laterTurnCount>0,preserveFollowingMessages:false});
    this.ports.drafts.setMessage(id,boundary.userMessage.content);this.inputVersion++;this.changed();
  }
  cancelMessageEdit():void{if(this.busy)return;const id=this.ports.chats.selectedId,edit=this.edits.get(id);if(!edit)return;this.ports.drafts.setMessage(id,edit.previousInput);this.edits.delete(id);this.inputVersion++;this.changed();void this.flush();}
  async requestMessageMutation(messageId:string,kind:'delete'|'regenerate'):Promise<void>{
    const id=this.ports.chats.selectedId;if(this.busy||this.runtime.active(id)||this.edits.has(id))return;
    this.mutation=this.revisionTarget(id,messageId,kind);this.changed();
    if(this.mutation?.kind==='regenerate'&&this.mutation.laterTurns===0)await this.confirmMessageMutation();
  }
  dismissMessageMutation():void{this.mutation=null;this.changed();}
  async confirmMessageMutation():Promise<void>{
    const target=this.mutation;if(!target)return;this.mutation=null;
    await this.action(async()=>{
      // A stale dialog can never mutate a conversation selected after the dialog opened.
      if(target.conversationId!==this.ports.chats.selectedId||this.runtime.active(target.conversationId))throw new Error('会话状态已改变，请重新选择消息');
      const fresh=this.revisionTarget(target.conversationId,target.messageId,target.kind);
      if(!fresh||fresh.turnId!==target.turnId||fresh.laterTurns!==target.laterTurns)throw new Error('消息状态已改变，请重新选择');
      if(target.kind==='regenerate'){
        const turn=this.ports.chats.get(target.conversationId)?.turns.find(t=>t.id===target.turnId);if(!turn)throw new Error('消息不存在');
        await this.submit(target.conversationId,turn.request,target.turnId);
      }else{
        const revised=Revision.deleteFromTurn(this.etaState(target.conversationId),target.messageId);if(!revised)throw new Error('消息不存在');
        await this.ports.chats.truncateFrom(target.conversationId,target.turnId);this.runtime.forget(target.conversationId);
        if(!this.ports.chats.selectedId)await this.ports.chats.create(this.ports.profiles.document.selectedId,'chat');this.inputVersion++;
      }
      this.mutation=null;
    });
  }
  stop(): void { const id=this.ports.chats.selectedId;this.queueState(id,Queue.clearInterruptFlags(this.etaState(id)));this.runtime.stop(id); }
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
    this.isForeground=value;
    if(!value)for(const id of this.queueStates.keys())this.queueState(id,Queue.clearInterruptFlags(this.etaState(id)));
    this.runtime.setForeground(value);
    if(!value) { this.pickerSession.cancel(); if(this.navigation.sheet==='models') {this.navigation.sheet='none';this.changed();} }
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
