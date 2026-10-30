// Ported from app/src/test/kotlin/io/github/mangi/eta/ui/markdown/MarkdownInlineTest.kt @ 3b1794d
// Copyright © 2026 蛮吉 (Mangi-11). PolyForm Noncommercial 1.0.0.
import test from "node:test";
import assert from "node:assert/strict";
import { hasSpan, paragraphOf } from "./markdownFixtures";
test("boldClosingBetweenCjkPunctuationAndTextIsPairedByIntent", () => {
  const { text } = paragraphOf("**这不代表你的真机。**但你研究");
  assert.equal(text.text, "这不代表你的真机。但你研究");
  assert.ok(hasSpan(text, "这不代表你的真机。", "bold"));
  assert.ok(!hasSpan(text, "但你研究", "bold"));
});
test("boldFollowedImmediatelyByCjkTextIsPairedByIntent", () => {
  const { text } = paragraphOf(
    "**我建议你先别把假期变成“大厂题库全背一遍”。**先准备好：",
  );
  assert.ok(!text.text.includes("*"));
  assert.ok(
    hasSpan(text, "我建议你先别把假期变成“大厂题库全背一遍”。", "bold"),
  );
  assert.ok(!hasSpan(text, "先准备好", "bold"));
});
test("boldAroundInlineCodeInCjkSentenceIsPairedByIntent", () => {
  const { text } = paragraphOf(
    "发现 **Android 17 的 `mmd`（内存管理守护程序）**值得你了解",
  );
  assert.ok(!text.text.includes("*"));
  assert.ok(hasSpan(text, "内存管理守护程序", "bold"));
  assert.ok(hasSpan(text, "mmd", "code"));
});
test("unmatchedDelimiterStaysLiteral", () => {
  const { text } = paragraphOf("未闭合 **标记");
  assert.equal(text.text, "未闭合 **标记");
  assert.ok(!hasSpan(text, "标记", "bold"));
});
test("spacedAsterisksStayLiteral", () => {
  const { text } = paragraphOf("2 * 3 * 4");
  assert.equal(text.text, "2 * 3 * 4");
  assert.equal(text.spans.length, 0);
});
test("looseSingleStarPairsAsItalic", () => {
  const { text } = paragraphOf("这是 *斜体* 内容");
  assert.ok(!text.text.includes("*"));
  assert.ok(hasSpan(text, "斜体", "italic"));
});
test("looseTildePairsAsStrikethrough", () => {
  const { text } = paragraphOf("这是 ~~删除线~~ 内容");
  assert.ok(!text.text.includes("~"));
  assert.ok(hasSpan(text, "删除线", "strike"));
});
test("parserStrongKeepsWorkingWithoutLoosePairing", () => {
  const { text } = paragraphOf("普通 **加粗** 普通");
  assert.equal(text.text, "普通 加粗 普通");
  assert.ok(hasSpan(text, "加粗", "bold"));
});
test("nestedLooseEmphasisPairsIndependently", () => {
  const { text } = paragraphOf("**加粗 *斜体* 结束**接");
  assert.ok(!text.text.includes("*"));
  assert.ok(hasSpan(text, "结束", "bold"));
  assert.ok(hasSpan(text, "斜体", "italic"));
});
test("streamingPrefixAndFinalTextRenderTheSameBold", () => {
  const partial = paragraphOf("**它。", false);
  const complete = paragraphOf("**它。**但");
  assert.ok(hasSpan(partial.text, "它。", "bold"));
  assert.equal(complete.text.text, "它。但");
  assert.ok(hasSpan(complete.text, "它。", "bold"));
});
test("kbdTagRendersContentAsInlineCode", () => {
  const { text } = paragraphOf("按 <kbd>Ctrl</kbd> 继续");
  assert.ok(!text.text.includes("<"));
  assert.ok(text.text.includes("Ctrl"));
  assert.ok(hasSpan(text, "Ctrl", "code"));
});
