// Ported from app/src/main/kotlin/io/github/mangi/eta/agent/device/ScrollGestureContract.kt @ 3b1794d
// Copyright © 2026 蛮吉 (Mangi-11). PolyForm Noncommercial 1.0.0.
export interface Rect {
  left: number;
  top: number;
  right: number;
  bottom: number;
}
export interface ScrollPoint {
  x: number;
  y: number;
}
export interface ScrollGesture {
  start: ScrollPoint;
  end: ScrollPoint;
}
export function rectIsEmpty(bounds: Rect): boolean {
  return bounds.right <= bounds.left || bounds.bottom <= bounds.top;
}
export function rectContains(bounds: Rect, point: ScrollPoint): boolean {
  return point.x >= bounds.left && point.x < bounds.right && point.y >= bounds.top && point.y < bounds.bottom;
}
export enum ScrollAxis {
  HORIZONTAL = 'HORIZONTAL',
  VERTICAL = 'VERTICAL',
}
/** 滚动幅度。系统滚动动作固定翻约一屏，不接受距离参数；SMALL 只走手势，用约四分之一位移露出少量内容。 */
export class ScrollAmount {
  static readonly SMALL = new ScrollAmount('SMALL', 0.38, 0.62);
  static readonly PAGE = new ScrollAmount('PAGE', 0.2, 0.8);
  private constructor(
    readonly name: string,
    readonly nearFraction: number,
    readonly farFraction: number,
  ) {}
  static parse(value: string): ScrollAmount | null {
    if (!value || value.trim() === '') return ScrollAmount.PAGE;
    const normalized = value.trim().toUpperCase();
    if (normalized === 'SMALL') return ScrollAmount.SMALL;
    if (normalized === 'PAGE') return ScrollAmount.PAGE;
    return null;
  }
}
/** 滚动方向表示希望显示的新内容所在方向，而不是手指移动方向。 */
export class ScrollDirection {
  static readonly UP = new ScrollDirection('UP', ScrollAxis.VERTICAL, -1);
  static readonly DOWN = new ScrollDirection('DOWN', ScrollAxis.VERTICAL, 1);
  static readonly LEFT = new ScrollDirection('LEFT', ScrollAxis.HORIZONTAL, -1);
  static readonly RIGHT = new ScrollDirection('RIGHT', ScrollAxis.HORIZONTAL, 1);
  static readonly ENTRIES: ScrollDirection[] = [
    ScrollDirection.UP,
    ScrollDirection.DOWN,
    ScrollDirection.LEFT,
    ScrollDirection.RIGHT,
  ];
  private constructor(
    readonly name: string,
    readonly axis: ScrollAxis,
    readonly scrollDeltaSign: number,
  ) {}
  static parse(value: string): ScrollDirection | null {
    const normalized = value.trim().toUpperCase();
    return ScrollDirection.ENTRIES.find((direction) => direction.name === normalized) ?? null;
  }
  gestureWithin(bounds: Rect, amount: ScrollAmount = ScrollAmount.PAGE): ScrollGesture | null {
    if (rectIsEmpty(bounds)) return null;
    const axisStart = this.axis === ScrollAxis.VERTICAL ? bounds.top : bounds.left;
    const axisEndExclusive = this.axis === ScrollAxis.VERTICAL ? bounds.bottom : bounds.right;
    if (axisEndExclusive - axisStart < MIN_GESTURE_SPAN_PX) return null;
    const near = pointOnAxis(axisStart, axisEndExclusive, amount.nearFraction);
    const far = pointOnAxis(axisStart, axisEndExclusive, amount.farFraction);
    const perpendicular =
      this.axis === ScrollAxis.VERTICAL
        ? midpoint(bounds.left, bounds.right)
        : midpoint(bounds.top, bounds.bottom);
    const startAxis = this.scrollDeltaSign > 0 ? far : near;
    const endAxis = this.scrollDeltaSign > 0 ? near : far;
    if (this.axis === ScrollAxis.VERTICAL) {
      return {
        start: { x: perpendicular, y: startAxis },
        end: { x: perpendicular, y: endAxis },
      };
    }
    return {
      start: { x: startAxis, y: perpendicular },
      end: { x: endAxis, y: perpendicular },
    };
  }
}
export const MIN_GESTURE_SPAN_PX = 2;
function pointOnAxis(start: number, endExclusive: number, fraction: number): number {
  const endInclusive = endExclusive - 1;
  const value = Math.round(start + (endInclusive - start) * fraction);
  return Math.min(Math.max(value, start), endInclusive);
}
function midpoint(start: number, endExclusive: number): number {
  return Math.min(start + Math.floor((endExclusive - start) / 2), endExclusive - 1);
}
