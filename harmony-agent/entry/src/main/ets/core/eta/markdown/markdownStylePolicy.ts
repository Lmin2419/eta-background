// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/markdown/MarkdownStyle.kt and MarkdownContent.kt @ 3b1794d
// Copyright © 2026 蛮吉 (Mangi-11). PolyForm Noncommercial 1.0.0.
import { MarkdownBlock } from "./markdownModel";
export type MarkdownTone = "Answer" | "Thinking";
export function markdownBlockGap(
  previous: MarkdownBlock | null,
  current: MarkdownBlock,
  tone: MarkdownTone,
): number {
  if (!previous) return 0;
  const gap =
    current.kind === "heading" && previous.kind === "heading"
      ? 8
      : current.kind === "heading"
        ? current.level <= 2
          ? 22
          : 18
        : previous.kind === "heading"
          ? 8
          : previous.kind === "paragraph" && current.kind === "paragraph"
            ? 12
            : 14;
  return gap * (tone === "Answer" ? 1 : 0.75);
}
export function markdownCompactGap(
  previous: MarkdownBlock | null,
  tone: MarkdownTone,
): number {
  return previous === null ? 0 : tone === "Answer" ? 4 : 3;
}
export function listMarkerVisible(
  revealActive: boolean,
  markerKey: number | null,
  started: Set<number> | null,
): boolean {
  return (
    !revealActive || (markerKey !== null && started?.has(markerKey) === true)
  );
}
