// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/markdown/StreamingGfmParser.kt @ 3b1794d
// Copyright © 2026 蛮吉 (Mangi-11). PolyForm Noncommercial 1.0.0.
import {
  defaultInlineStyle,
  MarkdownDocument,
  MarkdownInlineStyle,
} from "./markdownModel";
import { MarkdownDocumentBuilder } from "./markdownDocumentBuilder";
import { projectStreamingGfm } from "./streamingGfmProjection";
import { normalizeTexMath } from "./texMathDelimiters";
export class StreamingGfmSnapshot {
  constructor(
    readonly originalSource: string,
    readonly renderedSource: string,
    readonly isComplete: boolean,
    readonly document: MarkdownDocument,
  ) {}
  completedDocumentFor(content: string): MarkdownDocument | null {
    return this.isComplete && this.originalSource === content
      ? this.document
      : null;
  }
}
export class StreamingGfmParserSession {
  private builder = new MarkdownDocumentBuilder();
  parse(
    source: string,
    isComplete: boolean,
    style: MarkdownInlineStyle = defaultInlineStyle,
  ): StreamingGfmSnapshot {
    const rendered = projectStreamingGfm(normalizeTexMath(source), isComplete);
    return new StreamingGfmSnapshot(
      source,
      rendered,
      isComplete,
      this.builder.build(rendered, isComplete, style),
    );
  }
}
