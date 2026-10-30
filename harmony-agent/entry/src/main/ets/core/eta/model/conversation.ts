// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/model/AgentChatUiState.kt,
// agent/model/AgentModelClient.kt, AgentContextSnapshot.kt and agent/runtime/AgentEvent.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
// Minimal DTO subset for L1–L4; no Android, storage or model execution dependencies.
export interface AgentFileReference { displayName: string; absolutePath: string; kind: 'File' | 'Directory'; }
export interface PendingImageUi { id: string; uri: string; dataUrl: string; mimeType: string; }
export interface PendingFileReferenceUi { id: string; reference: AgentFileReference; }
export interface QueuedMessageUi {
  id: string; content: string; pendingImages: PendingImageUi[];
  pendingFileReferences: PendingFileReferenceUi[]; immediateInterrupt: boolean;
}
export function queuedMessage(id: string, content: string, pendingImages: PendingImageUi[] = [],
  pendingFileReferences: PendingFileReferenceUi[] = [], immediateInterrupt: boolean = false): QueuedMessageUi {
  return { id, content, pendingImages, pendingFileReferences, immediateInterrupt };
}
export type ToolActivityStatusUi = 'Running' | 'Success' | 'Failed' | 'Unknown';
export type SystemNoticeCode = 'Stopped' | 'EmptyResult' | 'RuntimeFailed' | 'ModelRetry' | 'ContextCompaction' | 'Interrupted';
export interface TokenUsageUi { contextTokens: number | null; inputTokens: number | null; outputTokens: number | null; reasoningTokens: number | null; cachedTokens: number | null; }
export interface MessageBase { id: string; kind: string; }
export interface UserMessageUi extends MessageBase { kind: 'user'; content: string; images: string[]; isEdited: boolean; }
export interface AgentMessageUi extends MessageBase {
  kind: 'assistant'; content: string; isStreaming: boolean; renderMarkdown: boolean;
  usage: TokenUsageUi | null; characterEditable: boolean; candidateCount: number;
  selectedCandidate: number; searchSuggestionsHtml: string[];
}
export interface ThinkingMessageUi extends MessageBase { kind: 'thinking'; content: string; isStreaming: boolean; elapsedSeconds: number | null; collapsed: boolean; }
export interface ToolActivityMessageUi extends MessageBase { kind: 'tool'; toolName: string; status: ToolActivityStatusUi; argumentsSummary: string; command: string | null; resultSummary: string | null; imageCount: number; }
export interface ToolSummaryMessageUi extends MessageBase { kind: 'toolSummary'; tools: string[]; }
export interface SystemNoticeMessageUi extends MessageBase { kind: 'notice'; code: SystemNoticeCode; detail: string | null; contextTokens: number | null; running: boolean; }
export interface CapabilityUi { title: string; items: string[]; }
export interface RunTraceMessageUi extends MessageBase { kind: 'trace'; capabilities: CapabilityUi[]; }
export interface SuggestionChipsMessageUi extends MessageBase { kind: 'suggestions'; prompts: string[]; }
export type AgentChatMessageUi = UserMessageUi | AgentMessageUi | ThinkingMessageUi | ToolActivityMessageUi | ToolSummaryMessageUi | SystemNoticeMessageUi | RunTraceMessageUi | SuggestionChipsMessageUi;
export function userMessage(id: string, content: string, images: string[] = [], isEdited: boolean = false): UserMessageUi { return { kind: 'user', id, content, images, isEdited }; }
export function agentMessage(id: string, content: string, isStreaming: boolean = false): AgentMessageUi {
  return { kind: 'assistant', id, content, isStreaming, renderMarkdown: true, usage: null, characterEditable: false, candidateCount: 1, selectedCandidate: 0, searchSuggestionsHtml: [] };
}
export function thinkingMessage(id: string, content: string, isStreaming: boolean = false): ThinkingMessageUi { return { kind: 'thinking', id, content, isStreaming, elapsedSeconds: null, collapsed: false }; }
export function toolActivityMessage(id: string, toolName: string, status: ToolActivityStatusUi, argumentsSummary: string = '', command: string | null = null, resultSummary: string | null = null, imageCount: number = 0): ToolActivityMessageUi { return { kind: 'tool', id, toolName, status, argumentsSummary, command, resultSummary, imageCount }; }
export function noticeMessage(id: string, code: SystemNoticeCode, detail: string | null = null): SystemNoticeMessageUi { return { kind: 'notice', id, code, detail, contextTokens: null, running: false }; }
export interface ConversationMessage {
  role: string; content: string; contentJson: string; toolCallId: string; reasoningContent: string;
  toolCallsJson: string; contextSummary: boolean; compactedUserTurns: number; summaryThroughUserTurn: number; messageId: string;
}
export function conversationMessage(role: string, content: string = ''): ConversationMessage {
  return { role, content, contentJson: '', toolCallId: '', reasoningContent: '', toolCallsJson: '', contextSummary: false, compactedUserTurns: 0, summaryThroughUserTurn: 0, messageId: '' };
}
export interface MessageEditUiState { targetMessageId: string; previousInput: string; previousImages: PendingImageUi[]; previousFileReferences: PendingFileReferenceUi[]; hasLaterTurns: boolean; preserveFollowingMessages: boolean; }
export interface AgentChatUiState {
  messages: AgentChatMessageUi[]; history: ConversationMessage[]; journal: ConversationMessage[];
  input: string; isStreaming: boolean; isCompacting: boolean; thinkingEnabled: boolean;
  pendingImages: PendingImageUi[]; pendingFileReferences: PendingFileReferenceUi[]; queuedMessages: QueuedMessageUi[];
  inputSyncVersion: number; appliedRuntimeRunIds: string[]; messageEdit: MessageEditUiState | null;
  modelId: string | null; isConversationLoaded: boolean; messageStartIndex: number; hasEarlierMessages: boolean;
}
export function emptyChatState(): AgentChatUiState { return { messages: [], history: [], journal: [], input: '', isStreaming: false, isCompacting: false, thinkingEnabled: false, pendingImages: [], pendingFileReferences: [], queuedMessages: [], inputSyncVersion: 0, appliedRuntimeRunIds: [], messageEdit: null, modelId: null, isConversationLoaded: true, messageStartIndex: 0, hasEarlierMessages: false }; }
export type AgentChatHomeUiState = AgentChatUiState;
export interface AgentContextSnapshot { operationId: string; messages: ConversationMessage[]; consumedUserTurns: number; coveredUserTurns: number; consumedSupplementCount: number; consumedTranscriptMessages: number | null; }
export function contextSnapshot(operationId: string, messages: ConversationMessage[]): AgentContextSnapshot { return { operationId, messages, consumedUserTurns: 0, coveredUserTurns: 0, consumedSupplementCount: 0, consumedTranscriptMessages: null }; }
export interface AssistantBlockDelta { round: number; kind: 'TEXT' | 'THINKING' | 'TOOL_CALL'; index: number; deltaChars: number; delta: string; }
