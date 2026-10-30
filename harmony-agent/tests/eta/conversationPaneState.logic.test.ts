// Logical subset of app/src/test/kotlin/io/github/mangi/eta/ui/components/ConversationPaneStateTest.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
// Compose animation cancellation/frame-clock assertions are NOT claimed as ported.
import test from 'node:test';
import assert from 'node:assert/strict';
import { ConversationPaneAnchor, ConversationPaneSettledValues } from '../../entry/src/main/ets/core/eta/conversationPaneState';
function verify(initial: ConversationPaneAnchor, target: ConversationPaneAnchor): void {
  const observer = new ConversationPaneSettledValues(), values: ConversationPaneAnchor[] = [];
  const position = (a: ConversationPaneAnchor) => a === 'Open' ? 320 : 0;
  let visible = initial === 'Open';
  const update = (settledValue: ConversationPaneAnchor, offset: number, isAnimationRunning = false, isDragging = false) => {
    const next = observer.update({ settledValue, settledPosition: position(settledValue), offset, isAnimationRunning, isDragging });
    if (next !== null) { values.push(next); visible = next === 'Open'; }
  };
  update(initial, position(initial)); visible = target === 'Open';
  update(initial, 32, true); update(initial, position(initial), false, true);
  assert.deepEqual(values, [initial]); assert.equal(visible, target === 'Open');
  update(initial, position(initial)); assert.deepEqual(values, [initial, initial]); assert.equal(visible, initial === 'Open');
  visible = target === 'Open'; update(target, position(target)); assert.deepEqual(values, [initial, initial, target]); assert.equal(visible, target === 'Open');
}
test('logic: interruptedOpeningResynchronizesClosedAndCanOpenAgain', () => verify('Closed', 'Open'));
test('logic: interruptedClosingResynchronizesOpenAndCanCloseAgain', () => verify('Open', 'Closed'));
