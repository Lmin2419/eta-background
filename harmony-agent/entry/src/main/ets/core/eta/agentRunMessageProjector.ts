// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/app/AgentRunMessageProjector.kt @ 3b1794d
// Copyright © 2026 蛮吉 (Mangi-11). PolyForm Noncommercial 1.0.0.
import {
  AgentChatMessageUi,
  ThinkingMessageUi,
  SystemNoticeCode,
  agentMessage,
  thinkingMessage,
  toolActivityMessage,
  noticeMessage,
} from "./model/conversation";
import {
  AgentProjectionEvent,
  BlockStart,
  AssistantBlockKind,
  SearchSuggestions,
  ToolStarted,
  ToolFinished,
  SubagentProgress,
  HostedToolStarted,
  HostedToolFinished,
  ModelRetryScheduled,
  modelRetryMessage,
  compactionMessage,
} from "./model/runtimeProjection";
import { SearchSuggestionsBudget } from "./searchSuggestionsBudget";
function lastIndex<T>(
  values: T[],
  predicate: (value: T, index: number) => boolean,
): number {
  for (let i = values.length - 1; i >= 0; i--)
    if (predicate(values[i], i)) return i;
  return -1;
}
export function isAssistantForRun(id: string, runId: string): boolean {
  return id === `assistant-${runId}` || id.startsWith(`assistant-${runId}-`);
}
function assistantForRound(id: string, runId: string, round: number): boolean {
  const prefix = `assistant-${runId}-${round}`;
  return id === prefix || id.startsWith(prefix + "-");
}
function thinkingForRound(id: string, runId: string, round: number): boolean {
  const prefix = `${runId}-thinking-${round}`;
  return id === prefix || id.startsWith(prefix + "-");
}
export function assistantRound(id: string, runId: string): number | null {
  const prefix = `assistant-${runId}-`;
  if (!id.startsWith(prefix)) return null;
  const value = id.slice(prefix.length).split("-")[0];
  return /^\d+$/.test(value) ? Number(value) : null;
}
function toolId(runId: string, round: number, callId: string): string {
  return `${runId}-tool-${round}-${callId.trim() ? callId : "unknown"}`;
}
export class AgentRunMessageProjector {
  private thinkingStartedAt = new Map<string, number>();
  // The caller supplies a monotonic clock; wall-clock time is deliberately not a default.
  constructor(private nowElapsedRealtime: () => number) {}
  applyEvent(
    runId: string,
    event: AgentProjectionEvent,
    messages: AgentChatMessageUi[],
  ): AgentChatMessageUi[] {
    switch (event.type) {
      case "AssistantBlockStart":
        return this.startAssistantBlock(runId, event, messages);
      case "AssistantBlockDelta":
        return event.kind === "TEXT"
          ? this.appendTextDelta(
              runId,
              event.round,
              event.index,
              event.delta,
              messages,
            )
          : event.kind === "THINKING"
            ? this.appendReasoningDelta(
                runId,
                event.round,
                event.index,
                event.delta,
                messages,
              )
            : messages;
      case "AssistantBlockEnd":
        return event.kind === "TEXT"
          ? this.finalizeTextBlock(
              runId,
              event.round,
              event.index,
              event.replacementContent,
              messages,
            )
          : event.kind === "THINKING"
            ? this.finalizeThinkingBlock(
                runId,
                event.round,
                event.index,
                event.replacementContent,
                messages,
              )
            : messages;
      case "SearchSuggestions":
        return this.attachSearchSuggestions(runId, event, messages);
      case "UsageReceived": {
        if (Object.values(event.usage).every((value) => value === null))
          return messages;
        const index = lastIndex(
          messages,
          (message) =>
            message.kind === "assistant" &&
            assistantForRound(message.id, runId, event.round),
        );
        return index < 0
          ? messages
          : messages.map((message, i) =>
              i === index && message.kind === "assistant"
                ? { ...message, usage: event.usage }
                : message,
            );
      }
      case "ToolStarted":
        return this.startTool(
          runId,
          event,
          this.finalizeTextRound(
            runId,
            event.round,
            this.finalizeThinkingRound(runId, event.round, messages),
          ),
        );
      case "ToolFinished":
        return this.finishTool(runId, event, messages);
      case "SubagentProgress":
        return this.updateSubagentProgress(runId, event, messages);
      case "HostedToolStarted":
        return this.startHostedTool(
          runId,
          event,
          this.finalizeTextRound(
            runId,
            event.round,
            this.finalizeThinkingRound(runId, event.round, messages),
          ),
        );
      case "HostedToolFinished":
        return this.finishHostedTool(runId, event, messages);
      case "ContextCompaction": {
        const id = `assistant-${runId}-compaction-${event.operationId}`;
        return [
          ...messages.filter((message) => message.id !== id),
          {
            ...noticeMessage(
              id,
              "ContextCompaction",
              compactionMessage(event.phase),
            ),
            contextTokens: event.tokensAfter,
            running: event.phase === "started",
          },
        ];
      }
      case "ModelRetryScheduled":
        return this.scheduleModelRetry(runId, event, messages);
      case "RunFailed":
        return this.failRunningTools(
          event.reason,
          this.finalizeText(runId, this.finalizeThinking(runId, messages)),
        );
      case "AssistantReceived":
        return event.reasoningContent.trim()
          ? this.ensureCompletedThinking(
              runId,
              event.round,
              event.reasoningContent,
              messages,
            )
          : messages;
      case "RunFinished":
        return this.finalizeText(runId, this.finalizeThinking(runId, messages));
      default:
        return messages;
    }
  }
  private attachSearchSuggestions(
    runId: string,
    event: SearchSuggestions,
    messages: AgentChatMessageUi[],
  ): AgentChatMessageUi[] {
    const id = `assistant-${runId}-${event.round}-${event.index}`;
    const target = messages.find(
      (message) => message.kind === "assistant" && message.id === id,
    );
    if (
      !target ||
      target.kind !== "assistant" ||
      target.searchSuggestionsHtml.includes(event.html)
    )
      return messages;
    const budget = new SearchSuggestionsBudget();
    messages
      .filter(
        (message) =>
          message.kind === "assistant" &&
          assistantForRound(message.id, runId, event.round),
      )
      .forEach((message, ordinal) => {
        if (message.kind === "assistant")
          message.searchSuggestionsHtml.forEach((html) =>
            budget.accept(event.round, ordinal, html),
          );
      });
    if (!budget.accept(event.round, messages.length, event.html))
      return messages;
    return messages.map((message) =>
      message === target
        ? {
            ...target,
            searchSuggestionsHtml: [
              ...target.searchSuggestionsHtml,
              event.html,
            ],
          }
        : message,
    );
  }
  resetForReplay(
    runId: string,
    messages: AgentChatMessageUi[],
    supplements = new Set<number>(),
  ): AgentChatMessageUi[] {
    if (!runId.trim()) return messages;
    this.clearRun(runId);
    const ids = new Set(
      [...supplements].map((index) => `user-${runId}-supplement-${index}`),
    );
    return messages.filter(
      (message) =>
        !(message.kind === "assistant"
          ? isAssistantForRun(message.id, runId)
          : message.kind === "thinking"
            ? message.id.startsWith(`${runId}-thinking-`)
            : message.kind === "tool"
              ? message.id.startsWith(`${runId}-tool-`)
              : message.kind === "notice"
                ? isAssistantForRun(message.id, runId) ||
                  message.id === `interrupted-${runId}`
                : message.kind === "user"
                  ? ids.has(message.id)
                  : false),
    );
  }
  finishContextCompaction(
    runId: string,
    messages: AgentChatMessageUi[],
    detail: string,
  ): AgentChatMessageUi[] {
    return messages.map((message) =>
      message.kind === "notice" &&
      message.code === "ContextCompaction" &&
      message.id.startsWith(`assistant-${runId}-compaction-`) &&
      message.running
        ? { ...message, detail, running: false }
        : message,
    );
  }
  scheduleModelRetry(
    runId: string,
    event: ModelRetryScheduled,
    messages: AgentChatMessageUi[],
  ): AgentChatMessageUi[] {
    const finished = this.finalizeTextRound(
      runId,
      event.round,
      this.finalizeThinkingRound(runId, event.round, messages),
    );
    const notice = noticeMessage(
      `assistant-${runId}-retry-${event.round}`,
      "ModelRetry",
      modelRetryMessage(event),
    );
    return [...finished.filter((message) => message.id !== notice.id), notice];
  }
  startAssistantBlock(
    runId: string,
    event: BlockStart,
    messages: AgentChatMessageUi[],
  ): AgentChatMessageUi[] {
    return this.transitionVisibleBlock(
      runId,
      event.round,
      event.kind,
      event.index,
      messages,
    );
  }
  appendTextDelta(
    runId: string,
    round: number,
    index: number,
    delta: string,
    messages: AgentChatMessageUi[],
  ): AgentChatMessageUi[] {
    if (!delta) return messages;
    const transitioned = this.transitionVisibleBlock(
      runId,
      round,
      "TEXT",
      index,
      messages,
    );
    const id = `assistant-${runId}-${round}-${index}`;
    let updated = false;
    const next = transitioned.map((message) => {
      if (message.kind !== "assistant" || message.id !== id) return message;
      updated = true;
      return {
        ...message,
        content: message.content + delta,
        isStreaming: true,
        renderMarkdown: false,
      };
    });
    return updated
      ? next
      : [...next, { ...agentMessage(id, delta, true), renderMarkdown: false }];
  }
  appendReasoningDelta(
    runId: string,
    round: number,
    index: number,
    delta: string,
    messages: AgentChatMessageUi[],
  ): AgentChatMessageUi[] {
    if (!delta) return messages;
    const transitioned = this.transitionVisibleBlock(
      runId,
      round,
      "THINKING",
      index,
      messages,
    );
    const id = `${runId}-thinking-${round}-${index}`;
    const elapsedSeconds = this.elapsedSeconds(id);
    let updated = false;
    const next = transitioned.map((message) => {
      if (message.kind !== "thinking" || message.id !== id) return message;
      updated = true;
      return {
        ...message,
        content: message.content + delta,
        isStreaming: true,
        elapsedSeconds,
        collapsed: false,
      };
    });
    return updated
      ? next
      : [...next, { ...thinkingMessage(id, delta, true), elapsedSeconds }];
  }
  ensureCompletedThinking(
    runId: string,
    round: number,
    content: string,
    messages: AgentChatMessageUi[],
  ): AgentChatMessageUi[] {
    if (
      messages.some(
        (message) =>
          message.kind === "thinking" &&
          thinkingForRound(message.id, runId, round),
      )
    )
      return this.finalizeThinkingRound(runId, round, messages);
    const id = `${runId}-thinking-${round}-fallback`;
    const message = {
      ...thinkingMessage(id, content),
      elapsedSeconds: this.elapsedSeconds(id),
      collapsed: true,
    };
    const index = messages.findIndex(
      (value) =>
        value.kind === "assistant" && assistantForRound(value.id, runId, round),
    );
    return index < 0
      ? [...messages, message]
      : [...messages.slice(0, index), message, ...messages.slice(index)];
  }
  finalizeThinking(
    runId: string,
    messages: AgentChatMessageUi[],
  ): AgentChatMessageUi[] {
    return messages.map((message) =>
      message.kind === "thinking" && message.id.startsWith(`${runId}-thinking-`)
        ? this.finished(message)
        : message,
    );
  }
  finalizeRun(
    runId: string,
    messages: AgentChatMessageUi[],
  ): AgentChatMessageUi[] {
    return this.finalizeText(runId, this.finalizeThinking(runId, messages)).map(
      (message) =>
        message.kind === "tool" &&
        message.id.startsWith(`${runId}-tool-`) &&
        message.status === "Running"
          ? { ...message, status: "Unknown" }
          : message,
    );
  }
  finalizeThinkingRound(
    runId: string,
    round: number,
    messages: AgentChatMessageUi[],
  ): AgentChatMessageUi[] {
    return messages.map((message) =>
      message.kind === "thinking" && thinkingForRound(message.id, runId, round)
        ? this.finished(message)
        : message,
    );
  }
  finalizeThinkingBlock(
    runId: string,
    round: number,
    index: number,
    content: string | null,
    messages: AgentChatMessageUi[],
  ): AgentChatMessageUi[] {
    const id = `${runId}-thinking-${round}-${index}`;
    return messages.map((message) =>
      message.kind === "thinking" && message.id === id
        ? this.finished(message, content)
        : message,
    );
  }
  finalizeText(
    runId: string,
    messages: AgentChatMessageUi[],
  ): AgentChatMessageUi[] {
    return messages.map((message) =>
      message.kind === "assistant" && isAssistantForRun(message.id, runId)
        ? {
            ...message,
            content: message.content.trimEnd(),
            isStreaming: false,
            renderMarkdown: true,
          }
        : message,
    );
  }
  finalizeTextRound(
    runId: string,
    round: number,
    messages: AgentChatMessageUi[],
  ): AgentChatMessageUi[] {
    return messages.map((message) =>
      message.kind === "assistant" &&
      assistantForRound(message.id, runId, round)
        ? {
            ...message,
            content: message.content.trimEnd(),
            isStreaming: false,
            renderMarkdown: true,
          }
        : message,
    );
  }
  finalizeTextBlock(
    runId: string,
    round: number,
    index: number,
    content: string | null,
    messages: AgentChatMessageUi[],
  ): AgentChatMessageUi[] {
    const id = `assistant-${runId}-${round}-${index}`;
    return messages.map((message) =>
      message.kind === "assistant" && message.id === id
        ? {
            ...message,
            content: (content ?? message.content).trimEnd(),
            isStreaming: false,
            renderMarkdown: true,
          }
        : message,
    );
  }
  startTool(
    runId: string,
    event: ToolStarted,
    messages: AgentChatMessageUi[],
  ): AgentChatMessageUi[] {
    const message = toolActivityMessage(
      toolId(runId, event.round, event.toolCallId),
      event.name,
      "Running",
      event.argsPreview,
      event.command,
    );
    return messages.some((value) => value.id === message.id)
      ? messages
      : [...messages, message];
  }
  finishTool(
    runId: string,
    event: ToolFinished,
    messages: AgentChatMessageUi[],
  ): AgentChatMessageUi[] {
    const id = toolId(runId, event.round, event.toolCallId);
    const status =
      event.success === true
        ? "Success"
        : event.success === false
          ? "Failed"
          : /ok=false/i.test(event.resultSummary)
            ? "Failed"
            : "Success";
    const target = lastIndex(
      messages,
      (message) => message.kind === "tool" && message.id === id,
    );
    if (target < 0) return messages;
    return messages.map((message, index) =>
      index === target && message.kind === "tool"
        ? {
            ...message,
            status,
            command: event.command ?? message.command,
            resultSummary: event.resultSummary,
            imageCount: event.imageCount,
          }
        : message,
    );
  }
  updateSubagentProgress(
    runId: string,
    event: SubagentProgress,
    messages: AgentChatMessageUi[],
  ): AgentChatMessageUi[] {
    const id = toolId(runId, event.round, event.toolCallId);
    const target = lastIndex(
      messages,
      (message) => message.kind === "tool" && message.id === id,
    );
    return target < 0
      ? messages
      : messages.map((message, index) =>
          index === target && message.kind === "tool"
            ? { ...message, command: event.snapshotJson }
            : message,
        );
  }
  startHostedTool(
    runId: string,
    event: HostedToolStarted,
    messages: AgentChatMessageUi[],
  ): AgentChatMessageUi[] {
    const message = toolActivityMessage(
      toolId(runId, event.round, event.toolCallId),
      event.name,
      "Running",
    );
    return messages.some((value) => value.id === message.id)
      ? messages
      : [...messages, message];
  }
  finishHostedTool(
    runId: string,
    event: HostedToolFinished,
    messages: AgentChatMessageUi[],
  ): AgentChatMessageUi[] {
    const id = toolId(runId, event.round, event.toolCallId);
    return messages.map((message) =>
      message.kind === "tool" && message.id === id
        ? {
            ...message,
            status: event.success ? "Success" : "Failed",
            resultSummary: null,
          }
        : message,
    );
  }
  failRunningTools(
    reason: string,
    messages: AgentChatMessageUi[],
  ): AgentChatMessageUi[] {
    return messages.map((message) =>
      message.kind === "tool" && message.status === "Running"
        ? { ...message, status: "Failed", resultSummary: reason.slice(0, 48) }
        : message,
    );
  }
  interruptRunningTools(
    reason: string,
    messages: AgentChatMessageUi[],
  ): AgentChatMessageUi[] {
    return messages.map((message) =>
      message.kind === "tool" && message.status === "Running"
        ? { ...message, status: "Unknown", resultSummary: reason.slice(0, 48) }
        : message,
    );
  }
  clearRun(runId: string): void {
    for (const id of this.thinkingStartedAt.keys())
      if (id.startsWith(`${runId}-thinking-`))
        this.thinkingStartedAt.delete(id);
  }
  private finished(
    message: ThinkingMessageUi,
    content: string | null = null,
  ): ThinkingMessageUi {
    const start = this.thinkingStartedAt.get(message.id);
    return {
      ...message,
      content: content ?? message.content,
      isStreaming: false,
      elapsedSeconds:
        start === undefined
          ? message.elapsedSeconds
          : Math.max(0, Math.trunc((this.nowElapsedRealtime() - start) / 1000)),
      collapsed: true,
    };
  }
  private elapsedSeconds(id: string): number {
    let start = this.thinkingStartedAt.get(id);
    if (start === undefined) {
      start = this.nowElapsedRealtime();
      this.thinkingStartedAt.set(id, start);
    }
    return Math.max(0, Math.trunc((this.nowElapsedRealtime() - start) / 1000));
  }
  private transitionVisibleBlock(
    runId: string,
    round: number,
    kind: AssistantBlockKind,
    index: number,
    messages: AgentChatMessageUi[],
  ): AgentChatMessageUi[] {
    const assistant =
      kind === "TEXT" ? `assistant-${runId}-${round}-${index}` : null;
    const thinking =
      kind === "THINKING" ? `${runId}-thinking-${round}-${index}` : null;
    return messages.map((message) =>
      message.kind === "assistant" &&
      message.isStreaming &&
      assistantForRound(message.id, runId, round) &&
      message.id !== assistant
        ? {
            ...message,
            content: message.content.trimEnd(),
            isStreaming: false,
            renderMarkdown: true,
          }
        : message.kind === "thinking" &&
            message.isStreaming &&
            thinkingForRound(message.id, runId, round) &&
            message.id !== thinking
          ? this.finished(message)
          : message,
    );
  }
  static mergeCompactionResultNotice(
    runId: string,
    messages: AgentChatMessageUi[],
    ok: boolean,
    detail: string,
  ): AgentChatMessageUi[] {
    const index = lastIndex(
      messages,
      (message) =>
        message.kind === "notice" &&
        message.code === "ContextCompaction" &&
        message.id.startsWith(`assistant-${runId}-compaction-`),
    );
    if (index < 0)
      return [
        ...messages,
        noticeMessage(
          `assistant-${runId}-compaction-result`,
          "ContextCompaction",
          detail,
        ),
      ];
    const notice = messages[index];
    if (ok && notice.kind === "notice" && !notice.running) return messages;
    return messages.map((message, i) =>
      i === index && message.kind === "notice"
        ? { ...message, detail, running: false }
        : message,
    );
  }
  private static lastRetryIndex(
    runId: string,
    messages: AgentChatMessageUi[],
  ): number {
    return lastIndex(
      messages,
      (message) =>
        message.kind === "notice" &&
        message.code === "ModelRetry" &&
        message.id.startsWith(`assistant-${runId}-retry-`),
    );
  }
  static resultTargetIndex(
    runId: string,
    messages: AgentChatMessageUi[],
    includeNotices = false,
  ): number {
    const retry = this.lastRetryIndex(runId, messages);
    return lastIndex(
      messages,
      (message, index) =>
        index > retry &&
        (message.kind === "assistant" ||
          (includeNotices && message.kind === "notice")) &&
        isAssistantForRun(message.id, runId),
    );
  }
  static resultFallbackId(
    runId: string,
    messages: AgentChatMessageUi[],
  ): string {
    const retry = messages[this.lastRetryIndex(runId, messages)];
    if (!retry) return `assistant-${runId}-1`;
    const suffix = retry.id.slice(retry.id.lastIndexOf("-") + 1);
    const round = /^\d+$/.test(suffix) ? Number(suffix) + 1 : 1;
    return `assistant-${runId}-${round}-result`;
  }
  static applyResult(
    runId: string,
    messages: AgentChatMessageUi[],
    content: string,
    notice: SystemNoticeCode | null,
    detail: string | null = null,
  ): AgentChatMessageUi[] {
    const index = this.resultTargetIndex(runId, messages);
    const target = messages[index];
    if (index < 0 || target.kind !== "assistant") {
      const id = this.resultFallbackId(runId, messages);
      return [
        ...messages,
        notice === null
          ? agentMessage(id, content)
          : noticeMessage(id, notice, detail),
      ];
    }
    const round = assistantRound(target.id, runId);
    const count =
      round === null
        ? 0
        : messages.filter(
            (message) =>
              message.kind === "assistant" &&
              assistantRound(message.id, runId) === round,
          ).length;
    const replacement =
      notice === null
        ? {
            ...target,
            content:
              count <= 1 || !target.content.trim() ? content : target.content,
            isStreaming: false,
            renderMarkdown: true,
          }
        : noticeMessage(target.id, notice, detail);
    return messages.map((message, i) => (i === index ? replacement : message));
  }
}
