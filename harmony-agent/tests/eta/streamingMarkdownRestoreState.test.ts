// Ported from app/src/test/kotlin/io/github/mangi/eta/ui/markdown/StreamingMarkdownRestoreStateTest.kt @ 3b1794d
// Copyright © 2026 蛮吉 (Mangi-11). PolyForm Noncommercial 1.0.0.
import test from "node:test";
import assert from "node:assert/strict";
import {
  StreamingMarkdownRestoreState,
  isStreamingMarkdownTargetComplete as complete,
} from "../../entry/src/main/ets/core/eta/markdown/streamingMarkdownRestoreState";
test("restoredContentWaitsForMatchingLayoutRegardlessOfEarlierLayoutCount", () => {
  const state = new StreamingMarkdownRestoreState();
  state.begin("后台已完成的内容");
  for (let i = 0; i < 10; i++)
    assert.equal(
      state.completeLayout(state.generation, "后台", "后台已完成的内容"),
      false,
    );
  assert.ok(
    state.completeLayout(
      state.generation,
      "后台已完成的内容",
      "后台已完成的内容",
    ),
  );
  assert.equal(
    state.completeLayout(
      state.generation,
      "后台已完成的内容和增量",
      "后台已完成的内容和增量",
    ),
    false,
  );
});
test("delayedOldLayoutCannotResumeAfterAnotherBackgroundCycle", () => {
  const state = new StreamingMarkdownRestoreState();
  state.begin("已有内容");
  const old = state.generation;
  state.pause();
  assert.equal(state.completeLayout(old, "已有内容", "已有内容"), false);
  state.begin("已有内容和后台增量");
  assert.equal(
    state.completeLayout(old, "已有内容和后台增量", "已有内容和后台增量"),
    false,
  );
  assert.equal(
    state.completeLayout(state.generation, "已有内容", "已有内容和后台增量"),
    false,
  );
  assert.ok(
    state.completeLayout(
      state.generation,
      "已有内容和后台增量",
      "已有内容和后台增量",
    ),
  );
});
test("newNetworkTextDoesNotKeepMovingTheRestoreBaseline", () => {
  const state = new StreamingMarkdownRestoreState();
  state.begin("历史");
  assert.ok(state.completeLayout(state.generation, "历史", "历史和新内容"));
});
test("authoritativeReplacementCanCompleteRestoreButStaleLayoutCannot", () => {
  const state = new StreamingMarkdownRestoreState();
  state.begin("旧内容");
  assert.equal(
    state.completeLayout(state.generation, "旧内容", "纠正后的内容"),
    false,
  );
  assert.ok(
    state.completeLayout(state.generation, "纠正后的内容", "纠正后的内容"),
  );
});
test("onlyCurrentTerminalSnapshotMayFinishReveal", () => {
  assert.equal(complete("正文和增量", false, "正文", true), false);
  assert.equal(complete("正文", true, "正文", true), false);
  assert.equal(complete("正文", false, "正文", false), false);
  assert.equal(complete("正文", false, null, false), false);
  assert.ok(complete("正文", false, "正文", true));
});
