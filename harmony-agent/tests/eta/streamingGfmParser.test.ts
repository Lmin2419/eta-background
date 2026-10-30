// Ported from app/src/test/kotlin/io/github/mangi/eta/ui/markdown/StreamingGfmParserTest.kt @ 3b1794d
// Copyright © 2026 蛮吉 (Mangi-11). PolyForm Noncommercial 1.0.0.
import test from "node:test";
import assert from "node:assert/strict";
import { StreamingGfmParserSession } from "../../entry/src/main/ets/core/eta/markdown/streamingGfmParser";
import { projectStreamingGfm } from "../../entry/src/main/ets/core/eta/markdown/streamingGfmProjection";
import { hasSpan, linkUrls } from "./markdownFixtures";
const parse = (source: string, complete: boolean) =>
  new StreamingGfmParserSession().parse(source, complete);
test("headingIsParsedAsHeadingFromFirstStreamingSnapshot", () => {
  const snapshot = parse("## 标题", false);
  assert.equal(snapshot.renderedSource, "## 标题");
  const heading = snapshot.document.blocks[0];
  assert.equal(snapshot.document.blocks.length, 1);
  assert.equal(heading.kind, "heading");
  if (heading.kind !== "heading") return;
  assert.equal(heading.level, 2);
  assert.equal(heading.text.text, "标题");
});
test("tableHeaderIsBufferedUntilDelimiterConfirmsTheBlock", () => {
  assert.equal(projectStreamingGfm("| 指标 | 数值 |", false), "");
  assert.equal(projectStreamingGfm("| 指标 | 数值 |\n| --", false), "");
  const confirmed = "| 指标 | 数值 |\n| --- | --- |";
  const snapshot = parse(confirmed, false);
  assert.equal(snapshot.renderedSource, confirmed);
  const table = snapshot.document.blocks[0];
  assert.equal(snapshot.document.blocks.length, 1);
  assert.equal(table.kind, "table");
  if (table.kind === "table")
    assert.deepEqual(
      table.header.map((cell) => cell.text.text),
      ["指标", "数值"],
    );
});
test("ordinaryPipeTextIsReleasedWhenNextLineCannotBeATableDelimiter", () => {
  const source = "请选择 A | B\n这不是分隔行";
  assert.equal(projectStreamingGfm(source, false), source);
});
test("incompleteStrongDelimiterUsesVirtualEofClosure", () => {
  const snapshot = parse("说明 **压力", false);
  assert.equal(snapshot.renderedSource, "说明 **压力**");
  const block = snapshot.document.blocks[0];
  assert.equal(block.kind, "paragraph");
  if (block.kind === "paragraph")
    assert.ok(hasSpan(block.text, "压力", "bold"));
});
test("incompleteInlineCodeUsesVirtualEofClosure", () => {
  const snapshot = parse("执行 `adb shell", false);
  assert.equal(snapshot.renderedSource, "执行 `adb shell`");
  const block = snapshot.document.blocks[0];
  assert.equal(block.kind, "paragraph");
  if (block.kind === "paragraph")
    assert.ok(hasSpan(block.text, "adb shell", "code"));
});
test("incompleteLinkIsNotPublishedAsRawMarkdown", () => {
  const snapshot = parse("参考 [官方文档](https://example.com/do", false);
  assert.equal(snapshot.renderedSource, "参考 ");
  assert.ok(!snapshot.renderedSource.includes("["));
});
test("openFenceRendersAsCodeWithoutChangingStoredSource", () => {
  const source = "```kotlin\nval answer = 42";
  const snapshot = parse(source, false);
  assert.equal(snapshot.originalSource, source);
  assert.ok(snapshot.renderedSource.endsWith("\n```"));
  const code = snapshot.document.blocks[0];
  assert.equal(code.kind, "code");
  if (code.kind === "code") {
    assert.equal(code.language, "kotlin");
    assert.equal(code.text.text, "val answer = 42");
  }
});
test("completionParsesExactOriginalSourceWithoutVirtualCharacters", () => {
  const source = "未闭合 **标记";
  const snapshot = parse(source, true);
  assert.equal(snapshot.renderedSource, source);
  assert.ok(snapshot.isComplete);
});
test("stableHandoffKeepsTheCompletedDocumentAndFormattedBlocks", () => {
  const parser = new StreamingGfmParserSession();
  parser.parse("## 结果\n\n```kotlin\nval answer = 42", false);
  const content = "## 结果\n\n```kotlin\nval answer = 42\n```\n\n**完成**";
  const snapshot = parser.parse(content, true);
  const stable = snapshot.completedDocumentFor(content);
  assert.ok(stable);
  assert.equal(stable, snapshot.document);
  assert.ok(
    stable.blocks.some(
      (block) => block.kind === "heading" && block.level === 2,
    ),
  );
  assert.ok(
    stable.blocks.some(
      (block) => block.kind === "code" && block.language === "kotlin",
    ),
  );
  const paragraphs = stable.blocks.filter(
    (block) => block.kind === "paragraph",
  );
  assert.equal(paragraphs.length, 1);
  assert.ok(hasSpan(paragraphs[0].text, "完成", "bold"));
});
test("unfinishedOrOutdatedSnapshotCannotBeUsedForStableHandoff", () => {
  const parser = new StreamingGfmParserSession();
  const content = "回复内容";
  assert.equal(
    parser.parse(content, false).completedDocumentFor(content),
    null,
  );
  const completed = parser.parse(content, true);
  assert.equal(completed.completedDocumentFor("回复内容和新补充"), null);
  assert.equal(completed.completedDocumentFor("修正后的内容"), null);
});
test("completedSnapshotIncludesReferenceLinksBeforeStableHandoff", () => {
  const snapshot = parse(
    "查看 [文档][eta]\n\n[eta]: https://example.com/docs",
    true,
  );
  assert.equal(snapshot.document.blocks.length, 1);
  const paragraph = snapshot.document.blocks[0];
  assert.equal(paragraph.kind, "paragraph");
  if (paragraph.kind === "paragraph") {
    assert.equal(paragraph.text.text, "查看 文档");
    assert.deepEqual(linkUrls(paragraph.text), ["https://example.com/docs"]);
  }
});
test("correctedSnapshotDoesNotRetainOrMutatePreviousLinkDefinitions", () => {
  const parser = new StreamingGfmParserSession();
  const original = parser.parse(
    "[文档][eta]\n\n[eta]: https://example.com/docs",
    true,
  );
  const replacement = parser.parse("[文档][eta]", true);
  const paragraph = replacement.document.blocks[0];
  assert.equal(paragraph.kind, "paragraph");
  if (paragraph.kind === "paragraph") {
    assert.equal(paragraph.text.text, "[文档][eta]");
    assert.deepEqual(linkUrls(paragraph.text), []);
  }
  const old = original.document.blocks[0];
  assert.equal(old.kind, "paragraph");
  if (old.kind === "paragraph")
    assert.deepEqual(linkUrls(old.text), ["https://example.com/docs"]);
});
