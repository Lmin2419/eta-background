// Ported from app/src/test/kotlin/io/github/mangi/eta/ui/markdown/MarkdownInlineTest.kt @ 3b1794d
// Copyright © 2026 蛮吉 (Mangi-11). PolyForm Noncommercial 1.0.0.
import assert from "node:assert/strict";
import {
  InlineSpan,
  MarkdownParagraph,
  MarkdownText,
} from "../../entry/src/main/ets/core/eta/markdown/markdownModel";
import { StreamingGfmParserSession } from "../../entry/src/main/ets/core/eta/markdown/streamingGfmParser";
export function paragraphOf(
  source: string,
  complete = true,
): MarkdownParagraph {
  const blocks = new StreamingGfmParserSession()
    .parse(source, complete)
    .document.blocks.filter((block) => block.kind === "paragraph");
  assert.equal(blocks.length, 1);
  return blocks[0];
}
export function hasSpan(
  text: MarkdownText,
  substring: string,
  kind: InlineSpan["kind"],
): boolean {
  const start = text.text.indexOf(substring);
  return (
    start >= 0 &&
    text.spans.some(
      (span) =>
        span.start <= start &&
        span.end >= start + substring.length &&
        span.kind === kind,
    )
  );
}
export function linkUrls(text: MarkdownText): string[] {
  return text.spans
    .filter((span) => span.kind === "link")
    .map((span) => span.url!);
}
