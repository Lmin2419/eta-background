import { DataGate } from './dataGate';
import { TokenUsage, usageOf, sumUsage } from './usage';
import { Message, RunEvent } from './types';

export type ConversationMode = 'chat' | 'device';
export type TurnStatus = 'running' | 'completed' | 'stopped' | 'failed' | 'interrupted';
export type StepStatus = 'running' | 'completed' | 'failed' | 'interrupted';
export interface ChatStep { id: string; tool: string; status: StepStatus; startedAt: number; endedAt?: number; detail?:string }
export interface ChatTurn {
  id: string; request: string; reply: string; status: TurnStatus; mode: ConversationMode;
  modelLabel: string; startedAt: number; endedAt?: number; steps: ChatStep[]; notice: string; reasoning?:string;revisionOf?:string;attachments?:string[];contextNote?:string;usage?:TokenUsage;
}
export interface Conversation {
  id: string; title: string; createdAt: number; updatedAt: number;
  profileId: string; mode: ConversationMode; turns: ChatTurn[]; preferredModel?:string;
}
export interface ConversationSummary { id: string; title: string; preview: string; updatedAt: number; count: number }
export interface ConversationDocument { version: 1; selectedId: string; conversations: Conversation[] }
export interface ConversationStorage { read(): Promise<string>; write(value: string): Promise<void> }
export interface ConversationContext { messages: Message[]; omittedTurns: number }
export const CHAT_LIMITS = { conversations:50, turns:200, steps:240, request:8000, reply:32000, document:2_000_000, context:48000, contextTurns:20 };
export const TOOL_LABELS: Record<string,string> = {
  open_known_app:'打开已知应用',browser_snapshot:'网页截图',apps_list:'已知应用',system_status:'系统状态',contact_pick:'选择联系人',calendar_draft:'日历草稿',location_once:'获取位置',browser_open:'浏览器导航',browser_read:'读取网页',browser_click:'网页点击',browser_input:'网页输入',delegate_analysis:'次模型分析',skill_read_resource:'技能资源',
  file_list:'工作区文件',file_read:'读取文件',file_write:'保存文件',file_edit:'精确编辑',file_search:'搜索文件',memory_read:'读取记忆',memory_write:'保存记忆',time_now:'当前时间',web_fetch:'读取网页',web_search:'网页搜索',
  long_press:'长按位置',drag:'拖动对象',scroll:'滚动容器',wait_for:'等待目标',observe_screen:'观察页面', tap_element:'点击控件', tap:'点击位置', swipe:'滑动页面',
  input_text:'输入文字', back:'返回上一页', home:'返回桌面', launch_app:'打开应用', wait:'等待页面', unknown:'未识别的工具'
};
export function statusLabel(status: TurnStatus): string {
  return {running:'进行中',completed:'已完成',stopped:'已停止',failed:'未完成',interrupted:'已中断'}[status];
}
export function redactSecrets(text: string, secrets: string[]): string {
  for (const secret of secrets.filter(s => s.length > 0).sort((a,b) => b.length-a.length)) text = text.split(secret).join('[已隐藏密钥]');
  return text;
}
function clone<T>(value:T):T { return JSON.parse(JSON.stringify(value)); }
function isRecord(value:unknown):value is Record<string,unknown> { return Boolean(value) && typeof value==='object' && !Array.isArray(value); }
function string(value:unknown, max:number):string {
  if (typeof value!=='string' || value.length>max) throw new Error('会话文件格式无效，已保留原文件');
  return value;
}
function time(value:unknown):number {
  if (typeof value!=='number' || !Number.isFinite(value) || value<0) throw new Error('会话时间无效，已保留原文件');
  return value;
}
function mode(value:unknown):ConversationMode {
  if (value!=='chat' && value!=='device') throw new Error('会话模式无效，已保留原文件');
  return value;
}
function uniqueId(value:unknown, ids:Set<string>):string {
  const id=string(value,128);
  if (!id || ids.has(id)) throw new Error('会话记录 ID 无效，已保留原文件');
  ids.add(id);return id;
}
export function parseConversationDocument(raw:string):ConversationDocument {
  if (raw.length>CHAT_LIMITS.document) throw new Error('会话文件过大，已保留原文件');
  let value:unknown;
  try {value=JSON.parse(raw);} catch (_) {throw new Error('会话文件损坏，已保留原文件');}
  if (!isRecord(value) || value.version!==1 || !Array.isArray(value.conversations) || value.conversations.length>CHAT_LIMITS.conversations) throw new Error('不支持的会话文件，已保留原文件');
  const ids=new Set<string>();const conversations:Conversation[]=[];
  for (const c of value.conversations) {
    if (!isRecord(c) || !Array.isArray(c.turns) || c.turns.length>CHAT_LIMITS.turns) throw new Error('会话格式无效，已保留原文件');
    const turns:ChatTurn[]=[]; const turnIds=new Set<string>();
    for (const t of c.turns) {
      if (!isRecord(t) || !Array.isArray(t.steps) || t.steps.length>CHAT_LIMITS.steps || !['running','completed','stopped','failed','interrupted'].includes(String(t.status))) throw new Error('对话轮次无效，已保留原文件');
      const steps:ChatStep[]=[];const stepIds=new Set<string>();
      for (const s of t.steps) {
        if (!isRecord(s) || !['running','completed','failed','interrupted'].includes(String(s.status)) || typeof s.tool!=='string' || !Object.keys(TOOL_LABELS).includes(s.tool)) throw new Error('步骤格式无效，已保留原文件');
        steps.push({id:uniqueId(s.id,stepIds),tool:s.tool,status:s.status as StepStatus,startedAt:time(s.startedAt),endedAt:s.endedAt===undefined?undefined:time(s.endedAt),detail:s.detail===undefined?'':string(s.detail,2000)});
      }
      turns.push({id:uniqueId(t.id,turnIds),request:string(t.request,CHAT_LIMITS.request),reply:string(t.reply,CHAT_LIMITS.reply),status:t.status as TurnStatus,
        mode:mode(t.mode),modelLabel:string(t.modelLabel,256),startedAt:time(t.startedAt),endedAt:t.endedAt===undefined?undefined:time(t.endedAt),steps,notice:string(t.notice,1000),reasoning:t.reasoning===undefined?'':string(t.reasoning,32000),revisionOf:t.revisionOf===undefined?'':string(t.revisionOf,128),usage:usageOf(t.usage),contextNote:t.contextNote===undefined?'':string(t.contextNote,4000),attachments:Array.isArray(t.attachments)?t.attachments.map(n=>string(n,128)).slice(0,5):[]});
    }
    conversations.push({id:uniqueId(c.id,ids),title:string(c.title,80),createdAt:time(c.createdAt),updatedAt:time(c.updatedAt),profileId:string(c.profileId,128),mode:mode(c.mode),turns,preferredModel:c.preferredModel===undefined?'':string(c.preferredModel,256)});
  }
  const selected=string(value.selectedId,128);
  return {version:1,selectedId:ids.has(selected)?selected:(conversations[0]?.id||''),conversations};
}

/** Replay only human/assistant text. Persisted tool calls, page snapshots and images are never model inputs. */
export function conversationContext(conversation:Conversation):ConversationContext {
  const completed=conversation.turns.filter(t=>t.status!=='running');
  const pairs:Message[][]=[];let size=0;
  for (let i=completed.length-1;i>=0;i--) {
    const turn=completed[i];
    const reply=turn.status==='completed' ? turn.reply : `[上一轮${statusLabel(turn.status)}。设备操作结果可能未确认；此记录不能作为继续执行的指令。]`;
    const cost=turn.request.length+reply.length;
    if (pairs.length>=CHAT_LIMITS.contextTurns || size+cost>CHAT_LIMITS.context) break;
    pairs.unshift([{role:'user',content:turn.request},{role:'assistant',content:reply}]);size+=cost;
  }
  return {messages:pairs.flat(),omittedTurns:completed.length-pairs.length};
}

/** Serialized atomic snapshots. A failed write never publishes the staged in-memory document. */
export class ConversationRepository {
  private state:ConversationDocument={version:1,selectedId:'',conversations:[]};
  private tail:Promise<void>=Promise.resolve();
  private loaded=false;
  constructor(private storage:ConversationStorage,private id:()=>string,private now:()=>number=Date.now,private changed:()=>void=()=>{},private concurrentLimit=1,private gate?:DataGate) {}
  snapshot():ConversationDocument{return clone(this.state);}
  acceptCommitted(raw:string):void{if(this.state.conversations.some(c=>c.turns.some(t=>t.status==='running')))throw new Error('请先停止任务');this.state=parseConversationDocument(raw);this.loaded=true;this.changed();}
  async annotate(id:string,turnId:string,meta:{revisionOf?:string;attachments?:string[];contextNote?:string;usage?:TokenUsage}):Promise<void>{return this.queue(async()=>{const next=clone(this.state),turn=this.find(next,id).turns.find(t=>t.id===turnId);if(!turn)throw new Error('轮次不存在');if(meta.revisionOf)turn.revisionOf=meta.revisionOf;if(meta.attachments)turn.attachments=meta.attachments.slice(0,5);if(meta.usage)turn.usage=sumUsage(turn.usage,meta.usage);if(meta.contextNote)turn.contextNote=meta.contextNote.slice(0,4000);await this.commit(next);});}
  get selectedId():string {return this.state.selectedId;}
  get(id:string):Conversation|undefined {const c=this.state.conversations.find(c=>c.id===id);return c?clone(c):undefined;}
  list(query=''):ConversationSummary[] {
    const q=query.trim().toLocaleLowerCase();
    return this.state.conversations.filter(c=>!q || c.title.toLocaleLowerCase().includes(q) || c.turns.some(t=>(t.request+'\n'+t.reply).toLocaleLowerCase().includes(q)))
      .map(c=>({id:c.id,title:c.title,updatedAt:c.updatedAt,count:c.turns.length,preview:(c.turns[c.turns.length-1]?.request||'还没有消息').slice(0,100)}))
      .sort((a,b)=>b.updatedAt-a.updatedAt);
  }
  private queue<T>(fn:()=>Promise<T>):Promise<T> {
    const release=this.gate?.enter();
    const result=this.tail.then(fn);this.tail=result.then(()=>{},()=>{});return result.finally(()=>release?.());
  }
  private async commit(next:ConversationDocument):Promise<void> {
    const raw=JSON.stringify(next);
    if (raw.length>CHAT_LIMITS.document) throw new Error('会话存储已满，请导出并删除不需要的会话后重试');
    await this.storage.write(raw);this.state=next;
    try {this.changed();} catch (_) { /* View errors cannot roll back durable state. */ }
  }
  async load():Promise<number> {
    return this.queue(async()=>{
      if (this.loaded) throw new Error('会话仓库不能重复加载');
      const raw=await this.storage.read();const next=raw?parseConversationDocument(raw):clone(this.state);let interrupted=0;
      for (const c of next.conversations) for (const t of c.turns) if (t.status==='running') {
        t.status='interrupted';t.endedAt=this.now();t.notice='上次运行已中断，未自动恢复操作。继续前请先确认手机当前状态。';
        for (const s of t.steps) if (s.status==='running') {s.status='interrupted';s.endedAt=this.now();}
        c.updatedAt=this.now();interrupted++;
      }
      if (interrupted) await this.commit(next); else this.state=next;
      this.loaded=true;return interrupted;
    });
  }
  private requireLoaded():void {if (!this.loaded) throw new Error('会话尚未成功载入');}
  private find(next:ConversationDocument,id:string):Conversation {
    const c=next.conversations.find(c=>c.id===id);if(!c) throw new Error('会话不存在');return c;
  }
  private idle(c:Conversation):void {if(c.turns.some(t=>t.status==='running')) throw new Error('请先停止正在运行的任务');}
  async create(profileId='',requestedMode:ConversationMode='chat'):Promise<string> {
    return this.queue(async()=>{
      this.requireLoaded();if(this.state.conversations.length>=CHAT_LIMITS.conversations) throw new Error('最多保留 50 个会话，请先导出或删除旧会话');
      const next=clone(this.state),id=this.id(),now=this.now();
      next.conversations.push({id,title:'新会话',createdAt:now,updatedAt:now,profileId,mode:requestedMode,turns:[]});next.selectedId=id;
      await this.commit(next);return id;
    });
  }
  async select(id:string):Promise<void> {return this.queue(async()=>{this.requireLoaded();const next=clone(this.state);this.find(next,id);next.selectedId=id;await this.commit(next);});}
  async rename(id:string,title:string):Promise<void> {
    return this.queue(async()=>{this.requireLoaded();if(!title.trim()) throw new Error('会话名称不能为空');const next=clone(this.state),c=this.find(next,id);this.idle(c);c.title=title.trim().slice(0,80);await this.commit(next);});
  }
  async remove(id:string):Promise<void> {
    return this.queue(async()=>{this.requireLoaded();const next=clone(this.state);this.idle(this.find(next,id));next.conversations=next.conversations.filter(c=>c.id!==id);if(next.selectedId===id) next.selectedId=next.conversations[0]?.id||'';await this.commit(next);});
  }
  async preferences(id:string,profileId:string,requestedMode:ConversationMode,preferredModel?:string):Promise<void> {
    return this.queue(async()=>{this.requireLoaded();const next=clone(this.state),c=this.find(next,id);this.idle(c);c.profileId=profileId;c.mode=requestedMode;if(preferredModel!==undefined)c.preferredModel=preferredModel.slice(0,256);await this.commit(next);});
  }
  async begin(id:string,request:string,modelLabel:string,profileId:string,requestedMode:ConversationMode,runId='',reviseFromTurnId=''):Promise<{turnId:string;context:ConversationContext}> {
    return this.queue(async()=>{
      this.requireLoaded();if(!request.trim() || request.length>CHAT_LIMITS.request) throw new Error('消息不能为空，且最多 8000 字符');
      if(this.state.conversations.reduce((n,c)=>n+c.turns.filter(t=>t.status==='running').length,0)>=this.concurrentLimit) throw new Error('已有任务运行中或等待恢复，请先停止');
      const next=clone(this.state),c=this.find(next,id);this.idle(c);
      if(runId && c.turns.some(t=>t.id===runId))throw new Error('运行已经接纳，拒绝重复执行');
      // Revision and admission are one durable write. A failed write leaves the old history intact.
      if(reviseFromTurnId){const index=c.turns.findIndex(t=>t.id===reviseFromTurnId);if(index<0)throw new Error('要修订的轮次不存在');c.turns=c.turns.slice(0,index);}
      if(c.turns.length>=CHAT_LIMITS.turns) throw new Error('当前会话已达 200 轮，请新建会话');
      const context=conversationContext(c),turnId=runId||this.id(),now=this.now();
      if(c.turns.some(t=>t.id===turnId))throw new Error('运行已经接纳，拒绝重复执行');
      c.turns.push({id:turnId,request:request.trim(),reply:'',status:'running',mode:requestedMode,modelLabel:modelLabel.slice(0,256),startedAt:now,steps:[],notice:'',...(reviseFromTurnId?{revisionOf:reviseFromTurnId}:{})});
      if(c.turns.length===1 && c.title==='新会话') c.title=request.trim().replace(/\s+/g,' ').slice(0,32);
      c.profileId=profileId;c.mode=requestedMode;c.updatedAt=now;if(!runId)next.selectedId=id;
      await this.commit(next);return {turnId,context};
    });
  }
  async truncateFrom(id:string,turnId:string):Promise<void>{
    return this.queue(async()=>{this.requireLoaded();const next=clone(this.state),c=this.find(next,id);this.idle(c);
      const index=c.turns.findIndex(t=>t.id===turnId);if(index<0)throw new Error('要删除的轮次不存在');
      if(index===0){next.conversations=next.conversations.filter(v=>v.id!==id);if(next.selectedId===id)next.selectedId=next.conversations[0]?.id||'';}
      else {c.turns=c.turns.slice(0,index);c.updatedAt=this.now();}await this.commit(next);
    });
  }
  async step(id:string,turnId:string,event:RunEvent):Promise<void> {
    return this.queue(async()=>{
      this.requireLoaded();const next=clone(this.state),c=this.find(next,id),t=c.turns.find(t=>t.id===turnId);
      if(!t || t.status!=='running') throw new Error('运行记录已经结束，拒绝迟到步骤');
      if(!event.stepId || !event.toolName || !['tool','result','error'].includes(event.kind)) throw new Error('步骤事件无效');
      const tool=Object.keys(TOOL_LABELS).includes(event.toolName)?event.toolName:'unknown';
      const found=t.steps.find(s=>s.id===event.stepId);
      if(event.kind==='tool') {
        if(found || t.steps.length>=CHAT_LIMITS.steps) throw new Error('步骤重复或超过上限');
        t.steps.push({id:event.stepId,tool,status:'running',startedAt:this.now(),detail:(event.detail||'').slice(0,500)});
      } else {
        if(!found || found.status!=='running' || found.tool!==tool) throw new Error('步骤结果与开始记录不匹配');
        found.status=event.kind==='result'?'completed':'failed';found.endedAt=this.now();found.detail=[found.detail,event.detail||''].filter(Boolean).join('\n').slice(0,2000);
      }
      c.updatedAt=this.now();await this.commit(next);
    });
  }
  async progress(id:string,turnId:string,reply:string,reasoning?:string):Promise<void> {
    return this.queue(async()=>{
      this.requireLoaded();const next=clone(this.state),c=this.find(next,id),t=c.turns.find(t=>t.id===turnId);
      if(!t||t.status!=='running')throw new Error('运行记录已经结束，拒绝迟到回复');
      t.reply=reply.slice(0,CHAT_LIMITS.reply);if(reasoning!==undefined)t.reasoning=reasoning.slice(0,32000);await this.commit(next);
    });
  }
  async finish(id:string,turnId:string,status:Exclude<TurnStatus,'running'|'interrupted'>,reply='',notice=''):Promise<void> {
    return this.queue(async()=>{
      this.requireLoaded();const next=clone(this.state),c=this.find(next,id),t=c.turns.find(t=>t.id===turnId);
      if(!t || t.status!=='running') throw new Error('运行记录已经结束，拒绝迟到回复');
      t.status=status;t.reply=reply.slice(0,CHAT_LIMITS.reply);t.notice=notice.slice(0,1000);t.endedAt=this.now();
      if(reply.length>CHAT_LIMITS.reply) t.notice='回复过长，历史只保留前 32000 字符。';
      for(const s of t.steps) if(s.status==='running') {s.status='interrupted';s.endedAt=this.now();}
      c.updatedAt=this.now();await this.commit(next);
    });
  }
  exportMarkdown(id:string):string {
    const c=this.get(id);if(!c) throw new Error('会话不存在');this.idle(c);
    const lines=['# '+c.title,'','导出时间：'+new Date(this.now()).toISOString(),'','步骤记录不包含原始页面、截图或工具参数。模型回复可能包含它引用的页面内容。',''];
    for(const t of c.turns) {
      lines.push('## 用户', '', t.request, '', '## 助手 · '+statusLabel(t.status), '', t.reply||'（没有完整回复）', '');
      if(t.notice) lines.push('说明：'+t.notice,'');
      if(t.steps.length) {lines.push('### 执行步骤','');for(const s of t.steps) lines.push('- '+TOOL_LABELS[s.tool]+'：'+({running:'进行中',completed:'执行返回',failed:'失败',interrupted:'结果未确认'}[s.status]));lines.push('');}
      lines.push('模型：'+t.modelLabel+'；模式：'+(t.mode==='chat'?'聊天':'操作手机'),'');
    }
    return lines.join('\n');
  }
}
