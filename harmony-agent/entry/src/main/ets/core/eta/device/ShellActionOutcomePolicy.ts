// Ported from app/src/main/kotlin/io/github/mangi/eta/agent/device/ShellActionOutcomePolicy.kt @ 3b1794d
// Copyright © 2026 蛮吉 (Mangi-11). PolyForm Noncommercial 1.0.0.
/** Root 进程超时不能证明已经发给系统的输入动作没有执行。 */
export enum ShellActionOutcome {
  SUCCEEDED = 'SUCCEEDED',
  FAILED = 'FAILED',
  TIMED_OUT = 'TIMED_OUT',
}
export const PROCESS_TIMEOUT_EXIT_CODE = -2;
export function classifyShellAction(exitCode: number): ShellActionOutcome {
  if (exitCode === 0) return ShellActionOutcome.SUCCEEDED;
  if (exitCode === PROCESS_TIMEOUT_EXIT_CODE) return ShellActionOutcome.TIMED_OUT;
  return ShellActionOutcome.FAILED;
}
