// Ported from app/src/test/kotlin/io/github/mangi/eta/ui/app/RoleplayConversationReducerTest.kt @ 3b1794d
// Copyright © 2026 蛮吉 (Mangi-11). PolyForm Noncommercial 1.0.0.
import test from "node:test";
import assert from "node:assert/strict";
import { RoleplayConversationReducer as Reducer } from "../../entry/src/main/ets/core/eta/roleplayConversationReducer";
import { AgentPendingResultRecovery as Recovery } from "../../entry/src/main/ets/core/eta/agentPendingResultRecovery";
import { AgentRuntimeHistoryReducer as History } from "../../entry/src/main/ets/core/eta/agentRuntimeHistoryReducer";
import {
  AgentChatUiState,
  AgentChatMessageUi,
  emptyChatState,
  agentMessage,
  userMessage,
  conversationMessage as entry,
} from "../../entry/src/main/ets/core/eta/model/conversation";
import {
  runResult,
  RuntimeRunResult,
  revisionContent,
} from "../../entry/src/main/ets/core/eta/model/runtimeProjection";
function conversation(): AgentChatUiState {
  const journal = [
    { ...entry("assistant", "你好"), messageId: "greeting" },
    { ...entry("user", "查询日历"), messageId: "user-r1" },
    {
      ...entry("assistant"),
      toolCallsJson:
        '[{"id":"tool-1","type":"function","function":{"name":"get_current_context","arguments":"{}"}}]',
      messageId: "assistant-r1-1",
    },
    { ...entry("tool", "今天有会议"), toolCallId: "tool-1" },
    { ...entry("assistant", "今天有会议。"), messageId: "assistant-r1-2" },
    { ...entry("user", "谢谢"), messageId: "user-r2" },
  ];
  return Reducer.linkRun(
    {
      ...emptyChatState(),
      messages: [
        agentMessage("greeting", "你好"),
        userMessage("user-r1", "查询日历"),
        agentMessage("assistant-r1-2-0", "今天有会议。"),
        userMessage("user-r2", "谢谢"),
      ],
      history: journal,
      journal,
      roleplay: { characterId: "c1", characterJson: "{}", name: "角色" },
    },
    "r1",
  );
}
function pagedConversation(): AgentChatUiState {
  const journal = [
    { ...entry("assistant", "第一段\n\n第二段"), messageId: "reply" },
  ];
  const state = emptyChatState();
  return {
    ...state,
    messages: [agentMessage("reply-1", "第二段")],
    history: journal,
    journal,
    roleplay: { characterId: "character", characterJson: "{}", name: "角色" },
    roleplayMessages: {
      ...state.roleplayMessages,
      links: new Map([
        ["reply-0", { transcriptMessageId: "reply", blockOrder: 0 }],
        ["reply-1", { transcriptMessageId: "reply", blockOrder: 1 }],
      ]),
    },
    roleplayOriginalContents: new Map([["reply-0", "第一段"]]),
    messageStartIndex: 1,
    hasEarlierMessages: true,
  };
}
function rewrite(
  runId: string,
  ok: boolean,
  content: string,
  target: string | null = null,
  error: string | null = null,
): RuntimeRunResult {
  return {
    ...runResult(runId, ok, content),
    operation: "rewrite_reply",
    rewriteTargetMessageId: target,
    error,
  };
}
function content(message: AgentChatMessageUi): string | null {
  return message.kind === "assistant" || message.kind === "user"
    ? message.content
    : null;
}
test("editingUserPreservesFollowingMessagesAndExecutionAudit", () => {
  const state = conversation();
  const edited = Reducer.edit(state, "user-r1", "修改日历");
  assert.ok(edited);
  assert.deepEqual(edited.journal, state.journal);
  assert.equal(edited.messages.length, state.messages.length);
  assert.equal(content(edited.messages.at(-1)!), "谢谢");
  assert.ok(
    edited.history.some(
      (value) => value.role === "system" && value.content.includes("原请求"),
    ),
  );
  assert.equal(
    edited.history.find((value) => value.role === "tool")?.content,
    "今天有会议",
  );
  assert.equal(
    edited.history.find((value) => value.messageId === "user-r1")?.content,
    "修改日历",
  );
});
test("rewriteUsesOnlyPrefixAndRetainsToolEvidence", () => {
  const history = Reducer.rewriteHistory(conversation(), "assistant-r1-2-0");
  assert.ok(history);
  assert.equal(history.at(-1)?.role, "tool");
  assert.ok(
    history.every(
      (value) =>
        value.messageId !== "user-r2" && value.messageId !== "assistant-r1-2",
    ),
  );
});
test("rewriteResultIsIdempotentAndCandidatesCanBeRestored", () => {
  const state = conversation();
  const result = rewrite(
    "rewrite-1",
    true,
    "你的日历上今天有会议。",
    "assistant-r1-2-0",
  );
  const rewritten = Reducer.applyRewrite(state, result.runId, result);
  assert.deepEqual(rewritten.journal, state.journal);
  assert.equal(
    rewritten.roleplayMessages.revisions.get("assistant-r1-2-0")?.candidates
      .length,
    2,
  );
  assert.deepEqual(
    Reducer.applyRewrite(rewritten, result.runId, result),
    rewritten,
  );
  const restored = Reducer.select(rewritten, "assistant-r1-2-0", 0);
  assert.ok(restored);
  assert.equal(
    restored.history.find((value) => value.messageId === "assistant-r1-2")
      ?.content,
    "今天有会议。",
  );
  assert.equal(content(restored.messages.at(-1)!), "谢谢");
});
test("interruptedRewriteKeepsOriginalAndRemovesPendingMarker", () => {
  const initial = conversation();
  const state = {
    ...initial,
    roleplayMessages: {
      ...initial.roleplayMessages,
      pendingRewrites: new Map([["rewrite-1", "assistant-r1-2-0"]]),
    },
    isStreaming: true,
  };
  const result = rewrite("rewrite-1", false, "", null, "已停止");
  const recovered = Recovery.apply(state, result.runId, result, []).state;
  assert.deepEqual(recovered.journal, state.journal);
  assert.deepEqual(recovered.history, state.history);
  assert.equal(
    recovered.messages.filter((value) => value.kind === "assistant").at(-1)
      ?.content,
    "今天有会议。",
  );
  assert.equal(recovered.roleplayMessages.pendingRewrites.size, 0);
  assert.equal(recovered.isStreaming, false);
});
test("greetingCanBeEditedWithoutUserTurnAndDuplicateTextDoesNotChooseTarget", () => {
  const state = conversation();
  const edited = Reducer.edit(state, "greeting", "新的开场白");
  assert.ok(edited);
  assert.equal(edited.history[0].content, "新的开场白");
  assert.deepEqual(edited.journal, state.journal);
  assert.equal(Reducer.edit(state, "missing", "今天有会议。"), null);
});
test("rewriteOfLaterBlockKeepsEarlierSiblingWithoutFabricatingToolCalls", () => {
  const original = conversation();
  const state = Reducer.linkRun(
    {
      ...original,
      messages: original.messages.flatMap<AgentChatMessageUi>((message) =>
        message.id === "assistant-r1-2-0"
          ? [
              agentMessage(message.id, "第一段"),
              agentMessage("assistant-r1-2-1", "第二段"),
            ]
          : [message],
      ),
    },
    "r1",
  );
  const prefix = Reducer.rewriteHistory(state, "assistant-r1-2-1");
  assert.ok(prefix);
  assert.equal(prefix.at(-1)?.content, "第一段");
  assert.equal(prefix.at(-1)?.toolCallsJson, "");
  assert.ok(prefix.every((value) => value.content !== "第二段"));
});
test("legacySummaryIsRetainedWhenItIsTheOnlyEarlyHistory", () => {
  const state = conversation();
  const summary = {
    ...entry("assistant", "已有早期摘要"),
    contextSummary: true,
    compactedUserTurns: 5,
  };
  const edited = Reducer.edit(
    { ...state, journal: [summary, ...state.journal] },
    "user-r1",
    "新正文",
  );
  assert.ok(edited);
  assert.ok(edited.history.includes(summary));
  assert.ok(
    edited.history.some((value) => value.content.includes("已缺失的历史")),
  );
});
test("interruptedPendingSupplementRemainsEditableAfterRecovery", () => {
  const base = conversation();
  const supplement = userMessage("user-r3-supplement-1", "补充说明");
  const restored = Reducer.linkRun(
    History.apply(
      { ...base, messages: [...base.messages, supplement] },
      "r3",
      [],
      null,
      true,
    ).state,
    "r3",
  );
  const edited = Reducer.edit(restored, supplement.id, "更正的说明");
  assert.ok(edited);
  assert.equal(edited.journal.at(-1)?.messageId, supplement.id);
  assert.equal(edited.journal.at(-1)?.content, "补充说明");
  assert.equal(edited.history.at(-1)?.content, "更正的说明");
});
test("mismatchedRewriteTargetCannotModifyAnotherReply", () => {
  const state = Reducer.restorePendingRewrite(
    conversation(),
    "rewrite-1",
    "assistant-r1-2-0",
  );
  const recovered = Reducer.applyRewrite(
    state,
    "rewrite-1",
    rewrite("rewrite-1", true, "不应覆盖开场白", "greeting"),
  );
  assert.equal(recovered.roleplayMessages.revisions.size, 0);
  assert.deepEqual(recovered.history, state.history);
  assert.equal(content(recovered.messages[0]), "你好");
});
test("editingAPagedBlockPreservesItsUnloadedSiblings", () => {
  const state = pagedConversation();
  const edited = Reducer.edit(state, "reply-1", "修订第二段");
  assert.ok(edited);
  assert.equal(edited.history.length, 1);
  assert.equal(edited.history[0].content, "第一段\n\n修订第二段");
  assert.deepEqual(edited.journal, state.journal);
  assert.equal(edited.messages.length, 1);
  assert.equal(content(edited.messages[0]), "修订第二段");
  assert.deepEqual(
    Reducer.requiredOriginalContentIds(state),
    new Set(["reply-0"]),
  );
});
test("rewritingAPagedBlockUsesTheUnloadedPrefix", () => {
  const prefix = Reducer.rewriteHistory(pagedConversation(), "reply-1");
  assert.ok(prefix);
  assert.equal(prefix.length, 1);
  assert.equal(prefix[0].content, "第一段");
  assert.equal(prefix[0].toolCallsJson, "");
});
test("pagedRunLinkingKeepsOriginalBlockOrder", () => {
  const linked = Reducer.linkRun(pagedConversation(), "later-run");
  assert.equal(linked.roleplayMessages.links.get("reply-1")?.blockOrder, 1);
  const history = Reducer.rewriteHistory(linked, "reply-1");
  assert.ok(history);
  assert.equal(history.length, 1);
  assert.equal(history[0].content, "第一段");
});
test("missingSiblingRejectsTheRevisionInsteadOfDroppingItsBody", () => {
  const state = {
    ...pagedConversation(),
    roleplayOriginalContents: new Map<string, string>(),
  };
  assert.equal(Reducer.edit(state, "reply-1", "修订第二段"), null);
  assert.equal(Reducer.rewriteHistory(state, "reply-1"), null);
  assert.equal(state.history.length, 1);
  assert.equal(state.history[0].content, "第一段\n\n第二段");
});
test("pendingRewriteCanFinishWhenItsTargetIsOutsideTheWindow", () => {
  const source = pagedConversation();
  const state = {
    ...source,
    messages: [],
    roleplayOriginalContents: new Map([
      ["reply-0", "第一段"],
      ["reply-1", "第二段"],
    ]),
    roleplayMessages: {
      ...source.roleplayMessages,
      pendingRewrites: new Map([["rewrite", "reply-1"]]),
    },
  };
  const rewritten = Reducer.applyRewrite(
    state,
    "rewrite",
    rewrite("rewrite", true, "重写第二段", "reply-1"),
  );
  assert.equal(rewritten.history.length, 1);
  assert.equal(rewritten.history[0].content, "第一段\n\n重写第二段");
  assert.equal(
    revisionContent(rewritten.roleplayMessages.revisions.get("reply-1")!),
    "重写第二段",
  );
  assert.equal(rewritten.messages.length, 0);
  assert.equal(rewritten.roleplayMessages.pendingRewrites.size, 0);
});
