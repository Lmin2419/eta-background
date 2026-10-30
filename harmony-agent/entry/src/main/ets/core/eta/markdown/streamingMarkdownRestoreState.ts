// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/markdown/StreamingMarkdownRestoreState.kt @ 3b1794d
// Copyright © 2026 蛮吉 (Mangi-11). PolyForm Noncommercial 1.0.0.
export class StreamingMarkdownRestoreState {
  generation = 0;
  private baseline: string | null = null;
  begin(content: string): void {
    this.generation++;
    this.baseline = content;
  }
  pause(): void {
    this.generation++;
    this.baseline = null;
  }
  completeLayout(
    generation: number,
    renderedContent: string,
    currentContent: string,
  ): boolean {
    if (this.baseline === null || generation !== this.generation) return false;
    if (
      renderedContent !== currentContent &&
      !(
        renderedContent.startsWith(this.baseline) &&
        currentContent.startsWith(renderedContent)
      )
    )
      return false;
    this.baseline = null;
    return true;
  }
}
export function isStreamingMarkdownTargetComplete(
  content: string,
  isStreaming: boolean,
  snapshotContent: string | null,
  snapshotComplete: boolean,
): boolean {
  return !isStreaming && snapshotComplete && snapshotContent === content;
}
