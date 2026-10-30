// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/markdown/StaticMarkdownDocumentCache.kt @ 3b1794d
// Copyright © 2026 蛮吉 (Mangi-11). PolyForm Noncommercial 1.0.0.
import { MarkdownDocument, MarkdownInlineStyle } from "./markdownModel";
import {
  StreamingGfmParserSession,
  StreamingGfmSnapshot,
} from "./streamingGfmParser";
interface Entry {
  document: MarkdownDocument;
  weight: number;
}
export class StaticMarkdownDocumentCache {
  private cache = new Map<string, Entry>();
  private weight = 0;
  private minimumWeight: number;
  constructor(
    private sourceCharBudget = 512 * 1024,
    maxEntries = 96,
    private maxDocumentSourceChars = Math.max(
      1,
      Math.floor(sourceCharBudget / 4),
    ),
  ) {
    if (
      sourceCharBudget <= 0 ||
      maxEntries <= 0 ||
      maxDocumentSourceChars < 1 ||
      maxDocumentSourceChars > sourceCharBudget
    )
      throw new Error("Invalid markdown cache budget");
    this.minimumWeight = Math.ceil(sourceCharBudget / maxEntries);
  }
  get(content: string, style: MarkdownInlineStyle): MarkdownDocument | null {
    const key = this.key(content, style);
    const entry = this.cache.get(key);
    if (!entry) return null;
    this.cache.delete(key);
    this.cache.set(key, entry);
    return entry.document;
  }
  put(snapshot: StreamingGfmSnapshot): void {
    if (snapshot.isComplete)
      this.admit(snapshot.originalSource, snapshot.document);
  }
  parse(content: string, style: MarkdownInlineStyle): MarkdownDocument {
    const document = new StreamingGfmParserSession().parse(
      content,
      true,
      style,
    ).document;
    this.admit(content, document);
    return document;
  }
  private key(content: string, style: MarkdownInlineStyle): string {
    return JSON.stringify([content, style.linkColor, style.codeBackground]);
  }
  private admit(content: string, document: MarkdownDocument): void {
    if (content.length > this.maxDocumentSourceChars) return;
    const key = this.key(content, document.inlineStyle);
    const old = this.cache.get(key);
    if (old) {
      this.cache.delete(key);
      this.weight -= old.weight;
    }
    const weight = Math.max(content.length, this.minimumWeight);
    this.cache.set(key, { document, weight });
    this.weight += weight;
    while (this.weight > this.sourceCharBudget) {
      const first = this.cache.entries().next().value;
      if (!first) break;
      this.cache.delete(first[0]);
      this.weight -= first[1].weight;
    }
  }
}
export const staticMarkdownDocumentCache = new StaticMarkdownDocumentCache();
