// Logic port from app/src/test/kotlin/io/github/mangi/eta/ui/navigation/AgentNavigationRestorationTest.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
// Activity/Bundle/Compose restoration remains a platform test, not replaced by this assertion.
import test from "node:test";
import assert from "node:assert/strict";
import { EtaNavigation } from "../../entry/src/main/ets/core/eta/etaNavigation";
import {
  AppRoute,
  AppRouteName as R,
} from "../../entry/src/main/ets/core/eta/routes";
test("Activity 重建后保留完整页面栈 (serialization logic only)", () => {
  const first = new EtaNavigation();
  first.push(new AppRoute(R.Settings));
  first.push(new AppRoute(R.ModelProviderDetail, "provider"));
  const saved = first.saveStack();
  const restored = new EtaNavigation();
  assert.equal(restored.restoreStack(saved), true);
  assert.deepEqual(restored.snapshot().routes, [
    new AppRoute(R.Home),
    new AppRoute(R.Settings),
    new AppRoute(R.ModelProviderDetail, "provider"),
  ]);
});
