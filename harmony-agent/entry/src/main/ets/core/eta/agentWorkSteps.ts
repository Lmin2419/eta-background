// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/components/AgentWorkSteps.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { AgentChatMessageUi, ToolActivityMessageUi } from './model/conversation';
export interface WorkSingle { kind: 'single'; key: string; message: AgentChatMessageUi; }
export interface WorkToolGroup { kind: 'group'; key: string; toolName: string; calls: ToolActivityMessageUi[]; running: boolean; failedCount: number; }
export type AgentWorkStep = WorkSingle | WorkToolGroup;
export interface AgentWorkSummary { thinkingCount: number; toolCount: number; failedCount: number; }
export function toWorkSteps(messages: AgentChatMessageUi[]): AgentWorkStep[] {
  const result: AgentWorkStep[] = []; let run: ToolActivityMessageUi[] = [];
  const flush = (): void => {
    if (run.length === 1) result.push({ kind: 'single', key: run[0].id, message: run[0] });
    else if (run.length > 1) result.push({ kind: 'group', key: 'group-' + run[0].id, toolName: run[0].toolName, calls: run.slice(), running: run.some(x => x.status === 'Running'), failedCount: run.filter(x => x.status === 'Failed').length });
    run = [];
  };
  for (const message of messages) {
    if (message.kind === 'tool' && message.toolName !== 'browser_use' && message.toolName !== 'subagent') {
      if (run.length > 0 && run[0].toolName !== message.toolName) flush();
      run.push(message);
    } else { flush(); result.push({ kind: 'single', key: message.id, message }); }
  }
  flush(); return result;
}
export function workSummary(messages: AgentChatMessageUi[]): AgentWorkSummary { return { thinkingCount: messages.filter(x => x.kind === 'thinking').length, toolCount: messages.filter(x => x.kind === 'tool').length, failedCount: messages.filter(x => x.kind === 'tool' && x.status === 'Failed').length }; }
/** These fixed Chinese markers are Runtime wire-summary prefixes, not UI labels. */
export function outcomeLine(message: ToolActivityMessageUi): string | null {
  const summary = message.resultSummary?.trim(); if (!summary) return null;
  const lines = summary.split(/\r\n|\r|\n/).map(x => x.trim()).filter(x => x.length > 0);
  const head = lines[0]; if (!head || message.status === 'Running') return null;
  const detail = lines.slice(1).filter(x => x !== '…').pop() ?? null;
  if (message.status === 'Failed' || message.status === 'Unknown') {
    const clean = (head.startsWith('失败 · ') ? head.slice('失败 · '.length) : head).split(' · code=')[0];
    return clean.trim() && clean !== '失败' ? clean : detail;
  }
  if (detail !== null) return detail;
  if (head === '完成' || head === '执行完成') return null;
  const clean = head.startsWith('完成 · ') ? head.slice('完成 · '.length) : head;
  return clean.trim() ? clean : null;
}
