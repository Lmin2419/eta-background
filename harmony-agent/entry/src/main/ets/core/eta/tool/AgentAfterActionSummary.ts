// Ported from app/src/main/kotlin/io/github/mangi/eta/agent/tool/AgentAfterActionSummary.kt @ 3b1794d
// Copyright © 2026 蛮吉 (Mangi-11). PolyForm Noncommercial 1.0.0.
import { ElementObservation, UiNode, boundsToShortString, centerX, centerY } from '../device/observation';
export interface CompactUiNode {
  index: number;
  text?: string;
  desc?: string;
  class: string;
  view_id?: string;
  center: { x: number; y: number };
  clickable?: true;
  long_clickable?: true;
  scrollable?: true;
  editable?: true;
  focused?: true;
  password?: true;
  enabled?: false;
  checked?: boolean;
  selected?: true;
  hint?: string;
}
export interface AfterActionSummary {
  observation_id: string;
  package: string;
  screen_changed: boolean;
  package_changed: boolean;
  window_changed: boolean;
  ui_tree_truncated: boolean;
  ui_nodes: CompactUiNode[];
  note: string;
}
function signature(nodes: UiNode[]): string[] {
  return nodes.map(
    (node) =>
      `${node.className}|${node.viewId}|${node.text}|${node.desc}|${boundsToShortString(node.bounds)}`,
  );
}
/** 精简字段：省略默认值为 false 的布尔与包名，降低每步附带观察的 token 开销。 */
export function compactNode(node: UiNode): CompactUiNode {
  const compact: CompactUiNode = {
    index: node.index,
    class: node.className.substring(node.className.lastIndexOf('.') + 1),
    center: { x: centerX(node), y: centerY(node) },
  };
  if (node.text.trim() !== '') compact.text = node.text;
  if (node.desc.trim() !== '') compact.desc = node.desc;
  if (node.viewId.trim() !== '') compact.view_id = node.viewId.substring(node.viewId.lastIndexOf('/') + 1);
  if (node.clickable) compact.clickable = true;
  if (node.longClickable) compact.long_clickable = true;
  if (node.scrollable) compact.scrollable = true;
  if (node.editable) compact.editable = true;
  if (node.focused) compact.focused = true;
  if (node.password) compact.password = true;
  if (!node.enabled) compact.enabled = false;
  if (node.checked !== null && node.checked !== undefined) compact.checked = node.checked;
  if (node.selected) compact.selected = true;
  if (node.hint !== undefined && node.hint.trim() !== '') compact.hint = node.hint;
  return compact;
}
/** 动作后观察的模型投影：先给结论（界面是否变化、是否切换应用或窗口），再给新的节点列表。 */
export function buildAfterActionSummary(
  before: ElementObservation | null,
  after: ElementObservation,
): AfterActionSummary {
  const changed = before === null || JSON.stringify(signature(before.nodes)) !== JSON.stringify(signature(after.nodes));
  return {
    observation_id: after.id,
    package: after.packageName,
    screen_changed: changed,
    package_changed: before !== null && before.packageName !== after.packageName,
    window_changed: before !== null && before.windowId !== after.windowId,
    ui_tree_truncated: after.truncated,
    ui_nodes: after.nodes.map(compactNode),
    note: changed
      ? '以上为动作后的新界面，可直接用该 observation_id 继续操作；需要更多节点或截图时再调用 observe_screen'
      : '界面没有可见变化，动作可能未生效；请换目标或方式，不要原样重复',
  };
}
