// Ported from app/src/test/kotlin/io/github/mangi/eta/agent/accessibility/TextEditPlannerTest.kt @ 3b1794d
import test from 'node:test';
import assert from 'node:assert/strict';
import {
  canSafelyReconstruct,
  insertAtSelection,
} from '../../../entry/src/main/ets/core/eta/accessibility/TextEditPlanner';

test('refuses to reconstruct password or unreadable populated input', () => {
  assert.equal(canSafelyReconstruct(true, true, 3, 1, 1), false);
  assert.equal(canSafelyReconstruct(false, false, 3, 3, 3), false);
  assert.equal(canSafelyReconstruct(false, true, 3, 3, 3), true);
  assert.equal(canSafelyReconstruct(false, true, 0, -1, -1), true);
});

test('inserts at cursor instead of always appending', () => {
  assert.deepEqual(insertAtSelection('ABC', 'X', 1, 1), { text: 'AXBC', cursor: 2 });
});

test('replaces selected range regardless of selection direction', () => {
  assert.deepEqual(insertAtSelection('ABC', 'X', 2, 1), { text: 'AXC', cursor: 2 });
});

test('uses UTF 16 offsets exposed by accessibility nodes', () => {
  assert.deepEqual(insertAtSelection('😀好', 'X', 2, 2), { text: '😀X好', cursor: 3 });
});

test('invalid selection on existing text is rejected instead of appending', () => {
  assert.equal(insertAtSelection('ABC', 'X', -1, -1), null);
});

test('partially invalid selection is rejected', () => {
  assert.equal(insertAtSelection('ABC', 'X', -1, 0), null);
});

test('empty text has one safe insertion point before cursor exists', () => {
  assert.deepEqual(insertAtSelection('', 'X', -1, -1), { text: 'X', cursor: 1 });
});
