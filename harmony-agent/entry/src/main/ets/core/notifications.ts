import { TaskLedger, TaskRecord, TaskState, terminal, taskLabel } from './runtime';

export interface NoticeAction {name:string;label:string;input:boolean}
export interface NoticeView {id:number;runId:string;conversationId:string;token:string;title:string;text:string;terminal:boolean;actions:NoticeAction[];updateOnly:boolean;alert:boolean}
export interface NotificationDriver {enabled():Promise<boolean>;activeIds():Promise<number[]>;publish(view:NoticeView):Promise<void>;cancel(id:number):Promise<void>;canAlert():Promise<boolean>}
export function notificationView(task:TaskRecord):NoticeView {
  let actions:NoticeAction[]=[];
  if(task.state==='running'||task.state==='preparing')actions=[{name:'pause',label:'暂停',input:false},{name:'stop',label:'停止',input:false}];
  else if(task.state==='paused')actions=[{name:'resume',label:'继续',input:false},{name:'stop',label:'停止',input:false}];
  else if(task.state==='queued')actions=[{name:'stop',label:'取消排队',input:false}];
  else if(terminal(task.state)){
    if(task.targetBundle&&task.targetAbility)actions.push({name:'open-app',label:'打开 App',input:false});
    actions.push({name:'continue',label:'继续对话',input:true});
  }
  return {id:task.notificationId,runId:task.id,conversationId:task.conversationId,token:task.token,title:taskLabel(task.state)+' · '+task.text.slice(0,32),text:(task.reason||(terminal(task.state)?task.resultSummary:'')||task.phase||task.modelLabel).slice(0,200),terminal:terminal(task.state),actions,updateOnly:task.posted,alert:false};
}
export function completionShouldAlert(state:TaskState,enabled:boolean,interactive:boolean,dismissed:boolean,alerted:boolean):boolean{
  return state==='completed'&&enabled&&interactive&&!dismissed&&!alerted;
}

/** One serial publisher. Deleted cards never get recreated by delayed progress or recovery. */
export class TaskNotifications {
  private tail:Promise<void>=Promise.resolve();
  private timer:ReturnType<typeof setTimeout>|undefined;
  private signatures:Map<string,string>=new Map();
  private restored:Set<string>=new Set();
  private initialized=false;
  alertsEnabled=true;
  inlineReplyEnabled=false; // Device verification / explicit opt-in, exact-conversation fallback is always available.
  constructor(private ledger:TaskLedger,private driver:NotificationDriver,private warning:(message:string)=>void=()=>{}){}
  initialize():void {if(this.initialized)return;this.initialized=true;for(const t of this.ledger.list())this.restored.add(t.id);}
  changed(immediate=false):void {
    if(!this.initialized)return;
    if(immediate){if(this.timer!==undefined)clearTimeout(this.timer);this.timer=undefined;this.enqueue();return;}
    if(this.timer===undefined)this.timer=setTimeout(()=>{this.timer=undefined;this.enqueue();},750);
  }
  private enqueue():void{this.tail=this.tail.then(()=>this.sync()).catch(()=>this.warning('任务通知未更新；任务记录仍可在助手中查看'));}
  async flush():Promise<void>{if(this.timer!==undefined){clearTimeout(this.timer);this.timer=undefined;this.enqueue();}await this.tail;}
  async dismissed(id:number,token:string):Promise<void>{
    const task=this.ledger.list().find(t=>t.notificationId===id&&t.token===token);
    if(task&&!task.dismissed)await this.ledger.update(task.id,t=>{t.dismissed=true;});
  }
  private async sync():Promise<void>{
    if(!await this.driver.enabled())return;
    let active=new Set(await this.driver.activeIds());
    const completed=this.ledger.list().filter(t=>terminal(t.state)&&t.posted&&!t.dismissed).sort((a,b)=>b.updatedAt-a.updatedAt);
    for(const old of completed.slice(3)){await this.ledger.update(old.id,t=>{t.dismissed=true;});await this.driver.cancel(old.notificationId);active.delete(old.notificationId);}
    for(const original of this.ledger.list()){
      let task=this.ledger.get(original.id)!;
      if(task.dismissed||task.continuationId||task.state==='queued')continue;
      if(task.posted&&!active.has(task.notificationId)){
        await this.ledger.update(task.id,t=>{t.dismissed=true;});continue;
      }
      if(this.restored.has(task.id)&&!task.posted)continue;
      const view=notificationView(task);if(!this.inlineReplyEnabled)for(const a of view.actions)a.input=false;
      const signature=JSON.stringify([view.title,view.text,view.actions,view.terminal]);
      if(this.signatures.get(task.id)===signature)continue;
      if(!task.posted){
        // Commit identity before the OS side effect. A crash cannot republish an old card on restart.
        task=await this.ledger.update(task.id,t=>{t.posted=true;});view.updateOnly=false;
      }
      if(terminal(task.state)&&!task.alerted){
        view.alert=!this.restored.has(task.id)&&completionShouldAlert(task.state,this.alertsEnabled,await this.driver.canAlert(),task.dismissed,task.alerted);
        // Consume the completion attempt even while locked. Unlocking must never replay a stale alert.
        await this.ledger.update(task.id,t=>{t.alerted=true;});
      }
      const latest=this.ledger.get(task.id);
      if(latest?.dismissed||latest?.continuationId)continue;
      await this.driver.publish(view);active.add(view.id);this.signatures.set(task.id,signature);
    }
  }
  async clearContinued(sourceId:string):Promise<void>{const task=this.ledger.get(sourceId);if(!task)return;await this.ledger.update(sourceId,t=>{t.dismissed=true;});await this.driver.cancel(task.notificationId);}
}
