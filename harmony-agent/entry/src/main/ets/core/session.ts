export interface BackgroundDriver {
  start(onRevoked: (reason: string) => void, isCancelled: () => boolean): Promise<void>;
  stop(): Promise<void>;
}
export interface AgentTask { run(goal: string): Promise<string>; stop(): void }

/** One foreground-authorized task. A late background grant must never restart a stopped agent. */
export class TaskSession {
  private attempted = false;
  private cancelled = false;
  private acquired = false;
  private cleanup: Promise<void> | undefined;
  stopReason = '';
  constructor(private task: AgentTask, private background: BackgroundDriver,
    private hide: () => Promise<void>, private abortDevice: () => void,
    private notice: (message: string) => void) {}
  stop(reason = '任务已停止，请检查当前页面'): void {
    if (this.cancelled) return;
    this.cancelled = true; this.stopReason = reason;
    this.task.stop(); this.abortDevice();
    this.notice(reason);
    if (this.acquired) void this.release();
  }
  private check(): void { if (this.cancelled) throw new Error('CANCELLED'); }
  private release(): Promise<void> {
    if (!this.cleanup) this.cleanup = Promise.resolve().then(() => this.background.stop()).catch(() => {
      this.notice('后台资源释放失败，请关闭助手并在系统设置中撤销定位权限');
    });
    return this.cleanup;
  }
  async run(goal: string): Promise<string> {
    if (this.attempted) throw new Error('同一个任务会话不能重复启动');
    this.attempted = true;
    try {
      this.check();
      await this.background.start(reason => this.stop(reason), () => this.cancelled);
      this.acquired = true; this.check();
      await this.hide(); this.check();
      const result = await this.task.run(goal);
      this.check();
      return result;
    } finally {
      // Ignore cancellation notifications caused by our own cleanup.
      this.cancelled = true;
      await this.release();
    }
  }
}
