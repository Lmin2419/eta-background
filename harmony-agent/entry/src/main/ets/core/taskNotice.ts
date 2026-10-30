export interface TaskNotice { title:string; text:string; running:boolean }
/** Serialize OS notification writes; a completion cannot be overwritten by a late progress write. */
export class TaskNoticeQueue {
  private tail:Promise<void>=Promise.resolve();
  private timer:ReturnType<typeof setTimeout>|undefined;
  private pending='';
  private ended=false;
  constructor(private publish:(notice:TaskNotice)=>Promise<void>,private warning:(message:string)=>void=()=>{}){}
  private send(notice:TaskNotice):Promise<void> {
    const write=this.tail.then(()=>this.publish(notice));
    this.tail=write.catch(()=>{this.warning('任务通知更新失败；请返回助手查看状态');});
    return this.tail;
  }
  start():Promise<void>{return this.send({title:'随身助手 · 正在执行',text:'点击返回助手查看或接管任务',running:true});}
  update(text:string):void {
    if(this.ended)return;this.pending=text;
    if(this.timer!==undefined)return;
    this.timer=setTimeout(()=>{this.timer=undefined;if(!this.ended)void this.send({title:'随身助手 · 正在执行',text:this.pending,running:true});},1000);
  }
  finish(success:boolean,stopped:boolean):Promise<void> {
    if(this.ended)return this.tail;this.ended=true;
    if(this.timer!==undefined){clearTimeout(this.timer);this.timer=undefined;}
    return this.send({title:stopped?'随身助手 · 已停止':success?'随身助手 · 任务完成':'随身助手 · 任务未完成',
      text:success?'点击查看回复与执行记录':'点击返回助手查看原因与当前页面',running:false});
  }
}
