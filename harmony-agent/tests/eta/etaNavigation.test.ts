// Navigation boundary regressions for the Harmony adaptation; these are new tests, not upstream test counts.
import test from "node:test";
import assert from "node:assert/strict";
import { AgentNavigator } from "../../entry/src/main/ets/core/eta/agentNavigator";
import { EtaNavigation } from "../../entry/src/main/ets/core/eta/etaNavigation";
import {
  AppRoute,
  AppRouteName as R,
  APP_ROUTE_NAMES,
  encodeRoutes,
  decodeRoute,
  routeCapability,
} from "../../entry/src/main/ets/core/eta/routes";
test("push/pop/replace preserve Eta whole-stack duplicate semantics", () => {
  const n = new AgentNavigator();
  n.push(new AppRoute(R.Settings));
  n.push(new AppRoute(R.ModelProviders));
  n.push(new AppRoute(R.Settings));
  assert.equal(n.current()?.name, R.ModelProviders);
  n.replace(new AppRoute(R.Settings));
  assert.equal(n.snapshot().length, 2);
  n.replace(new AppRoute(R.Memory));
  assert.equal(n.current()?.name, R.Memory);
  assert.equal(n.pop(), true);
  assert.equal(n.pop(), false);
  n.replace(new AppRoute(R.Settings));
  n.popToHome();
  assert.equal(n.current()?.name, R.Home);
});
test("child back preserves drawer intent; home back closes drawer before exit", () => {
  const n = new EtaNavigation();
  n.setConversationPaneOpen(true);
  n.push(new AppRoute(R.Settings));
  assert.equal(n.back(), true);
  assert.equal(n.snapshot().conversationPaneOpen, true);
  assert.equal(n.back(), true);
  assert.equal(n.snapshot().conversationPaneOpen, false);
  assert.equal(n.back(), false);
});
test("all Android-only routes stay hidden and B routes cannot be invoked", () => {
  const n = new EtaNavigation();
  for (const name of [
    R.Terminal,
    R.LinuxEnvironment,
    R.SharedFolders,
    R.SystemEnhance,
  ]) {
    assert.equal(routeCapability(name), "C");
    assert.equal(n.push(new AppRoute(name)), false);
  }
  assert.equal(n.push(new AppRoute(R.LinuxFiles, "alpine")), false);
  assert.equal(n.push(new AppRoute(R.Browser)), false);
  assert.equal(n.push(new AppRoute(R.Workspace)), false);
  assert.equal(APP_ROUTE_NAMES.length, 33);
  assert.equal(n.snapshot().routes.length, 1);
});
test("restoration drops unavailable routes and malformed input is atomic", () => {
  const n = new EtaNavigation();
  assert.equal(
    n.restoreStack(
      encodeRoutes([
        new AppRoute(R.Home),
        new AppRoute(R.Terminal),
        new AppRoute(R.Settings),
      ]),
    ),
    true,
  );
  assert.equal(n.snapshot().routes.length, 2);
  assert.equal(n.restoreStack("[null]"), false);
  assert.equal(n.snapshot().current?.name, R.Settings);
  assert.throws(() =>
    decodeRoute(
      '{"type":"io.github.mangi.eta.ui.navigation.AppRoute.ModelProviderDetail"}',
    ),
  );
  assert.throws(() =>
    decodeRoute(
      '{"type":"io.github.mangi.eta.ui.navigation.AppRoute.Browser","sessionKey":3}',
    ),
  );
  assert.deepEqual(
    decodeRoute(
      '{"type":"io.github.mangi.eta.ui.navigation.AppRoute.Home","futureField":true}',
    ),
    new AppRoute(R.Home),
  );
});
test("verified notification opens existing conversation then returns home without execution", async () => {
  const n = new EtaNavigation();
  n.setConversationPaneOpen(true);
  n.push(new AppRoute(R.Settings));
  const seen: string[] = [];
  assert.equal(
    await n.notificationConversation("conversation", async (id) => {
      seen.push(id);
      return true;
    }),
    true,
  );
  assert.deepEqual(seen, ["conversation"]);
  assert.equal(n.snapshot().routes.length, 1);
  assert.equal(n.snapshot().conversationPaneOpen, false);
});
test("missing or superseded external conversation never changes the current route", async () => {
  const n = new EtaNavigation();
  n.push(new AppRoute(R.Settings));
  assert.equal(
    await n.notificationConversation("missing", async () => false),
    false,
  );
  assert.equal(n.snapshot().current?.name, R.Settings);
  let finish: (value: boolean) => void = () => {};
  const pending = n.notificationConversation(
    "old",
    () =>
      new Promise<boolean>((resolve) => {
        finish = resolve;
      }),
  );
  n.push(new AppRoute(R.ModelProviders));
  finish(true);
  assert.equal(await pending, false);
  assert.equal(n.snapshot().current?.name, R.ModelProviders);
});
