// Ported runtime bridge from app/src/main/kotlin/io/github/mangi/eta/ui/app/AgentRunStore.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { FoundationRunProjection } from '../eta/foundationRunProjection';
import { AgentChatMessageUi } from '../eta/model/conversation';
import { ConversationRepository, redactSecrets } from '../conversations';
import { ConversationRun } from '../conversationRun';
import { ProfileRepository, modelCatalog } from '../profiles';
import { HttpTransport, OpenAIClient } from '../model';
import { AgentLoop } from '../loop';
import { ChatDevice, RunEvent } from '../types';
import { DiagnosticLog } from '../diagnostics';
import { implementedProtocol } from '../eta/protocol/foundationProviderBridge';

export interface ChatActivity {
  id: string; conversationId: string; text: string; model: string;
  reply: string; reasoning: string; phase: string; error: string; active: boolean;
}
interface Execution { activity: ChatActivity; run: ConversationRun; done: Promise<void>; admitted:Promise<void>; projection:FoundationRunProjection; stoppedByUser:boolean }

/** The rebuilt shell intentionally has a text-only runtime. No legacy extension is initialized. */
export class FoundationChatRuntime {
  private executions = new Map<string, Execution>();
  private foreground = true;
  constructor(private chats: ConversationRepository, private profiles: ProfileRepository,
    private transport: () => HttpTransport, private uuid: () => string, private changed: () => void,
    private diagnostics?: DiagnosticLog, private clock:()=>number=()=>0,
    private settled:(id:string,stoppedByUser:boolean,saved:boolean)=>Promise<void>=async()=>{}) {}
  activity(id: string): ChatActivity | undefined {
    const a = this.executions.get(id)?.activity; return a ? { ...a } : undefined;
  }
  active(id?: string): boolean {
    return id ? Boolean(this.executions.get(id)?.activity.active) : [...this.executions.values()].some(e => e.activity.active);
  }
  start(conversationId: string, text: string, profileId: string, model: string, reviseFromTurnId = ''): string {
    if (!this.foreground) throw new Error('请返回助手后发送');
    if (this.active(conversationId)) throw new Error('本会话正在回复，请先停止或等待完成');
    if ([...this.executions.values()].filter(e => e.activity.active).length >= 2) throw new Error('已有两个会话正在回复，请先等待其中一个完成');
    if (!this.chats.get(conversationId)) throw new Error('会话不存在');
    if (!text.trim() || text.length > 8000) throw new Error('请输入消息，最多 8000 字符');
    const profile = this.profiles.document.profiles.find(p => p.id === profileId);
    if (!profile || profile.isEnabled===false || profile.metadata?.find(m=>m.id===model)?.enabled===false || !model.trim() || !modelCatalog(profile.models,profile.model).includes(model)) throw new Error('请先添加服务并选择模型');
    if(!implementedProtocol(profile.protocol))throw Error('鸿蒙版尚未接入此协议');
    const snapshot = { ...profile, model, extraHeaders: { ...profile.extraHeaders } };
    const secrets: string[] = [];
    const activity: ChatActivity = { id: this.uuid(), conversationId, text, model, reply: '', reasoning: '', phase: '正在准备', error: '', active: true };
    let loop: AgentLoop | undefined, stopped = false, admitted = false;
    let resolveAdmission:()=>void=()=>{},rejectAdmission:(error:Error)=>void=()=>{};
    const admission=new Promise<void>((resolve,reject)=>{resolveAdmission=resolve;rejectAdmission=reject;});
    void admission.catch(()=>{});
    const projection=new FoundationRunProjection(activity.id,this.clock,this.changed);
    const run = new ConversationRun(this.chats, {
      runId: activity.id, conversationId, text, modelLabel: model, profileId, mode: 'chat', secrets, reviseFromTurnId,
      onAdmitted:()=>{admitted=true;resolveAdmission();}
    }, (context, journal) => ({
      stop: () => { stopped = true; loop?.stop(); },
      run: async () => {
        // The user turn is durable before even the secret lookup or a network request begins.
        const apiKey = await this.profiles.key(snapshot); secrets.push(apiKey,...Object.values(snapshot.extraHeaders||{}));
        if (stopped) throw new Error('CANCELLED');
        loop = new AgentLoop(new OpenAIClient({ ...snapshot, apiKey, screenshots: false, systemPrompt:snapshot.systemPrompt,sessionId:conversationId,providerId:profileId }, this.transport(), []),
          new ChatDevice(), (event: RunEvent) => {
            if (stopped) return;
            const clean = redactSecrets(event.text, secrets);
            if (event.kind === 'stream') { activity.reply = clean; run.capture(clean); projection.update('TEXT',clean); }
            else if (event.kind === 'reasoning') { activity.reasoning = clean; run.capture(clean, true); projection.update('THINKING',clean); }
            else activity.phase = clean;
            if(event.kind!=='stream'&&event.kind!=='reasoning')this.changed();
          }, { allowDevice: false, allowedTools: [], history: context.messages, omittedTurns: context.omittedTurns, journal });
        return loop.run(text);
      }
    }));
    const execution: Execution = { activity, run, done: Promise.resolve(), admitted:admission, projection, stoppedByUser:false };
    this.executions.set(conversationId, execution); this.changed();
    const started = Date.now();
    // Only the model name, timing and size are logged; never the question or the reply.
    this.diagnostics?.record('chat', '开始回复', '模型 ' + model + ' · 流式 ' + (snapshot.streaming === false ? '关' : '开'));
    execution.done = Promise.resolve().then(async () => {
      try {
        activity.reply = await run.run(); projection.update('TEXT',activity.reply); activity.phase = '已完成';
        this.diagnostics?.record('chat', '回复完成', (Date.now() - started) + 'ms · ' + activity.reply.length + ' 字');
      } catch (e) {
        activity.error = redactSecrets(e instanceof Error ? e.message : '回复未完成', secrets); activity.phase = activity.error;
        this.diagnostics?.record('chat', '回复未完成', activity.error + ' · ' + (Date.now() - started) + 'ms · 已收到 ' + activity.reply.length + ' 字', 400);
      }
      finally {
        projection.finish();activity.active = false;
        if(!admitted)rejectAdmission(new Error(activity.error||'本轮未保存'));
        this.changed();
        const turn=this.chats.get(conversationId)?.turns.find(t=>t.id===activity.id);
        await this.settled(conversationId,execution.stoppedByUser,Boolean(turn&&turn.status!=='running'));
      }
    });
    return activity.id;
  }
  stop(id: string, reason = '已停止生成，收到的内容已保留', byUser=true): void {
    const execution = this.executions.get(id);
    if (execution?.activity.active) { execution.stoppedByUser=execution.stoppedByUser||byUser; execution.activity.phase = '正在停止…'; execution.run.stop(reason); this.changed(); }
  }
  setForeground(value: boolean): void {
    this.foreground = value;
    if (!value) for (const id of this.executions.keys()) this.stop(id, '助手已进入后台，本轮停止；返回后可继续提问');
  }
  async admitted(id:string):Promise<void>{const e=this.executions.get(id);if(!e)throw new Error('运行不存在');await e.admitted;}
  projection(id:string):AgentChatMessageUi[]{return this.executions.get(id)?.projection.messages()||[];}
  async idleConversation(id:string):Promise<void>{await this.executions.get(id)?.done;}
  async idle(): Promise<void> {
    let previous:Promise<void>[]=[];
    while(true){const pending=[...this.executions.values()].map(e=>e.done);if(pending.length===previous.length&&pending.every((p,i)=>p===previous[i]))return;previous=pending;await Promise.all(pending);}
  }
  forget(id: string): void { if (!this.active(id)) this.executions.delete(id); }
}
