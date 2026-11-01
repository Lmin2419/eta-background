// 兜底帧时钟：预览器等环境里 UIContext.postFrameCallback 可能不回调，
// 用注入的计时器在 100 ms 内没有真实帧时改由 setTimeout 驱动 tick。
// 计时函数通过参数注入，便于单测（见 tests/eta/frameFallback.test.ts）。
export const FRAME_FALLBACK_GRACE_MS = 100;
export const FRAME_FALLBACK_INTERVAL_MS = 16;

/** 注入的计时器接口：生产环境接 setTimeout / clearTimeout / Date.now。 */
export interface FrameFallbackTimers {
  setTimeout(handler: () => void, delayMs: number): number;
  clearTimeout(id: number): void;
  now(): number;
}

/**
 * 真实帧回调优先：start() 后 100 ms 内收到 onFrame() 就取消兜底；
 * 超过 100 ms 没有帧，则每 16 ms 调用一次 tick，秒数取两次调用之间的实际时间差。
 * tick 返回 false 或调用 stop() 时清掉定时器；stop() 之后迟到的帧/定时器都不再触发 tick。
 */
export class FrameFallback {
  private timer: number | null = null;
  private active: boolean = false;
  private lastTickAt: number = 0;

  constructor(
    private readonly timers: FrameFallbackTimers,
    private readonly tick: (seconds: number) => boolean,
  ) {}

  /** 开始等待帧回调；已经排着定时器时不会重复计时（避免频繁更新把兜底一直往后推）。 */
  start(): void {
    this.active = true;
    if (this.timer === null) {
      this.lastTickAt = this.timers.now();
      this.armTimer(FRAME_FALLBACK_GRACE_MS);
    }
  }

  /** 收到真实的 postFrameCallback 帧：立即停用兜底，并记下这一帧的时间。 */
  onFrame(): void {
    if (!this.active) return;
    this.lastTickAt = this.timers.now();
    this.clearTimer();
  }

  /** 停止驱动；之后迟到的帧回调或定时器都不会再调用 tick。 */
  stop(): void {
    this.active = false;
    this.clearTimer();
  }

  private armTimer(delayMs: number): void {
    this.timer = this.timers.setTimeout(() => {
      this.timer = null;
      this.fire();
    }, delayMs);
  }

  private fire(): void {
    if (!this.active) return;
    const now = this.timers.now();
    const elapsedSeconds = Math.max(0, (now - this.lastTickAt) / 1000);
    this.lastTickAt = now;
    if (!this.tick(elapsedSeconds) || !this.active) {
      this.stop();
      return;
    }
    this.armTimer(FRAME_FALLBACK_INTERVAL_MS);
  }

  private clearTimer(): void {
    if (this.timer !== null) {
      this.timers.clearTimeout(this.timer);
      this.timer = null;
    }
  }
}
