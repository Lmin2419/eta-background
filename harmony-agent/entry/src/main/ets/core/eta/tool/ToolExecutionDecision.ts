// Ported from app/src/main/kotlin/io/github/mangi/eta/agent/tool/ToolExecutionDecision.kt @ 3b1794d
// Copyright © 2026 蛮吉 (Mangi-11). PolyForm Noncommercial 1.0.0.
export type ToolExecutionDecision =
  | { kind: 'allow' }
  | { kind: 'reject'; code: string; message: string };
export const ALLOW: ToolExecutionDecision = { kind: 'allow' };
export function reject(code: string, message: string): ToolExecutionDecision {
  return { kind: 'reject', code, message };
}
