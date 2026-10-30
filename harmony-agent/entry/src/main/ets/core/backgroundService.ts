import { BackgroundDriver } from './session';

export type BackgroundState = 'off' | 'starting' | 'active' | 'stopping' | 'error';
interface Attempt {
  driver: BackgroundDriver; cancelled: boolean; failure: boolean; reason: string;
  operation?: Promise<void>; cleanup?: Promise<void>;
}
/** A user-started background session. Task leases do not end the user's session when one task finishes. */
export class BackgroundService {
  state: BackgroundState = 'off';
  message = '后台服务未启动';
  private current: Attempt | undefined;
  private borrower: ((reason:string)=>void) | undefined;
  private cleanupFailed = false;
  constructor(private factory:()=>BackgroundDriver, private changed:()=>void = ()=>{}) {}
  get active(): boolean { return this.state==='active'; }
  private show(state: BackgroundState, message: string): void { this.state=state;this.message=message;this.changed(); }
  async start(): Promise<void> {
    if(this.cleanupFailed)throw new Error('上次后台资源未能释放，请先在系统设置中停止助手后重开');
    if (this.current) throw new Error('后台服务正在运行或切换状态');
    const attempt:Attempt={driver:this.factory(),cancelled:false,failure:false,reason:''};
    this.current=attempt;this.show('starting','正在申请通知与定位后台权限…');
    // Publish the pending promise before the driver can synchronously deliver a cancellation callback.
    attempt.operation=Promise.resolve().then(()=>this.acquire(attempt));
    await attempt.operation;
  }
  private async acquire(attempt:Attempt):Promise<void> {
    try {
      if(attempt.cancelled)return;
      await attempt.driver.start(reason=>{void this.stop(reason,true);},()=>attempt.cancelled);
      if (!attempt.cancelled) this.show('active','后台服务已启动；可切换应用，删除系统任务通知会停止服务');
    } catch (e) {
      if (!attempt.cancelled) {attempt.cancelled=true;attempt.failure=true;attempt.reason=(e as Error).message||'后台申请失败';}
    } finally {
      if (attempt.cancelled) await this.release(attempt);
    }
  }
  private async release(attempt:Attempt):Promise<void> {
    if (!attempt.cleanup) attempt.cleanup=Promise.resolve().then(()=>attempt.driver.stop()).catch(()=>{
      this.cleanupFailed=true;
      attempt.failure=true;attempt.reason='后台资源释放失败，请在系统设置中停止助手后重开';
    }).then(()=>{
      if(this.current===attempt){this.current=undefined;this.borrower=undefined;this.show(attempt.failure?'error':'off',attempt.reason||'后台服务已停止');}
    });
    await attempt.cleanup;
  }
  async stop(reason='后台服务已停止', failure=false):Promise<void> {
    const attempt=this.current;if(!attempt)return;
    if (!attempt.cancelled) {
      attempt.cancelled=true;attempt.reason=reason;attempt.failure=failure;
      this.show('stopping','正在停止后台服务…');
      const borrower=this.borrower;this.borrower=undefined;borrower?.(reason);
    }
    await attempt.operation;
    await this.release(attempt);
  }
  /** The next task borrows the active session without acquiring a second OS task. */
  lease():BackgroundDriver {
    const owner=this;let revoke:((reason:string)=>void)|undefined;
    return {
      async start(onRevoked:(reason:string)=>void,isCancelled:()=>boolean):Promise<void> {
        if(isCancelled())throw new Error('CANCELLED');
        if(!owner.active||!owner.current||owner.current.cancelled)throw new Error('后台服务已停止，请重新启动');
        if(owner.borrower)throw new Error('已有任务正在使用后台服务');
        revoke=onRevoked;owner.borrower=revoke;
      },
      async stop():Promise<void> {if(owner.borrower===revoke)owner.borrower=undefined;revoke=undefined;}
    };
  }
}
