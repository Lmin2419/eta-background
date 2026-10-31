// Ported from app/src/main/kotlin/io/github/mangi/eta/agent/tool/ToolArguments.kt @ 3b1794d
// Copyright © 2026 蛮吉 (Mangi-11). PolyForm Noncommercial 1.0.0.
/** Android 的 getString 会把 JSON null、对象和数组转成文本；写操作必须保留真实文本语义。 */
export function requiredText(argumentsValue: Record<string, unknown>, name: string): string {
  const value = argumentsValue[name];
  if (typeof value !== 'string') throw new Error(`${name} 缺失或不是文本`);
  return value;
}
