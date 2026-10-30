// Logical subset of app/src/test/kotlin/io/github/mangi/eta/ui/components/ChatHistoryPagingTest.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
// Robolectric Activity / actual LazyColumn measurement and pixel preservation remain device checks.
import test from 'node:test';
import assert from 'node:assert/strict';
import { ChatHistoryPagingController, PagingConfiguration, HistoryViewport } from '../../entry/src/main/ets/core/eta/chatHistoryPaging';
test('logic: actualUpwardScrollLoadsOnePageAndPreservesTheVisibleMessageOffset', () => {
  const entries = (start: number) => Array.from({ length: 150 - start }, (_, i) => 'message-' + (start + i));
  let config: PagingConfiguration = { entryKeys: entries(100), messageStartIndex: 100, messageCount: 50, hasEarlier: true, loadState: 'Idle', enabled: true, canReleasePages: true, keepBottomAnchored: true };
  const viewport: HistoryViewport = { measured: true, firstVisibleItemIndex: 2, visibleItems: [{ key: 'message-101', offset: -12 }], isScrollInProgress: false, canScrollForward: true };
  let requests = 0; const restored: number[][] = [];
  const paging = new ChatHistoryPagingController(config, {
    viewportChanged: () => {}, bottomAnchorChanged: value => { config = { ...config, keepBottomAnchored: value }; },
    loadEarlier: () => { requests++; config = { ...config, loadState: 'Loading' }; paging.configure(config, viewport); },
    requestScrollToItem: (index, offset) => restored.push([index, offset])
  });
  paging.maybeLoad(viewport); assert.equal(requests, 0);
  paging.configure(config, viewport); paging.maybeLoad(viewport); assert.equal(requests, 0);
  paging.onPreScroll(10, true, viewport); paging.maybeLoad(viewport); assert.equal(requests, 1);
  config = { ...config, entryKeys: entries(50), messageStartIndex: 50, messageCount: 100, loadState: 'Idle' }; paging.configure(config, viewport);
  for (let i = 0; i < 3; i++) paging.maybeLoad(viewport);
  assert.deepEqual(restored, [[52, 12]]); assert.equal(requests, 1);
});
