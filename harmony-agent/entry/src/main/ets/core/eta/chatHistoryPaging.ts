// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/components/ChatHistoryPaging.kt
// and ui/model/ConversationHistoryLoadState.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
// Compose callbacks/effects are exposed as explicit viewport transitions for ArkUI to drive.
export const ChatHistoryBoundaryKey = 'agent-chat-earlier-messages';
export const ChatBottomSentinelKey = 'agent-chat-bottom-sentinel';
export type ConversationHistoryLoadState = 'Idle' | 'Loading' | 'Failed';
export class HistoryScrollDemand {
  private pending: boolean = false;
  onScroll(verticalDistance: number): void { if (verticalDistance !== 0) this.pending = verticalDistance > 0; }
  canLoad(measured: boolean, firstVisibleItemIndex: number, hasEarlier: boolean, enabled: boolean, loadState: ConversationHistoryLoadState): boolean { return this.pending && measured && firstVisibleItemIndex <= 3 && hasEarlier && enabled && loadState === 'Idle'; }
  consume(): void { this.pending = false; }
}
export interface PagingConfiguration { entryKeys: string[]; messageStartIndex: number; messageCount: number; hasEarlier: boolean; loadState: ConversationHistoryLoadState; enabled: boolean; canReleasePages: boolean; keepBottomAnchored: boolean; }
export interface VisibleHistoryItem { key: string; offset: number; }
export interface HistoryViewport { measured: boolean; firstVisibleItemIndex: number; visibleItems: VisibleHistoryItem[]; isScrollInProgress: boolean; canScrollForward: boolean; }
export interface PagingActions { loadEarlier(): void; bottomAnchorChanged(value: boolean): void; viewportChanged(atLatest: boolean): void; requestScrollToItem(index: number, offset: number): void; }
interface ReadingAnchor { key: string; offset: number; startIndex: number; }
export class ChatHistoryPagingController {
  private demand = new HistoryScrollDemand();
  private anchor: ReadingAnchor | null = null;
  private latest: boolean | undefined = undefined;
  private lastCount: number = -1;
  constructor(private configuration: PagingConfiguration, private actions: PagingActions) {}
  private capture(viewport: HistoryViewport): ReadingAnchor | null {
    const item = viewport.visibleItems.find(x => x.key !== ChatHistoryBoundaryKey && x.key !== ChatBottomSentinelKey);
    return item ? { ...item, startIndex: this.configuration.messageStartIndex } : null;
  }
  onPreScroll(distance: number, fromUser: boolean, viewport: HistoryViewport): void {
    if (fromUser) { this.demand.onScroll(distance); if (distance > 0) this.actions.viewportChanged(false); }
  }
  onPostScroll(consumed: number, viewport: HistoryViewport): void {
    this.demand.onScroll(consumed); if (this.anchor !== null) this.anchor = this.capture(viewport);
  }
  requestEarlier(viewport: HistoryViewport): void {
    const c = this.configuration;
    if (c.hasEarlier && c.enabled && c.loadState !== 'Loading') {
      this.demand.consume(); this.anchor = this.capture(viewport);
      this.actions.viewportChanged(false); this.actions.bottomAnchorChanged(false); this.actions.loadEarlier();
    }
  }
  maybeLoad(viewport: HistoryViewport): void {
    const c = this.configuration;
    if (this.demand.canLoad(viewport.measured, viewport.firstVisibleItemIndex, c.hasEarlier, c.enabled, c.loadState)) this.requestEarlier(viewport);
  }
  configure(configuration: PagingConfiguration, viewport: HistoryViewport): void {
    this.configuration = configuration;
    const anchor = this.anchor;
    if (anchor && configuration.messageStartIndex < anchor.startIndex) {
      this.anchor = null;
      if (!viewport.isScrollInProgress && !configuration.keepBottomAnchored) {
        const index = configuration.entryKeys.indexOf(anchor.key);
        if (index >= 0) this.actions.requestScrollToItem(index + (configuration.hasEarlier ? 1 : 0), -anchor.offset);
      }
    }
  }
  notifyViewport(viewport: HistoryViewport): void {
    const c = this.configuration;
    const atLatest = c.enabled && c.canReleasePages && c.keepBottomAnchored && viewport.measured && !viewport.canScrollForward && !viewport.isScrollInProgress;
    if (this.latest !== atLatest || this.lastCount !== c.messageCount) {
      this.latest = atLatest; this.lastCount = c.messageCount; this.actions.viewportChanged(atLatest);
    }
  }
}
