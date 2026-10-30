// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/app/RoleplayConversationReducer.kt @ 3b1794d
// Copyright © 2026 蛮吉 (Mangi-11). PolyForm Noncommercial 1.0.0.
// Dependency of L4 result recovery. This does not enable the E08 character UI/runtime.
import {
  AgentChatUiState,
  ConversationMessage,
  conversationMessage,
  noticeMessage,
} from "./model/conversation";
import {
  RoleplayReplyRevision,
  RuntimeRunResult,
  revisionContent,
} from "./model/runtimeProjection";
export class RoleplayConversationReducer {
  static restorePendingRewrite(
    state: AgentChatUiState,
    runId: string,
    messageId: string | null,
  ): AgentChatUiState {
    if (
      !state.roleplay ||
      messageId === null ||
      state.appliedRuntimeRunIds.includes(runId)
    )
      return state;
    return {
      ...state,
      roleplayMessages: {
        ...state.roleplayMessages,
        pendingRewrites: new Map([
          ...state.roleplayMessages.pendingRewrites,
          [runId, messageId],
        ]),
      },
    };
  }
  static linkRun(state: AgentChatUiState, runId: string): AgentChatUiState {
    if (!state.roleplay) return state;
    const links = new Map(state.roleplayMessages.links);
    const journal = state.journal.length ? state.journal : state.history;
    for (const entry of journal.filter((value) => value.messageId.trim())) {
      if (entry.role === "user") {
        const message = state.messages.find(
          (value) => value.kind === "user" && value.id === entry.messageId,
        );
        if (message)
          links.set(message.id, {
            transcriptMessageId: entry.messageId,
            blockOrder: 0,
          });
      } else if (entry.role === "assistant")
        state.messages
          .filter(
            (value) =>
              value.kind === "assistant" &&
              (value.id === entry.messageId ||
                value.id.startsWith(entry.messageId + "-")),
          )
          .forEach((message, index) => {
            if (!links.has(message.id))
              links.set(message.id, {
                transcriptMessageId: entry.messageId,
                blockOrder: index,
              });
          });
    }
    const fallback = state.messages.find(
      (value) =>
        value.kind === "assistant" && value.id === `assistant-${runId}`,
    );
    const last = [...journal]
      .reverse()
      .find(
        (entry) =>
          entry.role === "assistant" &&
          entry.messageId.startsWith(`assistant-${runId}-`),
      );
    if (fallback && last)
      links.set(fallback.id, {
        transcriptMessageId: last.messageId,
        blockOrder: 0,
      });
    return this.decorate({
      ...state,
      roleplayMessages: { ...state.roleplayMessages, links },
    });
  }
  static decorate(state: AgentChatUiState): AgentChatUiState {
    if (!state.roleplay) return state;
    return {
      ...state,
      messages: state.messages.map((message) => {
        if (message.kind !== "assistant") return message;
        const revision = state.roleplayMessages.revisions.get(message.id);
        return {
          ...message,
          characterEditable: state.roleplayMessages.links.has(message.id),
          candidateCount: revision?.candidates.length ?? 1,
          selectedCandidate: revision?.selected ?? 0,
        };
      }),
    };
  }
  static edit(
    state: AgentChatUiState,
    messageId: string,
    content: string,
  ): AgentChatUiState | null {
    if (
      !state.roleplay ||
      !state.roleplayMessages.links.has(messageId) ||
      !content.trim()
    )
      return null;
    const original = this.messageContent(state, messageId);
    if (original === null) return null;
    const previous = state.roleplayMessages.revisions.get(messageId) ?? {
      original,
      candidates: [original],
      selected: 0,
    };
    return this.applyRevision(state, messageId, {
      ...previous,
      candidates: [...previous.candidates, content],
      selected: previous.candidates.length,
    });
  }
  static select(
    state: AgentChatUiState,
    messageId: string,
    index: number,
  ): AgentChatUiState | null {
    const revision = state.roleplayMessages.revisions.get(messageId);
    return !revision || index < 0 || index >= revision.candidates.length
      ? null
      : this.applyRevision(state, messageId, { ...revision, selected: index });
  }
  private static applyRevision(
    state: AgentChatUiState,
    id: string,
    revision: RoleplayReplyRevision,
  ): AgentChatUiState | null {
    const updated: AgentChatUiState = {
      ...state,
      messages: state.messages.map((message) =>
        message.id === id && message.kind === "user"
          ? { ...message, content: revisionContent(revision), isEdited: true }
          : message.id === id && message.kind === "assistant"
            ? { ...message, content: revisionContent(revision) }
            : message,
      ),
      roleplayMessages: {
        ...state.roleplayMessages,
        revisions: new Map([
          ...state.roleplayMessages.revisions,
          [id, revision],
        ]),
      },
    };
    const history = this.projectJournal(updated);
    return history === null ? null : this.decorate({ ...updated, history });
  }
  static requiredOriginalContentIds(state: AgentChatUiState): Set<string> {
    if (!state.roleplay) return new Set();
    const visible = new Set(
      state.messages
        .filter(
          (message) => message.kind === "user" || message.kind === "assistant",
        )
        .map((message) => message.id),
    );
    const relevant = new Set([
      ...visible,
      ...state.roleplayMessages.revisions.keys(),
      ...state.roleplayMessages.pendingRewrites.values(),
    ]);
    const transcriptIds = new Set(
      [...relevant]
        .map((id) => state.roleplayMessages.links.get(id)?.transcriptMessageId)
        .filter((id): id is string => id !== undefined),
    );
    return new Set(
      [...state.roleplayMessages.links]
        .filter(
          ([id, link]) =>
            transcriptIds.has(link.transcriptMessageId) &&
            !visible.has(id) &&
            !state.roleplayMessages.revisions.has(id),
        )
        .map(([id]) => id),
    );
  }
  static projectJournal(state: AgentChatUiState): ConversationMessage[] | null {
    const journal = state.journal.length ? state.journal : state.history;
    const result: ConversationMessage[] = [];
    if (journal.some((entry) => entry.contextSummary))
      result.push(
        conversationMessage(
          "system",
          "早期历史仅保留摘要；正文修订只应用于仍有原文的消息，不能恢复已缺失的历史。",
        ),
      );
    for (const entry of journal) {
      const links = [...state.roleplayMessages.links]
        .filter(([, link]) => link.transcriptMessageId === entry.messageId)
        .sort((a, b) => a[1].blockOrder - b[1].blockOrder);
      if (!links.some(([id]) => state.roleplayMessages.revisions.has(id))) {
        result.push(entry);
        continue;
      }
      const bodies: string[] = [];
      for (const [id] of links) {
        const content = this.messageContent(state, id);
        if (content === null) return null;
        bodies.push(content);
      }
      if (entry.role === "user")
        result.push(
          conversationMessage(
            "system",
            "以下历史用户正文经用户修订；后续已有工具记录来自原请求，不能视为修订后请求已被执行。",
          ),
        );
      result.push({ ...entry, content: bodies.join("\n\n"), contentJson: "" });
    }
    return result;
  }
  static rewriteHistory(
    state: AgentChatUiState,
    messageId: string,
  ): ConversationMessage[] | null {
    const link = state.roleplayMessages.links.get(messageId);
    if (!link) return null;
    const history = this.projectJournal(state);
    if (!history) return null;
    const index = history.findIndex(
      (entry) => entry.messageId === link.transcriptMessageId,
    );
    if (index < 0) return null;
    const siblings = [...state.roleplayMessages.links]
      .filter(
        ([, value]) =>
          value.transcriptMessageId === link.transcriptMessageId &&
          value.blockOrder < link.blockOrder,
      )
      .sort((a, b) => a[1].blockOrder - b[1].blockOrder);
    const bodies: string[] = [];
    for (const [id] of siblings) {
      const content = this.messageContent(state, id);
      if (content === null) return null;
      bodies.push(content);
    }
    return [
      ...history.slice(0, index),
      ...(bodies.length
        ? [conversationMessage("assistant", bodies.join("\n\n"))]
        : []),
    ];
  }
  private static messageContent(
    state: AgentChatUiState,
    id: string,
  ): string | null {
    const revision = state.roleplayMessages.revisions.get(id);
    if (revision) return revisionContent(revision);
    const message = state.messages.find((value) => value.id === id);
    return message?.kind === "user" || message?.kind === "assistant"
      ? message.content
      : (state.roleplayOriginalContents.get(id) ?? null);
  }
  static applyRewrite(
    state: AgentChatUiState,
    runId: string,
    result: RuntimeRunResult,
  ): AgentChatUiState {
    if (state.appliedRuntimeRunIds.includes(runId)) return state;
    const expected = state.roleplayMessages.pendingRewrites.get(runId);
    const target = result.rewriteTargetMessageId ?? expected;
    const edited =
      result.ok &&
      result.content.trim() &&
      target !== undefined &&
      (expected === undefined || target === expected)
        ? this.edit(state, target, result.content)
        : null;
    const base = edited ?? state;
    const pendingRewrites = new Map(base.roleplayMessages.pendingRewrites);
    pendingRewrites.delete(runId);
    return {
      ...base,
      messages: edited
        ? base.messages
        : [
            ...base.messages,
            noticeMessage(
              `rewrite-result-${runId}`,
              "RuntimeFailed",
              result.error ?? "重新生成未完成，已保留原回复。",
            ),
          ],
      roleplayMessages: { ...base.roleplayMessages, pendingRewrites },
      appliedRuntimeRunIds: [...base.appliedRuntimeRunIds, runId],
      isStreaming: false,
    };
  }
}
