import test from 'node:test';
import assert from 'node:assert/strict';
import { fingerprint, renderKey, syncSlots, Slot } from '../../entry/src/main/ets/core/eta/renderKey';

test('renderKey changes when the drawn content changes and is stable otherwise', () => {
  const row = { title: '会话 A', subtitle: '昨天' };
  assert.equal(renderKey('c1', row), renderKey('c1', { title: '会话 A', subtitle: '昨天' }));
  assert.notEqual(renderKey('c1', row), renderKey('c1', { ...row, title: '会话 B' }));
  assert.notEqual(renderKey('c1', row), renderKey('c2', row));
  assert.ok(renderKey('c1', row).startsWith('c1#'));
});

test('fingerprint distinguishes growing streamed text and handles undefined', () => {
  const seen = new Set<string>();
  const full = '这是预览数据。回复会逐段显示，也可以点击停止。';
  for (let i = 0; i <= full.length; i++) seen.add(fingerprint(full.slice(0, i)));
  assert.equal(seen.size, full.length + 1);
  assert.equal(typeof fingerprint(undefined), 'string');
});

interface Item { id: string; text: string; }
const slot = (value: Item): Slot<Item> => ({ value });
const id = (item: Item) => item.id;

test('syncSlots keeps the slot object for a stable id and replaces its value in place', () => {
  const first = syncSlots<Item, Slot<Item>>([], [{ id: 'a', text: '这' }, { id: 'b', text: 'x' }], id, slot);
  const before = first[0];
  const next = syncSlots(first, [{ id: 'a', text: '这是预览' }, { id: 'b', text: 'x' }], id, slot);
  assert.equal(next[0], before, 'same slot object, so @ObjectLink children are updated, not recreated');
  assert.equal(next[0].value.text, '这是预览');
});

test('syncSlots leaves equal content untouched, adds new ids, drops removed ids and follows the new order', () => {
  const b = { id: 'b', text: 'x' };
  const first = syncSlots<Item, Slot<Item>>([], [{ id: 'a', text: '1' }, b], id, slot);
  const kept = first[1].value;
  const next = syncSlots(first, [{ id: 'c', text: 'new' }, { id: 'b', text: 'x' }], id, slot);
  assert.deepEqual(next.map(s => s.value.id), ['c', 'b']);
  assert.equal(next[1], first[1]);
  assert.equal(next[1].value, kept, 'an equal value is not reassigned, so the row does not re-render');
});
