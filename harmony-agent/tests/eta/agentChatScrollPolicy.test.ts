// Ported from app/src/test/kotlin/io/github/mangi/eta/ui/components/AgentChatScrollPolicyTest.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import test from 'node:test';
import assert from 'node:assert/strict';
import { resolveBottomFollowEnabled as follow, shouldRequestInitialBottom as initial, resolveKeepBottomAnchored as keep, resolveBottomFollowDecision as decision } from '../../entry/src/main/ets/core/eta/agentChatScrollPolicy';
test('networkCompletionKeepsFollowingUntilRenderedTailSettles', () => { assert.equal(follow(false, true, false, true), true); });
test('draggingInterruptsCompletionFollowing', () => { assert.equal(follow(false, true, true, true), false); });
test('completionDoesNotPullReaderBackFromHistory', () => { assert.equal(follow(false, false, false, true), false); });
test('completedContentExpansionDoesNotFollowBottom', () => { assert.equal(follow(false, true, false), false); });
test('streamingTailGrowthFollowsBottom', () => { assert.equal(follow(true, true, false), true); });
test('completedConversationDoesNotJumpToInitialBottom', () => { assert.equal(initial(false, true, false), false); });
test('streamingConversationRequestsInitialBottom', () => { assert.equal(initial(true, true, false), true); });
test('contentGrowthDoesNotDisableBottomFollowing', () => { assert.equal(keep(true, false, false), true); });
test('draggingAwayFromBottomDisablesFollowing', () => { assert.equal(keep(true, true, false), false); });
test('reachingBottomEnablesFollowingAgain', () => { assert.equal(keep(false, false, true), true); });
test('growingTailOnlyScrollsByTheOverflowDistance', () => { assert.deepEqual(decision(true, 8, 1024, 1000, 8), { scrollByPx: 24, requestIndex: null }); });
test('largeAppendRequestsBottomOnlyWhenSentinelLeftTheViewport', () => { assert.deepEqual(decision(true, 8, null, 1000, 6), { scrollByPx: 0, requestIndex: 8 }); });
test('disabledFollowingNeverMovesTheList', () => { assert.deepEqual(decision(false, 8, 1100, 1000, 8), { scrollByPx: 0, requestIndex: null }); });
