// Ported from app/src/test/kotlin/io/github/mangi/eta/ui/components/ConversationPaneMotionTest.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import test from 'node:test';
import assert from 'node:assert/strict';
import { conversationPaneMotion as motion } from '../../entry/src/main/ets/core/eta/conversationPaneMotion';
const near = (a: number, b: number, tolerance = 0.0001) => assert.ok(Math.abs(a - b) <= tolerance, `${a} != ${b}`);
test('closedAndOpenPositionsMatchTheViewportEdges', () => { assert.deepEqual(motion(0, 320), { progress: 0, foregroundOffset: 0, panelOffset: -320 }); assert.deepEqual(motion(320, 320), { progress: 1, foregroundOffset: 320, panelOffset: 0 }); });
test('panesStayAdjacentWithoutOverlapOrGapsThroughoutEitherDirection', () => {
  for (const width of [280, 320, 340]) {
    const positions = [0, 0.1, 0.5, 0.6, 40, width / 2, width], opening = positions.map(x => motion(x, width));
    for (const m of opening) near(m.foregroundOffset, m.panelOffset + width);
    assert.ok(opening[3].progress < 0.01);
    for (let i = 1; i < opening.length; i++) { assert.ok(opening[i].progress >= opening[i - 1].progress); near(opening[i].foregroundOffset - opening[i - 1].foregroundOffset, opening[i].panelOffset - opening[i - 1].panelOffset); }
    assert.deepEqual(positions.slice().reverse().map(x => motion(x, width)).reverse(), opening);
  }
});
test('overshootAndUninitializedOffsetsCannotProduceInvalidLayers', () => {
  assert.deepEqual(motion(-20, 320), motion(0, 320)); assert.deepEqual(motion(400, 320), motion(320, 320)); assert.deepEqual(motion(NaN, 320), motion(0, 320));
  for (const width of [0, -1, NaN, Infinity]) { const m = motion(40, width); assert.equal(m.progress, 0); assert.equal(m.foregroundOffset, 0); assert.ok(Number.isFinite(m.panelOffset)); }
});
test('resizedWindowsKeepTheSameProgressAtEquivalentDragFractions', () => { const compact = motion(140, 280), wide = motion(170, 340); assert.equal(compact.progress, wide.progress); near(compact.panelOffset / 280, wide.panelOffset / 340, 0.00001); });
