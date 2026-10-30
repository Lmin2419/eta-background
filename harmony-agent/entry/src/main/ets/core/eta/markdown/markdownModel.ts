// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/markdown/MarkdownModel.kt @ 3b1794d
// Copyright © 2026 蛮吉 (Mangi-11). PolyForm Noncommercial 1.0.0.
export interface MarkdownInlineStyle {
  linkColor: string;
  codeBackground: string;
}
// Resource roles, resolved by the ArkUI renderer; no platform colors in the parser.
export const defaultInlineStyle: MarkdownInlineStyle = {
  linkColor: "primary",
  codeBackground: "secondaryContainer",
};
export interface InlineSpan {
  start: number;
  end: number;
  kind: "bold" | "italic" | "strike" | "code" | "link";
  url?: string;
}
export interface MarkdownText {
  text: string;
  spans: InlineSpan[];
}
export interface MarkdownParagraph {
  kind: "paragraph";
  offset: number;
  text: MarkdownText;
}
export interface MarkdownHeading {
  kind: "heading";
  offset: number;
  level: number;
  text: MarkdownText;
}
export interface MarkdownCode {
  kind: "code";
  offset: number;
  language: string | null;
  text: MarkdownText;
  isMath: boolean;
}
export interface MarkdownQuote {
  kind: "quote";
  offset: number;
  blocks: MarkdownBlock[];
}
export interface MarkdownAlert {
  kind: "alert";
  offset: number;
  alertKind: "Note" | "Tip" | "Important" | "Warning" | "Caution";
  blocks: MarkdownBlock[];
}
export interface MarkdownListItem {
  offset: number;
  number: number | null;
  checked: boolean | null;
  blocks: MarkdownBlock[];
  markerRevealKey: number | null;
}
export interface MarkdownList {
  kind: "list";
  offset: number;
  ordered: boolean;
  loose: boolean;
  items: MarkdownListItem[];
}
export interface MarkdownTableCell {
  kind: "cell";
  offset: number;
  text: MarkdownText;
}
export interface MarkdownTable {
  kind: "table";
  offset: number;
  alignments: Array<"Start" | "Center" | "End">;
  header: MarkdownTableCell[];
  rows: MarkdownTableCell[][];
}
export interface MarkdownRule {
  kind: "rule";
  offset: number;
}
export type MarkdownBlock =
  | MarkdownParagraph
  | MarkdownHeading
  | MarkdownCode
  | MarkdownQuote
  | MarkdownAlert
  | MarkdownList
  | MarkdownTable
  | MarkdownRule;
export interface MarkdownDocument {
  blocks: MarkdownBlock[];
  revealKeys: Set<number>;
  inlineStyle: MarkdownInlineStyle;
}
export function plainText(text: string): MarkdownText {
  return { text, spans: [] };
}
export function revealKeys(blocks: MarkdownBlock[]): Set<number> {
  const keys = new Set<number>();
  for (const block of blocks) {
    if ("text" in block) {
      if (block.text.text.length) keys.add(block.offset);
    } else if (block.kind === "quote" || block.kind === "alert") {
      for (const key of revealKeys(block.blocks)) keys.add(key);
    } else if (block.kind === "list") {
      for (const item of block.items)
        for (const key of revealKeys(item.blocks)) keys.add(key);
    } else if (block.kind === "table") {
      for (const cell of [...block.header, ...block.rows.flat()])
        if (cell.text.text.length) keys.add(cell.offset);
    }
  }
  return keys;
}
export function firstRevealKey(blocks: MarkdownBlock[]): number | null {
  return revealKeys(blocks).values().next().value ?? null;
}
