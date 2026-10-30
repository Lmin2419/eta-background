// Ported from app/src/test/kotlin/io/github/mangi/eta/ui/components/HistoryScrollDemandTest.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import test from 'node:test';
import assert from 'node:assert/strict';
import { HistoryScrollDemand, ConversationHistoryLoadState } from '../../entry/src/main/ets/core/eta/chatHistoryPaging';
const ready = (d: HistoryScrollDemand, measured = true, index = 0, earlier = true, enabled = true, state: ConversationHistoryLoadState = 'Idle') => d.canLoad(measured, index, earlier, enabled, state);
test('initialLayoutAndRestoredPositionDoNotReadHistoryWithoutScrolling', () => { const d = new HistoryScrollDemand(); assert.equal(ready(d), false); assert.equal(ready(d, true, 2), false); });
test('approachingTheTopPrefetchesOnlyAfterTheListHasBeenMeasured', () => { const d = new HistoryScrollDemand(); d.onScroll(20); assert.equal(ready(d, false), false); assert.equal(ready(d, true, 10), false); assert.equal(ready(d, true, 3), true); });
test('completedPageAndLayoutChangesCannotChainAnotherRead', () => { const d = new HistoryScrollDemand(); d.onScroll(20); assert.equal(ready(d), true); d.consume(); assert.equal(ready(d, true, 0, true, true, 'Loading'), false); assert.equal(ready(d), false); d.onScroll(10); assert.equal(ready(d), true); });
test('failureWaitsForAnExplicitRetryAndBusyReadsAreNotDuplicated', () => { const d = new HistoryScrollDemand(); d.onScroll(20); assert.equal(ready(d, true, 0, true, true, 'Loading'), false); assert.equal(ready(d, true, 0, true, true, 'Failed'), false); d.consume(); assert.equal(ready(d), false); });
test('reversingDirectionWithdrawsUnusedPrefetchDemand', () => { const d = new HistoryScrollDemand(); d.onScroll(20); d.onScroll(-10); assert.equal(ready(d), false); });
test('endOfHistoryAndMessageMutationsPreventPrefetch', () => { const d = new HistoryScrollDemand(); d.onScroll(20); assert.equal(ready(d, true, 0, false), false); assert.equal(ready(d, true, 0, true, false), false); });
