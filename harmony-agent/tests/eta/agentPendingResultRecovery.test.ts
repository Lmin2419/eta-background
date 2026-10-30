// Ported from app/src/test/kotlin/io/github/mangi/eta/ui/app/AgentPendingResultRecoveryTest.kt @ 3b1794d
// Copyright © 2026 蛮吉 (Mangi-11). PolyForm Noncommercial 1.0.0.
import test from "node:test";
import assert from "node:assert/strict";
import { AgentPendingResultRecovery as Recovery } from "../../entry/src/main/ets/core/eta/agentPendingResultRecovery";
import {
  emptyChatState,
  agentMessage,
  noticeMessage,
  conversationMessage,
  toolActivityMessage,
} from "../../entry/src/main/ets/core/eta/model/conversation";
import { runResult } from "../../entry/src/main/ets/core/eta/model/runtimeProjection";
test("recoveryDoesNotReplaceFailedAttemptOrRetryNotice", () => {
  const partial = agentMessage("assistant-retry-run-1-0", "半截回答");
  const notice = noticeMessage(
    "assistant-retry-run-retry-1",
    "ModelRetry",
    "正在重试",
  );
  const state = {
    ...emptyChatState(),
    messages: [partial, notice],
    isStreaming: true,
  };
  for (const ok of [true, false]) {
    const recovered = Recovery.apply(
      state,
      "retry-run",
      {
        ...runResult("retry-run", ok, ok ? "最终回答" : ""),
        error: ok ? null : "重试耗尽",
      },
      [],
    );
    assert.deepEqual(recovered.state.messages.slice(0, 2), [partial, notice]);
    assert.equal(
      recovered.state.messages.at(-1)?.id,
      "assistant-retry-run-2-result",
    );
    assert.equal(recovered.state.messages.length, 3);
  }
});
test("recoveryFinalizesLatestRoundAndAppendsTranscriptExactlyOnce", () => {
  const transcript = [conversationMessage("assistant", "最终结果")];
  const state = {
    ...emptyChatState(),
    messages: [
      {
        ...agentMessage("assistant-run-1-1", "中间结果"),
        renderMarkdown: false,
      },
      {
        ...agentMessage("assistant-run-1-2", "部分", true),
        renderMarkdown: false,
      },
    ],
    isStreaming: true,
  };
  const supplement = { index: 1, text: "补充条件", createdAt: 1 };
  const result = { ...runResult("run-1", true, "最终结果"), transcript };
  const recovered = Recovery.apply(state, "run-1", result, [supplement]);
  assert.equal(recovered.alreadyApplied, false);
  assert.deepEqual(
    recovered.state.messages.map((value) => value.id),
    ["assistant-run-1-1", "user-run-1-supplement-1", "assistant-run-1-2"],
  );
  const latest = recovered.state.messages.at(-1);
  assert.equal(latest?.kind, "assistant");
  if (latest?.kind === "assistant") {
    assert.equal(latest.content, "最终结果");
    assert.equal(latest.isStreaming, false);
    assert.ok(latest.renderMarkdown);
  }
  assert.deepEqual(recovered.state.history, transcript);
  assert.equal(recovered.state.isStreaming, false);
  const replay = Recovery.apply(recovered.state, "run-1", result, [supplement]);
  assert.ok(replay.alreadyApplied);
  assert.deepEqual(replay.state, recovered.state);
});
test("recoveryCreatesAssistantWhenStreamingPlaceholderWasNeverPersisted", () => {
  const recovered = Recovery.apply(
    { ...emptyChatState(), isStreaming: true },
    "run-2",
    { ...runResult("run-2", false, ""), error: "失败原因" },
    [],
  );
  assert.equal(recovered.state.messages.length, 1);
  const message = recovered.state.messages[0];
  assert.equal(message.id, "assistant-run-2-1");
  assert.equal(message.kind, "notice");
  if (message.kind === "notice") {
    assert.equal(message.code, "RuntimeFailed");
    assert.equal(message.detail, "失败原因");
  }
});
test("recoveryDoesNotMergeEarlierTextBlockIntoSameRoundFinalBlock", () => {
  const state = {
    ...emptyChatState(),
    messages: [
      agentMessage("assistant-run-blocks-1-1", "我先搜索。"),
      agentMessage("assistant-run-blocks-1-3", "这是最终答案。"),
    ],
    isStreaming: true,
  };
  const content = "我先搜索。这是最终答案。";
  const recovered = Recovery.apply(
    state,
    "run-blocks",
    {
      ...runResult("run-blocks", true, content),
      transcript: [conversationMessage("assistant", content)],
    },
    [],
  );
  assert.deepEqual(
    recovered.state.messages
      .filter((value) => value.kind === "assistant")
      .map((value) => value.content),
    ["我先搜索。", "这是最终答案。"],
  );
});
test("recoveryUsesSemanticNoticeForSuccessfulRunWithoutText", () => {
  const recovered = Recovery.apply(
    { ...emptyChatState(), isStreaming: true },
    "run-empty",
    runResult("run-empty", true, ""),
    [],
  );
  assert.equal(recovered.state.messages.length, 1);
  const message = recovered.state.messages[0];
  assert.equal(message.kind, "notice");
  if (message.kind === "notice") {
    assert.equal(message.code, "EmptyResult");
    assert.equal(message.detail, null);
  }
});
test("completedOutboxResultKeepsToolTraceAndRemovesInterruptedNotice", () => {
  const tool = toolActivityMessage(
    "run-tool-tool-1-call-1",
    "run_command",
    "Success",
    "执行命令 · Android · root",
    "uptime",
    "完成",
  );
  const recovered = Recovery.apply(
    {
      ...emptyChatState(),
      messages: [tool, noticeMessage("interrupted-run-tool", "Interrupted")],
    },
    "run-tool",
    {
      ...runResult("run-tool", true, "最终结果"),
      transcript: [conversationMessage("assistant", "最终结果")],
    },
    [],
  );
  assert.equal(recovered.alreadyApplied, false);
  assert.deepEqual(recovered.state.messages[0], tool);
  assert.ok(
    recovered.state.messages.every(
      (value) => value.id !== "interrupted-run-tool",
    ),
  );
  const last = recovered.state.messages.at(-1);
  assert.equal(last?.kind, "assistant");
  if (last?.kind === "assistant") assert.equal(last.content, "最终结果");
});
test("appliedMarkerPreventsOldOutboxReplayAfterLaterTurns", () => {
  const state = {
    ...emptyChatState(),
    messages: [
      agentMessage("assistant-run-1-1", "第一轮"),
      agentMessage("assistant-run-2-1", "第二轮"),
    ],
    history: [
      conversationMessage("assistant", "第一轮"),
      conversationMessage("assistant", "第二轮"),
    ],
    appliedRuntimeRunIds: ["run-1", "run-2"],
  };
  const replay = Recovery.apply(
    state,
    "run-1",
    {
      ...runResult("run-1", true, "第一轮"),
      transcript: [conversationMessage("assistant", "第一轮")],
    },
    [],
  );
  assert.ok(replay.alreadyApplied);
  assert.deepEqual(replay.state, state);
});
test("continuationPromptIsAddedToUiAndDurableHistoryOnce", () => {
  const prompt = { index: 1, text: "继续检查", createdAt: 10 };
  const recovered = Recovery.apply(
    emptyChatState(),
    "run-next",
    {
      ...runResult("run-next", true, "检查完成"),
      transcript: [conversationMessage("assistant", "检查完成")],
    },
    [],
    prompt,
  );
  assert.deepEqual(
    recovered.state.messages.map((value) => value.id),
    ["user-run-next-supplement-1", "assistant-run-next-1"],
  );
  assert.deepEqual(
    recovered.state.history.map((value) => value.role),
    ["user", "assistant"],
  );
  assert.equal(recovered.state.history[0].content, "继续检查");
});
