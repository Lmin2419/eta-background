// Ported from app/src/test/kotlin/io/github/mangi/eta/ui/markdown/SmoothTextRevealCoordinatorTest.kt @ 3b1794d
// Copyright © 2026 蛮吉 (Mangi-11). PolyForm Noncommercial 1.0.0.
// Logic-only: explicit frame delta/layout identity replace Robolectric TextMeasurer/Compose frame clock.
import test from "node:test";
import assert from "node:assert/strict";
import {
  SmoothTextRevealCoordinator,
  SmoothTextRevealState,
  RevealNode,
} from "../../entry/src/main/ets/core/eta/markdown/smoothTextReveal";
function attach(
  coordinator: SmoothTextRevealCoordinator,
  key: number,
  text: string,
): RevealNode {
  const state = new SmoothTextRevealState(key, coordinator);
  const node: RevealNode = { onRevealDataChanged: () => {} };
  state.attach(node);
  state.onTextLayout(text, { identity: text });
  return node;
}
test("logic: detachedEarlierBlockCompletesAndLaterBlockKeepsRevealing", () => {
  const c = new SmoothTextRevealCoordinator();
  const earlier = attach(c, 0, "早先的思考正文");
  attach(c, 100, "随后到达的工具输出内容");
  c.detach(0, earlier);
  assert.equal(c.drawSnapshot(0)?.progress, 7);
  assert.ok(c.started.has(0));
  assert.equal(c.drained, false);
  c.advanceFrame(0.05);
  assert.ok(c.drawSnapshot(100)!.progress > 0);
});
test("logic: oneFrameCarriesAdvanceAcrossBlocksInSourceOrder", () => {
  const c = new SmoothTextRevealCoordinator();
  const keys = Array.from({ length: 10 }, (_, i) => i * 20);
  for (const key of [...keys].reverse()) attach(c, key, "abcdefghij");
  c.advanceFrame(0.05);
  assert.equal(c.drawSnapshot(keys[0])?.progress, 10);
  assert.equal(c.drawSnapshot(keys[1])?.progress, 10);
  assert.equal(c.drawSnapshot(keys[2])?.progress, 5);
  for (const key of keys.slice(3))
    assert.equal(c.drawSnapshot(key)?.progress, 0);
  assert.deepEqual(c.started, new Set(keys.slice(0, 3)));
  assert.equal(c.drained, false);
  for (let i = 0; i < 60; i++) c.advanceFrame(0.05);
  assert.ok(c.drained);
  assert.deepEqual(c.started, new Set(keys));
});
test("logic: reattachedBlockKeepsCompletedPrefixAndAnimatesOnlyNewText", () => {
  const c = new SmoothTextRevealCoordinator();
  const old = attach(c, 0, "已有文字");
  c.detach(0, old);
  assert.ok(c.drained);
  attach(c, 0, "已有文字和新增文字");
  assert.equal(c.drawSnapshot(0)?.progress, 4);
  assert.equal(c.drawSnapshot(0)!.boundaries.length - 1, 9);
  assert.equal(c.drained, false);
});
test("logic: layoutWithoutMountedNodeIsImmediatelyReadableOnLaterAttach", () => {
  const c = new SmoothTextRevealCoordinator();
  const text = "尚未挂载时收到的历史内容";
  const state = new SmoothTextRevealState(0, c);
  state.onTextLayout(text, { identity: text });
  assert.ok(c.drained);
  assert.equal(c.drawSnapshot(0)?.progress, text.length);
  state.attach({ onRevealDataChanged: () => {} });
  assert.ok(c.drained);
  assert.equal(c.drawSnapshot(0)?.progress, text.length);
});
test("logic: staleDetachDoesNotCompleteTheReplacementNode", () => {
  const c = new SmoothTextRevealCoordinator();
  const old = attach(c, 0, "原始内容");
  const replacement = attach(c, 0, "替换后的完整内容");
  c.detach(0, old);
  assert.equal(c.drawSnapshot(0)?.progress, 0);
  assert.equal(c.drained, false);
  c.detach(0, replacement);
  assert.ok(c.drained);
});
