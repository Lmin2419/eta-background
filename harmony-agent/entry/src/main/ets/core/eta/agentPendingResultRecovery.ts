// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/app/AgentPendingResultRecovery.kt @ 3b1794d
// Copyright © 2026 蛮吉 (Mangi-11). PolyForm Noncommercial 1.0.0.
import {
  AgentChatUiState,
  AgentChatMessageUi,
  agentMessage,
  noticeMessage,
  userMessage,
  conversationMessage,
} from "./model/conversation";
import { RuntimeRunResult, Supplement } from "./model/runtimeProjection";
import {
  AgentRuntimeHistoryReducer,
  HistoryOutcome,
} from "./agentRuntimeHistoryReducer";
import {
  AgentRunMessageProjector,
  assistantRound,
  isAssistantForRun,
} from "./agentRunMessageProjector";
import { RoleplayConversationReducer } from "./roleplayConversationReducer";
export class AgentPendingResultRecovery {
  static apply(
    state: AgentChatUiState,
    runId: string,
    result: RuntimeRunResult,
    supplements: Supplement[],
    promptSupplement: Supplement | null = null,
  ): HistoryOutcome {
    if (
      result.operation === "rewrite_reply" ||
      state.roleplayMessages.pendingRewrites.has(runId)
    )
      return {
        state: RoleplayConversationReducer.applyRewrite(state, runId, result),
        alreadyApplied: state.appliedRuntimeRunIds.includes(runId),
      };
    const all = [
      ...(promptSupplement ? [promptSupplement] : []),
      ...supplements,
    ];
    const withSupplements: AgentChatUiState = {
      ...state,
      messages: this.mergeSupplements(runId, all, state.messages),
    };
    const snapshot = result.contextSnapshot
      ? {
          ...result.contextSnapshot,
          consumedTranscriptMessages:
            result.contextSnapshot.consumedTranscriptMessages === null
              ? null
              : result.contextSnapshot.consumedTranscriptMessages +
                (promptSupplement ? 1 : 0),
        }
      : null;
    const additions = [
      ...(promptSupplement
        ? [
            {
              ...conversationMessage("user", promptSupplement.text),
              messageId: this.supplementMessageId(
                runId,
                promptSupplement.index,
              ),
            },
          ]
        : []),
      ...result.transcript,
    ];
    const history = AgentRuntimeHistoryReducer.apply(
      withSupplements,
      runId,
      additions,
      snapshot,
      !result.ok || result.contextSnapshot !== null,
    );
    if (history.alreadyApplied) return { state, alreadyApplied: true };
    if (result.operation === "compact")
      return {
        state: {
          ...history.state,
          isStreaming: false,
          isCompacting: false,
          messages: AgentRunMessageProjector.mergeCompactionResultNotice(
            runId,
            state.messages,
            result.ok,
            result.ok ? "上下文压缩完成" : (result.error ?? "上下文压缩失败"),
          ),
        },
        alreadyApplied: false,
      };
    const messages = withSupplements.messages.filter(
      (message) =>
        !(message.kind === "notice" && message.id === `interrupted-${runId}`),
    );
    const index = AgentRunMessageProjector.resultTargetIndex(
      runId,
      messages,
      true,
    );
    const id = AgentRunMessageProjector.resultFallbackId(runId, messages);
    const target = messages[index];
    const round =
      target?.kind === "assistant" ? assistantRound(target.id, runId) : null;
    const count =
      round === null
        ? 0
        : messages.filter(
            (message) =>
              message.kind === "assistant" &&
              assistantRound(message.id, runId) === round,
          ).length;
    const content = result.ok && result.content.trim() ? result.content : null;
    const completed: AgentChatMessageUi =
      content !== null
        ? agentMessage(
            id,
            count > 1 && target?.kind === "assistant" && target.content.trim()
              ? target.content
              : content,
          )
        : result.ok
          ? noticeMessage(id, "EmptyResult")
          : noticeMessage(id, "RuntimeFailed", result.error);
    if (index >= 0) messages[index] = { ...completed, id: messages[index].id };
    else messages.push(completed);
    return {
      state: RoleplayConversationReducer.linkRun(
        {
          ...history.state,
          messages: this.mergeSupplements(runId, all, messages, true),
          isStreaming: false,
        },
        runId,
      ),
      alreadyApplied: false,
    };
  }
  static mergeSupplements(
    runId: string,
    supplements: Supplement[],
    messages: AgentChatMessageUi[],
    beforeLatestAssistant = false,
  ): AgentChatMessageUi[] {
    let updated = messages;
    for (const supplement of [...supplements].sort(
      (a, b) => a.index - b.index,
    )) {
      const id = this.supplementMessageId(runId, supplement.index);
      if (updated.some((message) => message.id === id)) continue;
      const user = userMessage(id, supplement.text);
      let index = -1;
      for (let i = updated.length - 1; i >= 0; i--) {
        const message = updated[i];
        if (
          message.kind === "assistant" &&
          isAssistantForRun(message.id, runId) &&
          (beforeLatestAssistant || message.isStreaming)
        ) {
          index = i;
          break;
        }
      }
      updated =
        index < 0
          ? [...updated, user]
          : [...updated.slice(0, index), user, ...updated.slice(index)];
    }
    return updated;
  }
  static supplementMessageId(runId: string, index: number): string {
    return `user-${runId}-supplement-${index}`;
  }
}
