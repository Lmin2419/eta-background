// Ported from app/src/test/kotlin/io/github/mangi/eta/ui/components/ChatConversationKeyTest.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import test from 'node:test';
import assert from 'node:assert/strict';
import { chatConversationCompositionKey as key } from '../../entry/src/main/ets/core/eta/chatConversationKey';
test('sameConversationKeepsTheSameCompositionKey', () => { assert.equal(key('conversation-1'), key('conversation-1')); });
test('switchingConversationChangesTheCompositionKey', () => { assert.notEqual(key('conversation-1'), key('conversation-2')); });
test('draftConversationHasAStableNonNullKey', () => { assert.equal(key(null), 'conversation:draft'); });
