// Ported from app/src/test/kotlin/io/github/mangi/eta/agent/device/ScrollGestureContractTest.kt @ 3b1794d
import test from 'node:test';
import assert from 'node:assert/strict';
import {
  Rect,
  ScrollAmount,
  ScrollAxis,
  ScrollDirection,
  ScrollGesture,
  rectContains,
} from '../../../entry/src/main/ets/core/eta/device/ScrollGestureContract';

const bounds: Rect = { left: 100, top: 200, right: 500, bottom: 1000 };

function requireGesture(gesture: ScrollGesture | null): ScrollGesture {
  assert.notEqual(gesture, null);
  return gesture as ScrollGesture;
}

function assertInside(gesture: ScrollGesture, rect: Rect): void {
  assert.ok(rectContains(rect, gesture.start));
  assert.ok(rectContains(rect, gesture.end));
}

function assertNearFractions(far: number, near: number, start: number, endExclusive: number): void {
  const span = endExclusive - start - 1;
  assert.ok(far >= Math.trunc(start + span * 0.7) && far <= Math.trunc(start + span * 0.9));
  assert.ok(near >= Math.trunc(start + span * 0.1) && near <= Math.trunc(start + span * 0.3));
}

test('smallAmountMovesAboutAQuarterOfThePageGestureInTheSameDirection', () => {
  const page = requireGesture(ScrollDirection.DOWN.gestureWithin(bounds, ScrollAmount.PAGE));
  const small = requireGesture(ScrollDirection.DOWN.gestureWithin(bounds, ScrollAmount.SMALL));
  const pageTravel = page.start.y - page.end.y;
  const smallTravel = small.start.y - small.end.y;
  assert.ok(smallTravel > 0);
  assert.ok(smallTravel * 2 < pageTravel);
  assertInside(small, bounds);
  assert.equal(ScrollAmount.parse(''), ScrollAmount.PAGE);
  assert.equal(ScrollAmount.parse('Small'), ScrollAmount.SMALL);
  assert.equal(ScrollAmount.parse('huge'), null);
});

test('downShowsLowerContentWithUpwardFingerGesture', () => {
  const gesture = requireGesture(ScrollDirection.DOWN.gestureWithin(bounds));
  assert.equal(ScrollDirection.DOWN.axis, ScrollAxis.VERTICAL);
  assert.equal(ScrollDirection.DOWN.scrollDeltaSign, 1);
  assert.equal(gesture.start.x, gesture.end.x);
  assert.ok(gesture.start.y > gesture.end.y);
  assertNearFractions(gesture.start.y, gesture.end.y, bounds.top, bounds.bottom);
  assertInside(gesture, bounds);
});

test('upShowsUpperContentWithDownwardFingerGesture', () => {
  const gesture = requireGesture(ScrollDirection.UP.gestureWithin(bounds));
  assert.equal(ScrollDirection.UP.axis, ScrollAxis.VERTICAL);
  assert.equal(ScrollDirection.UP.scrollDeltaSign, -1);
  assert.equal(gesture.start.x, gesture.end.x);
  assert.ok(gesture.start.y < gesture.end.y);
  assertNearFractions(gesture.end.y, gesture.start.y, bounds.top, bounds.bottom);
  assertInside(gesture, bounds);
});

test('rightShowsRightContentWithLeftwardFingerGesture', () => {
  const gesture = requireGesture(ScrollDirection.RIGHT.gestureWithin(bounds));
  assert.equal(ScrollDirection.RIGHT.axis, ScrollAxis.HORIZONTAL);
  assert.equal(ScrollDirection.RIGHT.scrollDeltaSign, 1);
  assert.equal(gesture.start.y, gesture.end.y);
  assert.ok(gesture.start.x > gesture.end.x);
  assertNearFractions(gesture.start.x, gesture.end.x, bounds.left, bounds.right);
  assertInside(gesture, bounds);
});

test('leftShowsLeftContentWithRightwardFingerGesture', () => {
  const gesture = requireGesture(ScrollDirection.LEFT.gestureWithin(bounds));
  assert.equal(ScrollDirection.LEFT.axis, ScrollAxis.HORIZONTAL);
  assert.equal(ScrollDirection.LEFT.scrollDeltaSign, -1);
  assert.equal(gesture.start.y, gesture.end.y);
  assert.ok(gesture.start.x < gesture.end.x);
  assertNearFractions(gesture.end.x, gesture.start.x, bounds.left, bounds.right);
  assertInside(gesture, bounds);
});

test('twoPixelBoundsKeepEveryDirectionInsideAndMoving', () => {
  const tinyBounds: Rect = { left: 10, top: 20, right: 12, bottom: 22 };
  for (const direction of ScrollDirection.ENTRIES) {
    const gesture = requireGesture(direction.gestureWithin(tinyBounds));
    assert.notDeepEqual(gesture.start, gesture.end, direction.name);
    assertInside(gesture, tinyBounds);
  }
});

test('onePixelPerpendicularAxisStillSupportsGesture', () => {
  const narrowVertical: Rect = { left: 10, top: 20, right: 11, bottom: 24 };
  const shortHorizontal: Rect = { left: 10, top: 20, right: 14, bottom: 21 };
  const vertical = requireGesture(ScrollDirection.DOWN.gestureWithin(narrowVertical));
  const horizontal = requireGesture(ScrollDirection.RIGHT.gestureWithin(shortHorizontal));
  assertInside(vertical, narrowVertical);
  assertInside(horizontal, shortHorizontal);
});

test('boundsWithoutEnoughAxisTravelReturnNull', () => {
  const onePixel: Rect = { left: 10, top: 20, right: 11, bottom: 21 };
  for (const direction of ScrollDirection.ENTRIES) {
    assert.equal(direction.gestureWithin(onePixel), null, direction.name);
  }
  assert.equal(ScrollDirection.DOWN.gestureWithin({ left: 0, top: 0, right: 0, bottom: 10 }), null);
});

test('parseUsesTheUnifiedPhysicalDirectionsOnly', () => {
  assert.equal(ScrollDirection.parse(' Down '), ScrollDirection.DOWN);
  assert.equal(ScrollDirection.parse('LEFT'), ScrollDirection.LEFT);
  assert.equal(ScrollDirection.parse('forward'), null);
});
