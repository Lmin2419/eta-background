// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/markdown/StreamingMarkdownState.kt @ 3b1794d
// Copyright © 2026 蛮吉 (Mangi-11). PolyForm Noncommercial 1.0.0.
import { defaultInlineStyle, MarkdownInlineStyle } from "./markdownModel";
import {
  StreamingGfmParserSession,
  StreamingGfmSnapshot,
} from "./streamingGfmParser";
import { StreamingMarkdownRestoreState } from "./streamingMarkdownRestoreState";
import { SmoothTextRevealCoordinator } from "./smoothTextReveal";
export interface StreamingMarkdownTarget {
  content: string;
  isStreaming: boolean;
  style: MarkdownInlineStyle;
}
export function markdownTarget(
  content: string,
  isStreaming: boolean,
  style = defaultInlineStyle,
): StreamingMarkdownTarget {
  return { content, isStreaming, style };
}
/** Single consumer, latest-value channel equivalent to Channel.CONFLATED. */
export class MarkdownTargets {
  private pending: StreamingMarkdownTarget | null = null;
  private closed = false;
  private wakeup: (() => void) | null = null;
  send(target: StreamingMarkdownTarget): void {
    if (this.closed) throw new Error("Markdown targets are closed");
    this.pending = target;
    this.wakeup?.();
    this.wakeup = null;
  }
  close(): void {
    this.closed = true;
    this.wakeup?.();
    this.wakeup = null;
  }
  tryReceive(): StreamingMarkdownTarget | null {
    const value = this.pending;
    this.pending = null;
    return value;
  }
  async receive(): Promise<StreamingMarkdownTarget | null> {
    if (!this.pending && !this.closed)
      await new Promise<void>((resolve) => {
        this.wakeup = resolve;
      });
    return this.tryReceive();
  }
}
export class MarkdownParseCancellation {
  cancelled = false;
  private resolveCancel: (() => void) | null = null;
  readonly signal = new Promise<null>((resolve) => {
    this.resolveCancel = () => resolve(null);
  });
  cancel(): void {
    this.cancelled = true;
    this.resolveCancel?.();
    this.resolveCancel = null;
  }
}
function sameTarget(
  a: StreamingMarkdownTarget,
  b: StreamingMarkdownTarget,
): boolean {
  return (
    a.content === b.content &&
    a.isStreaming === b.isStreaming &&
    a.style.linkColor === b.style.linkColor &&
    a.style.codeBackground === b.style.codeBackground
  );
}
export async function consumeStreamingMarkdownTargets(
  targets: MarkdownTargets,
  parse: (target: StreamingMarkdownTarget) => Promise<StreamingGfmSnapshot>,
  publish: (snapshot: StreamingGfmSnapshot) => void,
  cancellation = new MarkdownParseCancellation(),
): Promise<void> {
  let target = await Promise.race([targets.receive(), cancellation.signal]);
  while (target !== null && !cancellation.cancelled) {
    target = targets.tryReceive() ?? target;
    const parsed = await Promise.race([parse(target), cancellation.signal]);
    if (parsed === null || cancellation.cancelled) return;
    const newer = targets.tryReceive();
    // Append traffic may be faster than parsing: publish useful prefixes instead of starving UI.
    if (
      newer === null ||
      (newer.content.startsWith(parsed.originalSource) &&
        (!parsed.isComplete || sameTarget(newer, target)))
    )
      publish(parsed);
    target =
      newer ?? (await Promise.race([targets.receive(), cancellation.signal]));
  }
}
export class StreamingMarkdownState {
  private attached = 0;
  revealedContent: string | null = null;
  snapshot: StreamingGfmSnapshot | null = null;
  private parser = new StreamingGfmParserSession();
  readonly revealCoordinator = new SmoothTextRevealCoordinator();
  readonly restoreState = new StreamingMarkdownRestoreState();
  readonly parseTargets = new MarkdownTargets();
  constructor() {
    this.revealCoordinator.pauseAnimationsAndCatchUp();
  }
  get isAttachedToContent(): boolean {
    return this.attached > 0;
  }
  attachContent(): void {
    this.attached++;
  }
  detachContent(): void {
    if (this.attached <= 0) throw new Error("Unbalanced markdown detach");
    this.attached--;
  }
  parseUpdates(
    cancellation = new MarkdownParseCancellation(),
    parse?: (target: StreamingMarkdownTarget) => Promise<StreamingGfmSnapshot>,
  ): Promise<void> {
    return consumeStreamingMarkdownTargets(
      this.parseTargets,
      parse ??
        (async (target) =>
          this.parser.parse(target.content, !target.isStreaming, target.style)),
      (snapshot) => {
        this.snapshot = snapshot;
      },
      cancellation,
    );
  }
}
