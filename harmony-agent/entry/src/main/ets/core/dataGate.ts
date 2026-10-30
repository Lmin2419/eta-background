/** Keeps restore/export snapshots separate from in-flight UI edits and file pickers. */
export class DataGate {
  private active=0;
  private exclusive=false;
  enter():()=>void {
    if(this.exclusive)throw new Error('数据维护正在进行，请稍后再保存');
    this.active++;let released=false;
    return ()=>{if(!released){released=true;this.active--;}};
  }
  maintain():()=>void {
    if(this.exclusive||this.active)throw new Error('仍有配置保存、文件操作或数据维护进行中，请完成后再试');
    this.exclusive=true;let released=false;
    return ()=>{if(!released){released=true;this.exclusive=false;}};
  }
}
