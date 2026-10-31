// Logic port from app/src/test/kotlin/io/github/mangi/eta/ui/app/StartupSplashTest.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
// Android ViewTreeObserver/lifecycle and Harmony startup-window behavior are not exercised.
import test from "node:test";
import assert from "node:assert/strict";
import { StartupSplashGate } from "../../entry/src/main/ets/core/eta/startupSplash";
test("仅在首帧必要配置未就绪时阻止绘制 (gate logic only)", () => {
  let ready = false,
    checks = 0;
  const gate = new StartupSplashGate(() => {
    checks++;
    return ready;
  });
  assert.equal(gate.onPreDraw(), false);
  ready = true;
  assert.equal(gate.onPreDraw(), true);
  ready = false;
  assert.equal(gate.onPreDraw(), true);
  assert.equal(checks, 2);
});
test("准备期间销毁页面会移除绘制拦截 (gate logic only)", () => {
  let checks = 0;
  const gate = new StartupSplashGate(() => {
    checks++;
    return false;
  });
  assert.equal(gate.onPreDraw(), false);
  gate.onDestroy();
  assert.equal(gate.onPreDraw(), true);
  assert.equal(checks, 1);
});
