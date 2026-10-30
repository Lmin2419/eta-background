// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/markdown/MarkdownDocumentBuilder.kt @ 3b1794d
// Copyright © 2026 蛮吉 (Mangi-11). PolyForm Noncommercial 1.0.0.
// Additional TS adapter checks, not included in the count of translated Eta tests.
import test from "node:test";
import assert from "node:assert/strict";
import { StreamingGfmParserSession } from "../../entry/src/main/ets/core/eta/markdown/streamingGfmParser";
import {
  inlineText,
  safeLinkUrl,
} from "../../entry/src/main/ets/core/eta/markdown/markdownInline";
test("TS block adapter: unchanged prefixes retain identity and theme changes invalidate them", () => {
  const parser = new StreamingGfmParserSession();
  const first = parser.parse("标题\n\n尾", false);
  const next = parser.parse("标题\n\n尾巴", false);
  assert.equal(first.document.blocks[0], next.document.blocks[0]);
  assert.notEqual(first.document.blocks[1], next.document.blocks[1]);
  const themed = parser.parse("标题\n\n尾巴", false, {
    linkColor: "other",
    codeBackground: "other",
  });
  assert.notEqual(themed.document.blocks[0], next.document.blocks[0]);
});
test("TS block adapter: aligned tables trim extra cells and fill missing cells", () => {
  const block = new StreamingGfmParserSession().parse(
    "| 左 | 中 | 右 |\n| :--- | :---: | ---: |\n| a | b | c | drop |\n| only |",
    true,
  ).document.blocks[0];
  assert.equal(block.kind, "table");
  if (block.kind !== "table") return;
  assert.deepEqual(block.alignments, ["Start", "Center", "End"]);
  assert.deepEqual(
    block.rows.map((row) => row.map((cell) => cell.text.text)),
    [
      ["a", "b", "c"],
      ["only", "", ""],
    ],
  );
});
test("TS block adapter: nested lists, task markers, quote and alert retain source identity", () => {
  const source =
    "- [x] done\n  - nested\n- [ ] pending\n\n> [!TIP]\n> **hint**\n\n> quoted";
  const blocks = new StreamingGfmParserSession().parse(source, true).document
    .blocks;
  assert.equal(blocks[0].kind, "list");
  if (blocks[0].kind !== "list") return;
  assert.deepEqual(
    blocks[0].items.map((item) => item.checked),
    [true, false],
  );
  assert.equal(blocks[0].items[0].blocks[1].kind, "list");
  assert.equal(blocks[0].items[0].markerRevealKey, 6);
  assert.equal(blocks[1].kind, "alert");
  assert.equal(blocks[2].kind, "quote");
});
test("TS block adapter: fence content retains trailing blank lines and literal markup", () => {
  const block = new StreamingGfmParserSession().parse(
    "```text\n**literal**\n\n\n```",
    true,
  ).document.blocks[0];
  assert.equal(block.kind, "code");
  if (block.kind === "code") assert.equal(block.text.text, "**literal**\n\n");
});
test("TS inline adapter: unsafe schemes stay plain and remote images become links", () => {
  assert.equal(safeLinkUrl("javascript:alert(1)"), null);
  assert.equal(safeLinkUrl(" data:text/html,test "), null);
  const unsafe = inlineText("[开](javascript:alert(1))");
  assert.equal(unsafe.text, "开");
  assert.equal(unsafe.spans.length, 0);
  const image = inlineText("![说明](https://example.com/image.png)");
  assert.equal(image.text, "说明");
  assert.equal(image.spans[0].url, "https://example.com/image.png");
});
test("TS inline adapter: escaped punctuation, entities and deliberate line breaks survive", () => {
  assert.equal(
    inlineText("a\\*b &amp; &#x1F600;\n第二行  \n第三行").text,
    "a*b & 😀\n第二行\n第三行",
  );
});
