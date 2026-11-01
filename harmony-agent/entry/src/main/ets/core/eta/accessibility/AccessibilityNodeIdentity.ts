// Ported from app/src/main/kotlin/io/github/mangi/eta/agent/accessibility/AccessibilityNodeIdentity.kt @ 3b1794d
// Copyright © 2026 蛮吉 (Mangi-11). PolyForm Noncommercial 1.0.0.
/** 可在 JVM 中验证的节点身份指纹；viewId 不是列表项唯一标识。 */
export class AccessibilityNodeIdentity {
  constructor(
    readonly uniqueId: string,
    readonly windowId: number,
    readonly packageName: string,
    readonly className: string,
    readonly viewId: string,
    readonly text: string,
    readonly description: string,
    readonly password: boolean,
  ) {}
  get strong(): boolean {
    return this.uniqueId.trim() !== '' || this.text.trim() !== '' || this.description.trim() !== '';
  }
  matches(refreshed: AccessibilityNodeIdentity): boolean {
    if (this.windowId !== refreshed.windowId) return false;
    if (this.packageName !== refreshed.packageName) return false;
    if (this.className !== refreshed.className) return false;
    if (this.password !== refreshed.password) return false;
    if (this.uniqueId !== refreshed.uniqueId) return false;
    if (this.viewId.trim() !== '' && this.viewId !== refreshed.viewId) return false;
    // uniqueId 只证明还是同一个虚拟节点，不证明它仍表达模型观察时的动作语义。
    if (this.text !== refreshed.text) return false;
    if (this.description !== refreshed.description) return false;
    return true;
  }
}
/** 窗口内容变化后，只有真正稳定且在观察范围内可证明唯一的身份才能继续使用。 */
export function canBypassContentChange(
  hasUniqueId: boolean,
  snapshotTruncated: boolean,
  identityMatchCount: number,
): boolean {
  return identityMatchCount === 1 && (hasUniqueId || !snapshotTruncated);
}
