// Ported from app/src/test/kotlin/io/github/mangi/eta/ui/markdown/StreamingMarkdownStateCacheTest.kt @ 3b1794d
// Copyright © 2026 蛮吉 (Mangi-11). PolyForm Noncommercial 1.0.0.
import test from "node:test";
import assert from "node:assert/strict";
import { StreamingMarkdownStateCache } from "../../entry/src/main/ets/core/eta/markdown/streamingMarkdownStateCache";
import { StaticMarkdownDocumentCache } from "../../entry/src/main/ets/core/eta/markdown/staticMarkdownDocumentCache";
import { StreamingGfmParserSession } from "../../entry/src/main/ets/core/eta/markdown/streamingGfmParser";
import { defaultInlineStyle } from "../../entry/src/main/ets/core/eta/markdown/markdownModel";
test("completedOffscreenTurnsDoNotAccumulateStreamingSessions", () => {
  const cache = new StreamingMarkdownStateCache();
  for (let index = 0; index < 200; index++) {
    const id = `answer-${index}`;
    const state = cache.getOrCreate(id);
    state.attachContent();
    cache.discardUnused(new Set());
    assert.equal(cache.states.get(id), state);
    state.detachContent();
    cache.discardUnused(new Set());
    assert.equal(cache.states.size, 0);
  }
});
test("detachedLiveMessageKeepsItsParsingAndRevealState", () => {
  const cache = new StreamingMarkdownStateCache();
  const state = cache.getOrCreate("live");
  state.revealedContent = "已显示的前缀";
  cache.discardUnused(new Set(["live"]));
  assert.equal(cache.getOrCreate("live"), state);
  assert.equal(cache.states.get("live")?.revealedContent, "已显示的前缀");
  assert.deepEqual(cache.unusedMessageIds(new Set(["live"])), []);
});
test("everyMountedOwnerMustDetachBeforeACompletedMessageCanBeEvicted", () => {
  const cache = new StreamingMarkdownStateCache();
  const state = cache.getOrCreate("visible");
  state.attachContent();
  state.attachContent();
  state.detachContent();
  cache.discardUnused(new Set());
  assert.equal(cache.states.get("visible"), state);
  state.detachContent();
  cache.discardUnused(new Set());
  assert.equal(cache.states.get("visible"), undefined);
});
test("evictionHandsTheFinalDocumentToTheBoundedStaticCache", () => {
  const staticCache = new StaticMarkdownDocumentCache();
  const cache = new StreamingMarkdownStateCache(staticCache);
  const content = "**完成后的答复：缓存交接**";
  const snapshot = new StreamingGfmParserSession().parse(content, true);
  cache.getOrCreate("completed").snapshot = snapshot;
  cache.discardUnused(new Set());
  assert.equal(cache.states.size, 0);
  assert.equal(
    staticCache.get(content, snapshot.document.inlineStyle),
    snapshot.document,
  );
});
test("anIncompleteDetachedSnapshotNeverBecomesAStaticDocument", () => {
  const staticCache = new StaticMarkdownDocumentCache();
  const cache = new StreamingMarkdownStateCache(staticCache);
  const content = "**尚未完成的快照：不进入静态缓存";
  const snapshot = new StreamingGfmParserSession().parse(content, false);
  cache.getOrCreate("unfinished").snapshot = snapshot;
  cache.discardUnused(new Set());
  assert.equal(cache.states.has("unfinished"), false);
  assert.equal(staticCache.get(content, snapshot.document.inlineStyle), null);
});
test("movingCompletedDocumentsToStaticRenderingStillHasABoundedHistoryCache", () => {
  const staticCache = new StaticMarkdownDocumentCache();
  const cache = new StreamingMarkdownStateCache(staticCache);
  for (let index = 0; index < 128; index++) {
    cache.getOrCreate(`answer-${index}`).snapshot =
      new StreamingGfmParserSession().parse(
        `有界文档缓存回归测试 ${index}`,
        true,
      );
    cache.discardUnused(new Set());
  }
  assert.equal(cache.states.size, 0);
  assert.equal(
    staticCache.get("有界文档缓存回归测试 0", defaultInlineStyle),
    null,
  );
  assert.ok(staticCache.get("有界文档缓存回归测试 127", defaultInlineStyle));
});
