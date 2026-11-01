// 任务 R4-1：帧回调缺失时的兜底时钟单测。
import test from "node:test";
import assert from "node:assert/strict";
import {
  FrameFallback,
  FRAME_FALLBACK_GRACE_MS,
  FRAME_FALLBACK_INTERVAL_MS,
} from "../../entry/src/main/ets/core/eta/frameFallback";
import type { FrameFallbackTimers } from "../../entry/src/main/ets/core/eta/frameFallback";

interface FakeTimer {
  at: number;
  handler: () => void;
}

class FakeClock implements FrameFallbackTimers {
  private time = 0;
  private sequence = 1;
  private readonly timers = new Map<number, FakeTimer>();

  now(): number {
    return this.time;
  }

  setTimeout(handler: () => void, delayMs: number): number {
    const id = this.sequence++;
    this.timers.set(id, { at: this.time + delayMs, handler });
    return id;
  }

  clearTimeout(id: number): void {
    this.timers.delete(id);
  }

  advance(ms: number): void {
    const target = this.time + ms;
    for (;;) {
      let nextId: number | undefined;
      let next: FakeTimer | undefined;
      for (const [id, timer] of this.timers) {
        if (timer.at <= target && (next === undefined || timer.at < next.at)) {
          nextId = id;
          next = timer;
        }
      }
      if (next === undefined || nextId === undefined) break;
      this.timers.delete(nextId);
      this.time = next.at;
      next.handler();
    }
    this.time = target;
  }

  get pendingTimers(): number {
    return this.timers.size;
  }
}

test("framesWithinGraceKeepFallbackDisabled", () => {
  const clock = new FakeClock();
  const ticks: number[] = [];
  const fallback = new FrameFallback(clock, (seconds) => {
    ticks.push(seconds);
    return true;
  });
  fallback.start();
  // 真实帧按 16 ms 持续到达：每帧都会 onFrame() 并重新 start()，兜底不应触发。
  for (let frame = 0; frame < 25; frame++) {
    clock.advance(FRAME_FALLBACK_INTERVAL_MS);
    fallback.onFrame();
    fallback.start();
  }
  assert.deepEqual(ticks, []);
});

test("missingFramesStartFallbackAfterGrace", () => {
  const clock = new FakeClock();
  const ticks: number[] = [];
  const fallback = new FrameFallback(clock, (seconds) => {
    ticks.push(seconds);
    return true;
  });
  fallback.start();
  clock.advance(FRAME_FALLBACK_GRACE_MS);
  assert.equal(ticks.length, 1);
  assert.ok(ticks[0] > 0, `first fallback tick seconds must be > 0, got ${ticks[0]}`);
  clock.advance(FRAME_FALLBACK_INTERVAL_MS);
  assert.equal(ticks.length, 2);
  assert.ok(Math.abs(ticks[1] - FRAME_FALLBACK_INTERVAL_MS / 1000) < 0.0001);
});

test("repeatedStartWithoutFramesDoesNotPostponeFallback", () => {
  const clock = new FakeClock();
  const ticks: number[] = [];
  const fallback = new FrameFallback(clock, (seconds) => {
    ticks.push(seconds);
    return true;
  });
  fallback.start();
  clock.advance(50);
  // 内容更新会再次调用 start()；没有帧时不能把兜底一直往后推。
  fallback.start();
  clock.advance(50);
  assert.equal(ticks.length, 1);
  assert.ok(Math.abs(ticks[0] - FRAME_FALLBACK_GRACE_MS / 1000) < 0.0001);
});

test("tickReturningFalseStopsScheduling", () => {  const clock = new FakeClock();
  let calls = 0;
  const fallback = new FrameFallback(clock, () => {
    calls++;
    return false;
  });
  fallback.start();
  clock.advance(FRAME_FALLBACK_GRACE_MS);
  assert.equal(calls, 1);
  clock.advance(500);
  assert.equal(calls, 1);
  assert.equal(clock.pendingTimers, 0);
});

test("stopClearsPendingTimer", () => {
  const clock = new FakeClock();
  let calls = 0;
  const fallback = new FrameFallback(clock, () => {
    calls++;
    return true;
  });
  fallback.start();
  clock.advance(FRAME_FALLBACK_GRACE_MS - 50);
  fallback.stop();
  assert.equal(clock.pendingTimers, 0);
  clock.advance(500);
  assert.equal(calls, 0);
});

test("lateFrameOrTimerAfterStopNeverTicks", () => {
  const clock = new FakeClock();
  let calls = 0;
  const fallback = new FrameFallback(clock, () => {
    calls++;
    return true;
  });
  fallback.start();
  clock.advance(FRAME_FALLBACK_GRACE_MS);
  assert.equal(calls, 1);
  fallback.stop();
  clock.advance(1000);
  fallback.onFrame();
  clock.advance(1000);
  assert.equal(calls, 1);
  assert.equal(clock.pendingTimers, 0);
});
