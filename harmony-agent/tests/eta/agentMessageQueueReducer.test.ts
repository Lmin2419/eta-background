// Ported from app/src/test/kotlin/io/github/mangi/eta/ui/app/AgentMessageQueueReducerTest.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import test from 'node:test';
import assert from 'node:assert/strict';
import { AgentMessageQueueReducer as Q } from '../../entry/src/main/ets/core/eta/agentMessageQueueReducer';
import { queuedMessage as q, PendingImageUi, PendingFileReferenceUi } from '../../entry/src/main/ets/core/eta/model/conversation';
import { state } from './conversationFixtures';
const ids = (values: { id: string }[]) => values.map(x => x.id);
test('enqueueAppendsInOrderAndClearsCurrentDraft', () => {
  const image: PendingImageUi = { id: 'img-1', uri: 'file:///1.jpg', dataUrl: 'data:image/jpeg;base64,1', mimeType: 'image/jpeg' };
  const initial = state({ isStreaming: true, input: '草稿', pendingImages: [image] });
  const first = Q.enqueue(initial, q('q-1', '第一条', [image]));
  const second = Q.enqueue(first, q('q-2', '第二条'));
  assert.equal(second.input, ''); assert.equal(second.pendingImages.length, 0); assert.deepEqual(ids(second.queuedMessages), ['q-1', 'q-2']);
});
test('immediateInterruptPreemptsQueueHeadAndDemotesPreviousInterrupt', () => {
  const first = Q.enqueue(state({ isStreaming: true }), q('q-1', '普通排队'));
  const queued = Q.enqueue(first, q('q-2', '第一次插话', [], [], true));
  assert.deepEqual(ids(queued.queuedMessages), ['q-2', 'q-1']); assert.equal(queued.queuedMessages[0].immediateInterrupt, true);
  const next = Q.enqueue(queued, q('q-3', '第二次插话', [], [], true));
  assert.deepEqual(ids(next.queuedMessages), ['q-3', 'q-2', 'q-1']); assert.equal(next.queuedMessages[0].immediateInterrupt, true); assert.equal(next.queuedMessages[1].immediateInterrupt, false);
});
test('promoteToInterruptMovesItemToFrontWhenStreamingAndPopsImmediatelyWhenIdle', () => {
  const queuedMessages = [q('q-1', '第一条'), q('q-2', '第二条')];
  const streaming = Q.promoteToInterrupt(state({ isStreaming: true, queuedMessages }), 'q-2');
  assert.ok(streaming.item); assert.deepEqual(ids(streaming.state.queuedMessages), ['q-2', 'q-1']); assert.equal(streaming.state.queuedMessages[0].immediateInterrupt, true);
  const idle = Q.promoteToInterrupt(state({ isStreaming: false, queuedMessages }), 'q-2');
  assert.ok(idle.item); assert.equal(idle.item.immediateInterrupt, false); assert.deepEqual(ids(idle.state.queuedMessages), ['q-1']);
});
test('recallToDraftMergesWithUnsentTextAndAttachmentsAndBumpsSyncVersion', () => {
  const existing: PendingImageUi = { id: 'img-1', uri: 'u1', dataUrl: 'd1', mimeType: 'image/png' };
  const queued: PendingImageUi = { id: 'img-2', uri: 'u2', dataUrl: 'd2', mimeType: 'image/png' };
  const file: PendingFileReferenceUi = { id: 'file-1', reference: { displayName: 'doc.txt', absolutePath: '/sdcard/doc.txt', kind: 'File' } };
  const initial = state({ isStreaming: true, pendingImages: [existing], queuedMessages: [q('q-1', '撤回的内容', [existing, queued], [file]), q('q-2', '保留的内容')] });
  const recalled = Q.recallToDraft(initial, '正在敲的半句话', 'q-1'); assert.ok(recalled);
  assert.equal(recalled.input, '正在敲的半句话\n撤回的内容'); assert.deepEqual(ids(recalled.pendingImages), ['img-1', 'img-2']); assert.deepEqual(ids(recalled.pendingFileReferences), ['file-1']); assert.deepEqual(ids(recalled.queuedMessages), ['q-2']); assert.equal(recalled.inputSyncVersion, initial.inputSyncVersion + 1);
});
test('pollNextToSendAfterRunRespectsUserStopExceptForImmediateInterrupt', () => {
  const initial = state({ queuedMessages: [q('q-1', '第一条'), q('q-2', '第二条')] });
  const stopped = Q.pollNextToSendAfterRun(initial, true); assert.equal(stopped.item, null); assert.equal(stopped.state.queuedMessages.length, 2);
  const completed = Q.pollNextToSendAfterRun(initial, false); assert.equal(completed.item?.id, 'q-1'); assert.deepEqual(ids(completed.state.queuedMessages), ['q-2']);
  const interrupt = Q.pollNextToSendAfterRun({ ...initial, queuedMessages: [q('q-interrupt', '立即插话', [], [], true), q('q-1', '第一条')] }, true);
  assert.equal(interrupt.item?.id, 'q-interrupt'); assert.equal(interrupt.item?.immediateInterrupt, false); assert.deepEqual(ids(interrupt.state.queuedMessages), ['q-1']);
});
