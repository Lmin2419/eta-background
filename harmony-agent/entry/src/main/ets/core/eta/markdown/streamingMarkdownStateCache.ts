// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/markdown/StreamingMarkdownStateCache.kt @ 3b1794d
// Copyright © 2026 蛮吉 (Mangi-11). PolyForm Noncommercial 1.0.0.
import { StreamingMarkdownState } from "./streamingMarkdownState";
import {
  StaticMarkdownDocumentCache,
  staticMarkdownDocumentCache,
} from "./staticMarkdownDocumentCache";
export class StreamingMarkdownStateCache {
  readonly states = new Map<string, StreamingMarkdownState>();
  constructor(
    private staticCache: StaticMarkdownDocumentCache = staticMarkdownDocumentCache,
  ) {}
  getOrCreate(messageId: string): StreamingMarkdownState {
    let state = this.states.get(messageId);
    if (!state) {
      state = new StreamingMarkdownState();
      this.states.set(messageId, state);
    }
    return state;
  }
  unusedMessageIds(streamingIds: Set<string>): string[] {
    return [...this.states]
      .filter(
        ([id, state]) => !streamingIds.has(id) && !state.isAttachedToContent,
      )
      .map(([id]) => id);
  }
  discardUnused(streamingIds: Set<string>): void {
    for (const id of this.unusedMessageIds(streamingIds)) {
      const state = this.states.get(id);
      this.states.delete(id);
      if (state?.snapshot) this.staticCache.put(state.snapshot);
    }
  }
}
