// Ported from app/src/main/kotlin/io/github/mangi/eta/agent/device/RootShellDeviceController.kt @ 3b1794d (ElementObservation / ElementSource / UiNode only)
// Copyright © 2026 蛮吉 (Mangi-11). PolyForm Noncommercial 1.0.0.
import { Rect } from './ScrollGestureContract';
export enum ElementSource {
  ACCESSIBILITY = 'accessibility',
  UIAUTOMATOR = 'uiautomator',
}
export interface UiNode {
  index: number;
  text: string;
  desc: string;
  className: string;
  packageName: string;
  viewId: string;
  bounds: Rect;
  clickable: boolean;
  longClickable: boolean;
  scrollable: boolean;
  focused: boolean;
  editable: boolean;
  password: boolean;
  enabled: boolean;
  checked?: boolean | null;
  selected?: boolean;
  hint?: string;
}
export interface ElementObservation {
  id: string;
  source: ElementSource;
  packageName: string;
  windowId: number | null;
  nodes: UiNode[];
  maxNodes: number;
  truncated: boolean;
  treeSignature?: string;
}
export function centerX(node: UiNode): number {
  return Math.floor((node.bounds.left + node.bounds.right) / 2);
}
export function centerY(node: UiNode): number {
  return Math.floor((node.bounds.top + node.bounds.bottom) / 2);
}
/** Android Rect.toShortString(): "[left,top][right,bottom]". */
export function boundsToShortString(bounds: Rect): string {
  return `[${bounds.left},${bounds.top}][${bounds.right},${bounds.bottom}]`;
}
