// Ported from app/src/test/kotlin/io/github/mangi/eta/ui/markdown/SmoothTextRevealPolicyTest.kt @ 3b1794d
// Copyright © 2026 蛮吉 (Mangi-11). PolyForm Noncommercial 1.0.0.
import test from "node:test";
import assert from "node:assert/strict";
import {
  SmoothTextRevealCoordinator,
  graphemeBoundaries as boundaries,
  updateGraphemeBoundaries as update,
  smoothRevealSpeed as speed,
  advanceSmoothReveal as advance,
} from "../../entry/src/main/ets/core/eta/markdown/smoothTextReveal";
import {
  markdownBlockGap as gap,
  listMarkerVisible as visible,
} from "../../entry/src/main/ets/core/eta/markdown/markdownStylePolicy";
import {
  MarkdownBlock,
  plainText,
} from "../../entry/src/main/ets/core/eta/markdown/markdownModel";
import { StreamingGfmParserSession } from "../../entry/src/main/ets/core/eta/markdown/streamingGfmParser";
const close = (actual: number, expected: number) =>
  assert.ok(Math.abs(actual - expected) <= 0.0001, `${actual} != ${expected}`);
test("coordinatorTracksBackgroundAnimationSuspension", () => {
  const c = new SmoothTextRevealCoordinator();
  assert.equal(c.isAnimationPaused, false);
  c.pauseAnimationsAndCatchUp();
  assert.ok(c.isAnimationPaused);
  c.resumeAnimationsAfterCatchUp();
  assert.equal(c.isAnimationPaused, false);
});
test("emptyAndOrdinaryTextExposeEveryGraphemeBoundary", () => {
  assert.deepEqual(boundaries(""), [0]);
  assert.deepEqual(boundaries("A中B"), [0, 1, 2, 3]);
});
test("emojiSurrogatePairIsOneGrapheme", () => {
  assert.deepEqual(boundaries("A😀B"), [0, 1, 3, 4]);
});
test("extendedEmojiSequencesAreNeverSplit", () => {
  assert.deepEqual(boundaries("A👨‍👩‍👧‍👦B"), [0, 1, 12, 13]);
  assert.deepEqual(boundaries("A👍🏽B"), [0, 1, 5, 6]);
  assert.deepEqual(boundaries("A🇨🇳B"), [0, 1, 5, 6]);
});
test("combiningMarkAndCrLfStayInOneGrapheme", () => {
  assert.deepEqual(boundaries("Ae\u0301B"), [0, 1, 3, 4]);
  assert.deepEqual(boundaries("A\r\nB"), [0, 1, 3, 4]);
});
test("appendedTextOnlyRebuildsTheLastPotentiallyExtendedGrapheme", () => {
  const prefix = "A👨‍👩";
  const family = `${prefix}‍👧‍👦B`;
  assert.deepEqual(
    update(prefix, boundaries(prefix), family),
    boundaries(family),
  );
  assert.deepEqual(
    update("A\r", boundaries("A\r"), "A\r\nB"),
    boundaries("A\r\nB"),
  );
});
test("nonAppendReplacementFallsBackToACompleteBoundaryScan", () => {
  const previous = "alpha 😀";
  const replacement = "beta 👨‍👩‍👧‍👦";
  assert.deepEqual(
    update(previous, boundaries(previous), replacement),
    boundaries(replacement),
  );
});
test("replacementSharingPrefixInsideSurrogatePairFallsBackToCompleteScan", () => {
  for (const [previous, replacement] of [
    ["😀 alpha", "😁 beta"],
    ["A😀x", "A😀y"],
    ["first", "second"],
  ])
    assert.deepEqual(
      update(previous, boundaries(previous), replacement),
      boundaries(replacement),
    );
});
test("changedExtendedGraphemeFallsBackToCompleteScanWithoutSplitting", () => {
  assert.deepEqual(update("👨‍👩X", boundaries("👨‍👩X"), "👨‍👧Y"), [0, 5, 6]);
});
test("revealSpeedUsesBaseRateThenCatchesUpWithoutCeiling", () => {
  for (const [backlog, expected] of [
    [0, 36],
    [9, 45],
    [20, 100],
    [1000, 5000],
    [10000, 50000],
  ])
    close(speed(backlog), expected);
});
test("normalFrameAdvancesFractionallyAtBaseRate", () => {
  close(advance(0, 10, 1 / 60, 1), 0.6);
});
test("catchUpAdvancesBeyondFormerSpeedCap", () => {
  close(advance(3, 1000, 0.05, 1000), 253);
});
test("sustainedFastOutputDoesNotAccumulateUnboundedBacklog", () => {
  let target = 0;
  let progress = 0;
  for (let i = 0; i < 600; i++) {
    target += 20;
    progress = advance(progress, target, 1 / 60, target - progress);
  }
  assert.ok(target - progress < 300);
  for (let i = 0; i < 90; i++)
    progress = advance(progress, target, 1 / 60, target - progress);
  close(progress, target);
});
test("frameAdvanceClampsInvalidTimeAndTargetBounds", () => {
  close(advance(2, 10, -1, 10), 2);
  close(advance(4.75, 5, 1, 100), 5);
  close(advance(7, 5, 1, 100), 5);
});
test("markdownDocumentCollapsesSourceBlankLinesIntoSemanticBlocks", () => {
  const blocks = new StreamingGfmParserSession().parse(
    "第一段\n\n\n第二段",
    true,
  ).document.blocks;
  assert.equal(blocks.length, 2);
  assert.deepEqual(
    blocks.map((block) => (block.kind === "paragraph" ? block.text.text : "")),
    ["第一段", "第二段"],
  );
});
test("markdownBlockGapBuildsReadableHierarchyWithoutLeadingGap", () => {
  const first: MarkdownBlock = {
    kind: "paragraph",
    offset: 0,
    text: plainText("第一段"),
  };
  const second: MarkdownBlock = {
    kind: "paragraph",
    offset: 4,
    text: plainText("第二段"),
  };
  const heading: MarkdownBlock = {
    kind: "heading",
    offset: 8,
    level: 2,
    text: plainText("小节"),
  };
  const subHeading: MarkdownBlock = {
    kind: "heading",
    offset: 12,
    level: 3,
    text: plainText("子节"),
  };
  const list: MarkdownBlock = {
    kind: "list",
    offset: 16,
    ordered: false,
    loose: false,
    items: [],
  };
  const table: MarkdownBlock = {
    kind: "table",
    offset: 20,
    alignments: [],
    header: [],
    rows: [],
  };
  assert.equal(gap(null, first, "Answer"), 0);
  assert.equal(gap(first, second, "Answer"), 12);
  assert.equal(gap(first, heading, "Answer"), 22);
  assert.equal(gap(first, subHeading, "Answer"), 18);
  assert.equal(gap(heading, second, "Answer"), 8);
  assert.equal(gap(heading, subHeading, "Answer"), 8);
  assert.equal(gap(first, list, "Answer"), 14);
  assert.equal(gap(table, first, "Answer"), 14);
  assert.equal(gap(null, first, "Thinking"), 0);
  assert.equal(gap(first, second, "Thinking"), 9);
  assert.equal(gap(first, heading, "Thinking"), 16.5);
});
test("listMarkerWaitsForItsOwnContentToStartRevealing", () => {
  assert.equal(visible(true, 10, new Set()), false);
  assert.ok(visible(true, 10, new Set([10])));
});
test("listMarkerStaysHiddenForEmptyItemsUntilRevealEnds", () => {
  assert.equal(visible(true, null, new Set()), false);
  assert.ok(visible(false, null, null));
  assert.ok(visible(false, 10, new Set()));
});
