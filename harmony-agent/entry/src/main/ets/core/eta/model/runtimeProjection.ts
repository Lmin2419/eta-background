// Ported from app/src/main/kotlin/io/github/mangi/eta/agent/runtime/AgentEvent.kt,
// AgentRuntimeWire.kt, AgentUiHandoffPayload.kt and agent/roleplay/RoleplayMessageState.kt @ 3b1794d
// Copyright © 2026 蛮吉 (Mangi-11). PolyForm Noncommercial 1.0.0.
import {
  AgentContextSnapshot,
  ConversationMessage,
  TokenUsageUi,
} from "./conversation";
export interface RoleplayBinding {
  characterId: string;
  characterJson: string;
  name: string;
  cardSnapshotJson?: string; characterName?: string; avatarPath?: string | null;
  userName?: string; userDescription?: string;
}
export interface RoleplayMessageLink {
  transcriptMessageId: string;
  blockOrder: number;
}
export interface RoleplayReplyRevision {
  original: string;
  candidates: string[];
  selected: number;
}
export interface RoleplayMessageState {
  links: Map<string, RoleplayMessageLink>;
  revisions: Map<string, RoleplayReplyRevision>;
  pendingRewrites: Map<string, string>;
}
export function emptyRoleplayMessages(): RoleplayMessageState {
  return { links: new Map(), revisions: new Map(), pendingRewrites: new Map() };
}
export function revisionContent(revision: RoleplayReplyRevision): string {
  return revision.candidates[revision.selected] ?? revision.original;
}
export interface Supplement {
  index: number;
  text: string;
  createdAt: number;
}
export interface RuntimeRunResult {
  runId: string;
  ok: boolean;
  content: string;
  error: string | null;
  reasoningContent: string;
  transcript: ConversationMessage[];
  contextSnapshot: AgentContextSnapshot | null;
  contextSnapshotRef: string;
  operation: "chat" | "compact" | "rewrite_reply";
  rewriteTargetMessageId: string | null;
}
export function runResult(
  runId: string,
  ok: boolean,
  content: string,
): RuntimeRunResult {
  return {
    runId,
    ok,
    content,
    error: null,
    reasoningContent: "",
    transcript: [],
    contextSnapshot: null,
    contextSnapshotRef: "",
    operation: "chat",
    rewriteTargetMessageId: null,
  };
}
export type AssistantBlockKind = "TEXT" | "THINKING" | "TOOL_CALL";
export interface BlockStart {
  type: "AssistantBlockStart";
  round: number;
  kind: AssistantBlockKind;
  index: number;
}
export interface BlockDelta {
  type: "AssistantBlockDelta";
  round: number;
  kind: AssistantBlockKind;
  index: number;
  delta: string;
}
export interface BlockEnd {
  type: "AssistantBlockEnd";
  round: number;
  kind: AssistantBlockKind;
  index: number;
  replacementContent: string | null;
}
export interface SearchSuggestions {
  type: "SearchSuggestions";
  round: number;
  index: number;
  html: string;
}
export interface UsageReceived {
  type: "UsageReceived";
  round: number;
  usage: TokenUsageUi;
}
export interface ToolStarted {
  type: "ToolStarted";
  round: number;
  toolCallId: string;
  name: string;
  argsPreview: string;
  command: string | null;
}
export interface ToolFinished {
  type: "ToolFinished";
  round: number;
  toolCallId: string;
  name: string;
  resultSummary: string;
  imageCount: number;
  success: boolean | null;
  command: string | null;
}
export interface SubagentProgress {
  type: "SubagentProgress";
  round: number;
  toolCallId: string;
  snapshotJson: string;
}
export interface HostedToolStarted {
  type: "HostedToolStarted";
  round: number;
  toolCallId: string;
  name: string;
}
export interface HostedToolFinished {
  type: "HostedToolFinished";
  round: number;
  toolCallId: string;
  name: string;
  success: boolean;
}
export interface ContextCompaction {
  type: "ContextCompaction";
  operationId: string;
  phase: string;
  tokensAfter: number | null;
}
export interface ModelRetryScheduled {
  type: "ModelRetryScheduled";
  round: number;
  attempt: number;
  maxAttempts: number;
  delayMs: number;
  reasonCode: string;
}
export interface RunFailed {
  type: "RunFailed";
  reason: string;
}
export interface AssistantReceived {
  type: "AssistantReceived";
  round: number;
  reasoningContent: string;
}
export interface IgnoredProjectionEvent {
  type:
    | "GuiControlChanged"
    | "UserSupplementReceived"
    | "RunStarted"
    | "ProviderRequestStarted"
    | "ProviderResponseStarted"
    | "ToolImagesAttached"
    | "RoundStarted";
}
export interface RunFinished {
  type: "RunFinished";
}
export type AgentProjectionEvent =
  | BlockStart
  | BlockDelta
  | BlockEnd
  | SearchSuggestions
  | UsageReceived
  | ToolStarted
  | ToolFinished
  | SubagentProgress
  | HostedToolStarted
  | HostedToolFinished
  | ContextCompaction
  | ModelRetryScheduled
  | RunFailed
  | AssistantReceived
  | IgnoredProjectionEvent
  | RunFinished;
export function modelRetryMessage(event: ModelRetryScheduled): string {
  return `模型请求暂时中断，${Math.trunc(event.delayMs / 1000)} 秒后重试（${event.attempt}/${event.maxAttempts}）；此前工具结果已保留。`;
}
export function compactionMessage(phase: string): string {
  return phase === "started"
    ? "正在压缩上下文…"
    : phase === "completed"
      ? "上下文已压缩，用量将在下一次模型响应后更新。"
      : "上下文压缩失败，原始上下文已保留。";
}
