// Ported from app/src/test/kotlin/io/github/mangi/eta/ui/markdown/StaticMarkdownDocumentCacheTest.kt @ 3b1794d
// Copyright © 2026 蛮吉 (Mangi-11). PolyForm Noncommercial 1.0.0.
import test from "node:test";
import assert from "node:assert/strict";
import { StaticMarkdownDocumentCache } from "../../entry/src/main/ets/core/eta/markdown/staticMarkdownDocumentCache";
import { defaultInlineStyle as style } from "../../entry/src/main/ets/core/eta/markdown/markdownModel";
import { StreamingGfmParserSession } from "../../entry/src/main/ets/core/eta/markdown/streamingGfmParser";
const cache = () => new StaticMarkdownDocumentCache(100, 4, 60);
test("smallDocumentsRespectTheEntryBudgetAndLeastRecentlyUsedOrder", () => {
  const c = cache();
  const first = c.parse("第一条", style);
  for (const value of ["第二条", "第三条", "第四条"]) c.parse(value, style);
  assert.equal(c.get("第一条", style), first);
  c.parse("第五条", style);
  assert.equal(c.get("第一条", style), first);
  assert.equal(c.get("第二条", style), null);
  for (const value of ["第三条", "第四条", "第五条"])
    assert.ok(c.get(value, style));
});
test("totalSourceCharactersEvictBeforeTheEntryLimitIsReached", () => {
  const c = cache();
  const first = "甲".repeat(40);
  const second = "乙".repeat(40);
  const third = "丙".repeat(40);
  c.parse(first, style);
  const retained = c.parse(second, style);
  const latest = c.parse(third, style);
  assert.equal(c.get(first, style), null);
  assert.equal(c.get(second, style), retained);
  assert.equal(c.get(third, style), latest);
});
test("oversizedStaticDocumentIsParsedWithoutDisplacingSmallerDocuments", () => {
  const c = cache();
  const small = c.parse("保留的短回复", style);
  const oversized = "长".repeat(61);
  const rendered = c.parse(oversized, style);
  assert.equal(rendered.blocks.length, 1);
  assert.equal(rendered.blocks[0].kind, "paragraph");
  if (rendered.blocks[0].kind === "paragraph")
    assert.equal(rendered.blocks[0].text.text, oversized);
  assert.equal(c.get(oversized, style), null);
  assert.equal(c.get("保留的短回复", style), small);
});
test("streamingHandoffUsesTheSameSingleDocumentAdmissionLimit", () => {
  const c = cache();
  const small = c.parse("流式交接前的小文档", style);
  const parser = new StreamingGfmParserSession();
  const oversized = parser.parse("大".repeat(61), true);
  c.put(oversized);
  assert.equal(c.get(oversized.originalSource, style), null);
  assert.equal(c.get("流式交接前的小文档", style), small);
  const accepted = parser.parse("界".repeat(60), true);
  c.put(accepted);
  assert.equal(c.get(accepted.originalSource, style), accepted.document);
});
