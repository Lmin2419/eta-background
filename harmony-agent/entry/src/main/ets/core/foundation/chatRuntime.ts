import { ConversationRepository, redactSecrets } from '../conversations';
import { ConversationRun } from '../conversationRun';
import { ProfileRepository } from '../profiles';
import { HttpTransport, OpenAIClient } from '../model';
import { AgentLoop } from '../loop';
import { ChatDevice, RunEvent } from '../types';

export interface ChatActivity {
  id: string; conversationId: string; text: string; model: string;
  reply: string; reasoning: string; phase: string; error: string; active: boolean;
}
interface Execution { activity: ChatActivity; run: ConversationRun; done: Promise<void> }

/** The rebuilt shell intentionally has a text-only runtime. No legacy extension is initialized. */
export class FoundationChatRuntime {
  private executions = new Map<string, Execution>();
  private foreground = true;
  constructor(private chats: ConversationRepository, private profiles: ProfileRepository,
    private transport: () => HttpTransport, private uuid: () => string, private changed: () => void) {}
  activity(id: string): ChatActivity | undefined {
    const a = this.executions.get(id)?.activity; return a ? { ...a } : undefined;
  }
  active(id?: string): boolean {
    return id ? Boolean(this.executions.get(id)?.activity.active) : [...this.executions.values()].some(e => e.activity.active);
  }
  start(conversationId: string, text: string, profileId: string, model: string): string {
    if (!this.foreground) throw new Error('请返回助手后发送');
    if (this.active(conversationId)) throw new Error('本会话正在回复，请先停止或等待完成');
    if ([...this.executions.values()].filter(e => e.activity.active).length >= 2) throw new Error('已有两个会话正在回复，请先等待其中一个完成');
    if (!this.chats.get(conversationId)) throw new Error('会话不存在');
    if (!text.trim() || text.length > 8000) throw new Error('请输入消息，最多 8000 字符');
    const profile = this.profiles.document.profiles.find(p => p.id === profileId);
    if (!profile || !model.trim()) throw new Error('请先添加服务并选择模型');
    const snapshot = { ...profile, model, extraHeaders: { ...profile.extraHeaders } };
    const secrets: string[] = [];
    const activity: ChatActivity = { id: this.uuid(), conversationId, text, model, reply: '', reasoning: '', phase: '正在准备', error: '', active: true };
    let loop: AgentLoop | undefined, stopped = false;
    const run = new ConversationRun(this.chats, {
      runId: activity.id, conversationId, text, modelLabel: model, profileId, mode: 'chat', secrets
    }, (context, journal) => ({
      stop: () => { stopped = true; loop?.stop(); },
      run: async () => {
        // The user turn is durable before even the secret lookup or a network request begins.
        const apiKey = await this.profiles.key(snapshot); secrets.push(apiKey);
        if (stopped) throw new Error('CANCELLED');
        loop = new AgentLoop(new OpenAIClient({ ...snapshot, apiKey, screenshots: false }, this.transport(), []),
          new ChatDevice(), (event: RunEvent) => {
            if (stopped) return;
            const clean = redactSecrets(event.text, secrets);
            if (event.kind === 'stream') { activity.reply = clean; run.capture(clean); }
            else if (event.kind === 'reasoning') { activity.reasoning = clean; run.capture(clean, true); }
            else activity.phase = clean;
            this.changed();
          }, { allowDevice: false, allowedTools: [], history: context.messages, omittedTurns: context.omittedTurns, journal });
        return loop.run(text);
      }
    }));
    const execution: Execution = { activity, run, done: Promise.resolve() };
    this.executions.set(conversationId, execution); this.changed();
    execution.done = Promise.resolve().then(async () => {
      try { activity.reply = await run.run(); activity.phase = '已完成'; }
      catch (e) { activity.error = redactSecrets(e instanceof Error ? e.message : '回复未完成', secrets); activity.phase = activity.error; }
      finally { activity.active = false; this.changed(); }
    });
    return activity.id;
  }
  stop(id: string, reason = '已停止生成，收到的内容已保留'): void {
    const execution = this.executions.get(id);
    if (execution?.activity.active) { execution.activity.phase = '正在停止…'; execution.run.stop(reason); this.changed(); }
  }
  setForeground(value: boolean): void {
    this.foreground = value;
    if (!value) for (const id of this.executions.keys()) this.stop(id, '助手已进入后台，本轮停止；返回后可继续提问');
  }
  async idle(): Promise<void> { await Promise.all([...this.executions.values()].map(e => e.done)); }
  forget(id: string): void { if (!this.active(id)) this.executions.delete(id); }
}
