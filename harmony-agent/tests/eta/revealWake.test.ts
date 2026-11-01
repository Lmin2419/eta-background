import test from 'node:test';
import assert from 'node:assert/strict';
import { SmoothTextRevealCoordinator } from '../../entry/src/main/ets/core/eta/markdown/smoothTextReveal';

// The frame loop can stop (coordinator drained) one frame before a newly created block registers its
// text. The coordinator must then ask its owner to restart frames, or that block stays transparent.
const node = { onRevealDataChanged: () => {} };
const drain = (coordinator: SmoothTextRevealCoordinator) => { for (let i = 0; i < 2000 && !coordinator.drained; i++) coordinator.advanceFrame(0.016); };

test('a block that registers text after the coordinator drained wakes the frame loop', () => {
  const coordinator = new SmoothTextRevealCoordinator();
  let wakes = 0;
  coordinator.onPending = () => { wakes++; };
  coordinator.attach(0, node, '第一段', { identity: '0' });
  assert.equal(wakes, 1);
  drain(coordinator);
  assert.equal(coordinator.drained, true);
  // The last list item appears after the loop stopped.
  coordinator.attach(64, node, '列表第二项', { identity: '64' });
  assert.equal(coordinator.drained, false);
  assert.equal(wakes, 2);
  drain(coordinator);
  assert.equal(coordinator.drained, true);
});

test('no extra wake while text is already pending, and none for text that is shown at once', () => {
  const coordinator = new SmoothTextRevealCoordinator();
  let wakes = 0;
  coordinator.onPending = () => { wakes++; };
  coordinator.attach(0, node, '一', { identity: '0' });
  coordinator.attach(9, node, '二', { identity: '9' });
  assert.equal(wakes, 1);
  drain(coordinator);
  coordinator.updateLayout(20, null, '没有挂载的块直接完整显示', { identity: '20' });
  assert.equal(coordinator.drained, true);
  assert.equal(wakes, 1);
});
