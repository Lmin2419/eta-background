// Ported from app/src/test/kotlin/io/github/mangi/eta/agent/accessibility/AccessibilityNodeIdentityTest.kt @ 3b1794d
import test from 'node:test';
import assert from 'node:assert/strict';
import {
  AccessibilityNodeIdentity,
  canBypassContentChange,
} from '../../../entry/src/main/ets/core/eta/accessibility/AccessibilityNodeIdentity';

function identity(uniqueId = '', text = '条目', description = ''): AccessibilityNodeIdentity {
  return new AccessibilityNodeIdentity(
    uniqueId,
    7,
    'example.app',
    'android.widget.TextView',
    'example.app:id/row',
    text,
    description,
    false,
  );
}

test('same view id with recycled row text is not the same node', () => {
  assert.equal(identity('', '第一行').matches(identity('', '插入后的新行')), false);
});

test('unique id cannot hide changed action semantics', () => {
  const original = identity('virtual-42', '旧状态');
  assert.equal(original.matches(identity('virtual-42', '新状态')), false);
  assert.equal(original.matches(identity('virtual-42', '旧状态')), true);
});

test('blank semantic node is weak when window content changes', () => {
  assert.equal(identity('', '', '').strong, false);
  assert.equal(identity('', '按钮', '').strong, true);
});

test('identity gaining a unique id is treated as replacement', () => {
  assert.equal(identity('', '条目').matches(identity('virtual-42', '条目')), false);
});

test('truncated snapshot cannot promote semantic identity to globally unique', () => {
  assert.equal(canBypassContentChange(false, true, 1), false);
  assert.equal(canBypassContentChange(true, true, 1), true);
});

test('complete snapshot requires exactly one semantic identity match', () => {
  assert.equal(canBypassContentChange(false, false, 1), true);
  assert.equal(canBypassContentChange(false, false, 2), false);
});
