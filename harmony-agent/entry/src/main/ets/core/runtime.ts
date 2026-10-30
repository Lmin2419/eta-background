import { ConversationMode, ConversationStorage } from './conversations';

export type TaskState = 'queued'|'preparing'|'running'|'paused'|'cancelling'|'completed'|'failed'|'cancelled'|'interrupted';
export interface TaskRecord {
  id:string; conversationId:string; text:string; modelLabel:string; profileId:string; mode:ConversationMode;
  state:TaskState; phase:string; reason:string; createdAt:number; updatedAt:number; seq:number;
  held:boolean; token:string; notificationId:number; dismissed:boolean; posted:boolean; alerted:boolean;
  sourceRunId:string; continuationId:string; targetBundle:string; targetAbility:string;resultSummary?:string; attachments?:string[];revisionOf?:string;
}
export interface TaskDocument {version:1; nextNotificationId:number; tasks:TaskRecord[]}
export interface TaskRequest {conversationId:string;text:string;modelLabel:string;profileId:string;mode:ConversationMode;sourceRunId?:string;attachments?:string[];revisionOf?:string}
export function terminal(state:TaskState):boolean {return ['completed','failed','cancelled','interrupted'].includes(state);}
export function taskLabel(state:TaskState):string {return {queued:'排队中',preparing:'准备中',running:'执行中',paused:'已暂停',cancelling:'停止中',completed:'已完成',failed:'未完成',cancelled:'已停止',interrupted:'已中断'}[state];}
function copy<T>(v:T):T{return JSON.parse(JSON.stringify(v));}
const transitions:Record<TaskState,TaskState[]>={queued:['preparing','cancelled','interrupted'],preparing:['running','paused','cancelling','failed','cancelled','interrupted'],running:['paused','cancelling','completed','failed','cancelled','interrupted'],paused:['running','cancelling','completed','failed','cancelled','interrupted'],cancelling:['cancelled','failed','interrupted'],completed:[],failed:[],cancelled:[],interrupted:[]};

/** Durable task identities and a serial writer. No credentials, live sockets or UI state are stored here. */
export class TaskLedger {
  private document:TaskDocument={version:1,nextNotificationId:4100,tasks:[]};
  private tail:Promise<void>=Promise.resolve();
  private ready=false;
  constructor(private storage:ConversationStorage,private uuid:()=>string,private changed:()=>void=()=>{},private now:()=>number=Date.now){}
  get nextNotificationId():number{return this.document.nextNotificationId;}
  acceptEmptyCommitted(next:number):void{if(this.document.tasks.some(t=>!terminal(t.state)))throw new Error('仍有未结束任务');this.document={version:1,nextNotificationId:next,tasks:[]};this.ready=true;}
  list():TaskRecord[]{return copy(this.document.tasks);}
  get(id:string):TaskRecord|undefined {const r=this.document.tasks.find(t=>t.id===id);return r?copy(r):undefined;}
  private serial<T>(fn:()=>Promise<T>):Promise<T>{const p=this.tail.then(fn);this.tail=p.then(()=>{},()=>{});return p;}
  private async commit(doc:TaskDocument):Promise<void>{await this.storage.write(JSON.stringify(doc));this.document=doc;try{this.changed();}catch(_) {}}
  async load():Promise<number>{return this.serial(async()=>{
    if(this.ready)return 0;
    const raw=await this.storage.read();let doc:TaskDocument=this.document;
    if(raw){
      if(raw.length>4_000_000)throw new Error('任务记录超过上限，原文件已保留');
      const parsed=JSON.parse(raw) as TaskDocument;
      if(parsed.version!==1||!Array.isArray(parsed.tasks)||parsed.tasks.length>300||!Number.isInteger(parsed.nextNotificationId)||parsed.nextNotificationId<4100)throw new Error('任务记录版本或格式无效，原文件已保留');
      const ids=new Set<string>(),notices=new Set<number>();
      for(const t of parsed.tasks){
        if(!t||typeof t.id!=='string'||!t.id||ids.has(t.id)||typeof t.conversationId!=='string'||!t.conversationId||typeof t.text!=='string'||t.text.length>8000||!Object.keys(transitions).includes(t.state)||typeof t.token!=='string'||!t.token||!Number.isInteger(t.notificationId)||notices.has(t.notificationId)||typeof t.seq!=='number'||!Number.isFinite(t.createdAt)||!Number.isFinite(t.updatedAt)||!['chat','device'].includes(t.mode))throw new Error('任务记录损坏，原文件已保留');
        ids.add(t.id);notices.add(t.notificationId);
      }
      doc=parsed;
    }
    let interrupted=0;
    for(const t of doc.tasks)if(!terminal(t.state)){t.state='interrupted';t.reason='上次进程已结束，没有自动恢复操作';t.phase='等待你确认后继续';t.held=true;t.seq++;t.updatedAt=this.now();interrupted++;}
    if(interrupted)await this.commit(doc);else this.document=doc;
    this.ready=true;return interrupted;
  });}
  async admit(request:TaskRequest):Promise<TaskRecord>{return this.serial(async()=>{
    if(!this.ready)throw new Error('任务记录尚未载入');
    if(!request.text.trim()||request.text.length>8000)throw new Error('消息不能为空，且最多 8000 字符');
    const doc=copy(this.document);
    const source=request.sourceRunId?doc.tasks.find(t=>t.id===request.sourceRunId):undefined;
    if(request.sourceRunId&&(!source||source.conversationId!==request.conversationId||!terminal(source.state)))throw new Error('继续对话的源任务不存在或尚未结束');
    if(source?.continuationId){const prior=doc.tasks.find(t=>t.id===source.continuationId);if(prior)return copy(prior);throw new Error('此通知已经用于继续对话，请打开会话');}
    if(doc.tasks.filter(t=>!terminal(t.state)).length>=8)throw new Error('最多保留 8 个未结束任务，请先完成或取消队列');
    // Retain recent results and action tokens without letting the ledger grow unbounded.
    if(doc.tasks.length>=256)doc.tasks=doc.tasks.filter(t=>!terminal(t.state)).concat(doc.tasks.filter(t=>terminal(t.state)).slice(-200));
    const at=this.now();
    const record:TaskRecord={id:this.uuid(),conversationId:request.conversationId,text:request.text.trim(),modelLabel:request.modelLabel.slice(0,256),profileId:request.profileId,mode:request.mode,state:'queued',phase:'等待执行',reason:'',createdAt:at,updatedAt:at,seq:0,held:false,token:this.uuid(),notificationId:doc.nextNotificationId++,dismissed:false,posted:false,alerted:false,sourceRunId:request.sourceRunId||'',continuationId:'',targetBundle:'',targetAbility:''};
      if(doc.nextNotificationId>=200_000_000)throw new Error('通知编号已达上限');
    if(source)source.continuationId=record.id;
    if(request.revisionOf)record.revisionOf=request.revisionOf.slice(0,128);
    if(request.attachments){if(request.attachments.length>5||request.attachments.some(n=>typeof n!=='string'||n.length>128||/[\\/\0]/.test(n)))throw new Error('附件引用无效');record.attachments=request.attachments.slice();}
    doc.tasks.push(record);await this.commit(doc);return copy(record);
  });}
  async update(id:string,change:(task:TaskRecord)=>void):Promise<TaskRecord>{return this.serial(async()=>{
    const doc=copy(this.document),record=doc.tasks.find(t=>t.id===id);if(!record)throw new Error('任务不存在');
    const previous=record.state;change(record);
    if(record.state!==previous&&!transitions[previous].includes(record.state))throw new Error('拒绝过期任务状态更新：'+previous+' → '+record.state);
    record.updatedAt=this.now();record.seq++;await this.commit(doc);return copy(record);
  });}
  async holdConversation(id:string):Promise<void>{return this.serial(async()=>{
    const doc=copy(this.document);for(const r of doc.tasks)if(r.conversationId===id&&r.state==='queued')r.held=true;await this.commit(doc);
  });}
  async releaseQueue(id:string):Promise<void>{return this.serial(async()=>{
    const doc=copy(this.document);for(const r of doc.tasks)if(r.conversationId===id&&r.state==='queued')r.held=false;await this.commit(doc);
  });}
  async prioritize(id:string):Promise<void>{return this.serial(async()=>{
    const doc=copy(this.document),t=doc.tasks.find(r=>r.id===id);if(!t||t.state!=='queued')throw new Error('只能调整排队消息');
    t.held=false;doc.tasks=doc.tasks.filter(r=>r.id!==id);doc.tasks.unshift(t);await this.commit(doc);
  });}
  validAction(id:string,token:string):TaskRecord|undefined {const r=this.get(id);return r&&token.length>=16&&r.token===token?r:undefined;}
}

export interface ManagedTask {run():Promise<void>;stop(reason:string):void;pause(reason:string):void;resume():Promise<void>;steer(text:string):void}
export type TaskFactory=(record:TaskRecord)=>ManagedTask;
/** Admission and execution are separate: queued tasks own no model/device resources. */
export class TaskScheduler {
  private active:Map<string,ManagedTask>=new Map();
  private drainTail:Promise<void>=Promise.resolve();
  private starts:Map<string,Promise<void>>=new Map();
  private pausePending:Map<string,string>=new Map();
  private stopPending:Map<string,string>=new Map();
  private stoppingConversations:Set<string>=new Set();
  constructor(readonly ledger:TaskLedger,private factory:TaskFactory,private warning:(message:string)=>void=()=>{},private limit=2){}
  get activeCount():number{return this.starts.size;}
  kick():void {
    this.drainTail=this.drainTail.then(async()=>{
      for(const r of this.ledger.list()){
        if(this.starts.size>=this.limit)break;
        if(r.state!=='queued'||r.held||this.stoppingConversations.has(r.conversationId))continue;
        const occupants=Array.from(this.starts.keys()).map(id=>this.ledger.get(id)).filter((t):t is TaskRecord=>Boolean(t));
        if(occupants.some(t=>t.conversationId===r.conversationId||(r.mode==='device'&&t.mode==='device')))continue;
        // Reserve synchronously, before the first durable transition yields.
        const execution=Promise.resolve().then(()=>this.execute(r.id));this.starts.set(r.id,execution);
        void execution.finally(()=>{this.starts.delete(r.id);this.active.delete(r.id);this.pausePending.delete(r.id);this.stopPending.delete(r.id);this.kick();});
      }
    }).catch(e=>{this.warning((e as Error).message);});
  }
  private async execute(id:string):Promise<void>{
    try {
      let r=this.ledger.get(id);if(!r||r.state!=='queued')return;
      r=await this.ledger.update(id,t=>{t.state='preparing';t.phase='校验配置与资源';});
      if(this.stopPending.has(id))throw new Error('CANCELLED');
      const worker=this.factory(r);this.active.set(id,worker);
      r=await this.ledger.update(id,t=>{t.state='running';t.phase='正在执行';});
      if(this.stopPending.has(id)){worker.stop(this.stopPending.get(id)!);throw new Error('CANCELLED');}
      const reason=this.pausePending.get(id);if(reason){worker.pause(reason);await this.ledger.update(id,t=>{t.state='paused';t.reason=reason;});}
      await worker.run();
      const latest=this.ledger.get(id);
      if(latest&&!terminal(latest.state))await this.ledger.update(id,t=>{t.state=this.stopPending.has(id)?'cancelled':'completed';t.phase=taskLabel(t.state);});
    }catch(e){
      this.active.get(id)?.stop('任务执行结束');
      const latest=this.ledger.get(id);
      if(latest&&!terminal(latest.state))try{await this.ledger.update(id,t=>{t.state=this.stopPending.has(id)?'cancelled':'failed';t.reason=this.stopPending.get(id)||(e as Error).message;t.phase=taskLabel(t.state);});}catch(_){this.warning('任务已停止，但终态保存失败；重启后会标为中断');}
    }
  }
  async stop(id:string,reason='用户已停止任务',holdQueue=true):Promise<void>{
    const r=this.ledger.get(id);if(!r||terminal(r.state))return;
    this.stoppingConversations.add(r.conversationId);this.stopPending.set(id,reason);
    this.active.get(id)?.stop(reason); // Synchronous cancellation before any storage await.
    try{
      if(holdQueue)await this.ledger.holdConversation(r.conversationId);
      const current=this.ledger.get(id);if(current&&!terminal(current.state))await this.ledger.update(id,t=>{if(!terminal(t.state)){t.state=t.state==='queued'?'cancelled':'cancelling';t.reason=reason;t.phase='正在停止';}});
    }finally{this.stoppingConversations.delete(r.conversationId);this.kick();}
  }
  async pause(id:string,reason='用户暂停'):Promise<void>{
    const r=this.ledger.get(id);if(!r||terminal(r.state)||r.state==='cancelling'||r.state==='queued')return;
    this.pausePending.set(id,reason);this.active.get(id)?.pause(reason);
    await this.ledger.update(id,t=>{if(!terminal(t.state)&&t.state!=='cancelling'){t.state='paused';t.reason=reason;t.phase='暂停：'+reason;}});
  }
  async resume(id:string):Promise<void>{
    const r=this.ledger.get(id);if(!r)return;
    if(r.state==='queued'){await this.ledger.releaseQueue(r.conversationId);this.kick();return;}
    if(r.state!=='paused')throw new Error('此任务不能直接继续；请在会话中发送新消息');
    const worker=this.active.get(id);if(!worker)throw new Error('运行进程已结束，请在会话中继续');
    await worker.resume();this.pausePending.delete(id);
    await this.ledger.update(id,t=>{if(t.state==='paused'){t.state='running';t.reason='';t.phase='重新观察后继续';}});
  }
  steer(id:string,text:string):void{if(!text.trim()||text.length>8000)throw new Error('补充内容不能为空且最多 8000 字符');const worker=this.active.get(id);if(!worker)throw new Error('任务尚未开始');worker.steer(text);}
  async interrupt(queuedId:string):Promise<void>{const r=this.ledger.get(queuedId);if(!r||r.state!=='queued')throw new Error('消息不在队列中');await this.ledger.prioritize(queuedId);for(const [id] of this.starts){if(this.ledger.get(id)?.conversationId===r.conversationId)await this.stop(id,'已插话，旧任务停止后开始新消息',false);}this.kick();}
  async idle():Promise<void>{await this.drainTail;while(this.starts.size){await Promise.all(Array.from(this.starts.values()));await this.drainTail;}}
}
