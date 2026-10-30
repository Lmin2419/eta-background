// Ported from app/src/test/kotlin/io/github/mangi/eta/ui/app/AgentRunMessageProjectorTest.kt @ 3b1794d
// Copyright © 2026 蛮吉 (Mangi-11). PolyForm Noncommercial 1.0.0.
import test from "node:test";
import assert from "node:assert/strict";
import { AgentRunMessageProjector as Projector } from "../../entry/src/main/ets/core/eta/agentRunMessageProjector";
import {
  AgentChatMessageUi,
  agentMessage,
  thinkingMessage,
  toolActivityMessage,
  noticeMessage,
  userMessage,
} from "../../entry/src/main/ets/core/eta/model/conversation";
import {
  AgentProjectionEvent,
  ToolStarted,
  ToolFinished,
  ModelRetryScheduled,
} from "../../entry/src/main/ets/core/eta/model/runtimeProjection";
function texts(messages: AgentChatMessageUi[]) {
  return messages.filter((message) => message.kind === "assistant");
}
function thoughts(messages: AgentChatMessageUi[]) {
  return messages.filter((message) => message.kind === "thinking");
}
function tools(messages: AgentChatMessageUi[]) {
  return messages.filter((message) => message.kind === "tool");
}
function notices(messages: AgentChatMessageUi[]) {
  return messages.filter((message) => message.kind === "notice");
}
function start(
  round: number,
  toolCallId: string,
  name: string,
  argsPreview = "{}",
  command: string | null = null,
): ToolStarted {
  return { type: "ToolStarted", round, toolCallId, name, argsPreview, command };
}
function finish(
  round: number,
  toolCallId: string,
  name: string,
  resultSummary: string,
  imageCount = 0,
  success: boolean | null = null,
): ToolFinished {
  return {
    type: "ToolFinished",
    round,
    toolCallId,
    name,
    resultSummary,
    imageCount,
    success,
    command: null,
  };
}
const fixed = () => new Projector(() => 1000);
test("searchSuggestionsBindToExactTextBlockAndDoNotPersistIntoAnotherRun", () => {
  const p = fixed();
  let messages = p.appendTextDelta("run", 1, 0, "第一段", []);
  messages = p.appendTextDelta("run", 1, 3, "带来源的回答", messages);
  const event: AgentProjectionEvent = {
    type: "SearchSuggestions",
    round: 1,
    index: 3,
    html: "<div>搜索建议</div>",
  };
  const attached = p.applyEvent("run", event, messages);
  assert.deepEqual(texts(attached)[0].searchSuggestionsHtml, []);
  assert.deepEqual(texts(attached)[1].searchSuggestionsHtml, [event.html]);
  assert.equal(p.applyEvent("run", event, attached), attached);
  assert.equal(p.applyEvent("other", event, attached), attached);
  assert.equal(p.applyEvent("run", { ...event, index: 7 }, attached), attached);
  const finalized = p.finalizeRun("run", attached);
  assert.deepEqual(texts(finalized)[1].searchSuggestionsHtml, [event.html]);
  assert.deepEqual(p.resetForReplay("run", finalized), []);
});
test("searchSuggestionsEnforcePerRoundBudgetAcrossMultipleAnswerBlocks", () => {
  const p = fixed();
  let messages = p.appendTextDelta("run", 1, 0, "第一段", []);
  messages = p.appendTextDelta("run", 1, 1, "第二段", messages);
  for (let index = 0; index < 5; index++)
    messages = p.applyEvent(
      "run",
      {
        type: "SearchSuggestions",
        round: 1,
        index: index % 2,
        html: `建议-${index}`,
      },
      messages,
    );
  assert.equal(
    texts(messages).reduce(
      (sum, message) => sum + message.searchSuggestionsHtml.length,
      0,
    ),
    5,
  );
  assert.equal(
    p.applyEvent(
      "run",
      { type: "SearchSuggestions", round: 1, index: 0, html: "第六个" },
      messages,
    ),
    messages,
  );
});
test("eventsWithoutVisibleChangeReturnSameListSoCallerSkipsTimestamp", () => {
  const p = fixed();
  const messages = p.appendTextDelta("run", 1, 0, "回答", []);
  const emptyUsage = {
    contextTokens: null,
    inputTokens: null,
    outputTokens: null,
    reasoningTokens: null,
    cachedTokens: null,
  };
  const events: AgentProjectionEvent[] = [
    { type: "RoundStarted" },
    { type: "ProviderRequestStarted" },
    { type: "AssistantReceived", round: 1, reasoningContent: "" },
    { type: "UsageReceived", round: 1, usage: emptyUsage },
    {
      type: "UsageReceived",
      round: 9,
      usage: { ...emptyUsage, inputTokens: 10, outputTokens: 2 },
    },
    { type: "UserSupplementReceived" },
  ];
  for (const event of events)
    assert.equal(p.applyEvent("run", event, messages), messages);
});
test("usageAttachesToRoundWithoutRestartingStreaming", () => {
  const p = fixed();
  const streaming = p.appendTextDelta("run", 1, 0, "回答", []);
  const finished = p.finalizeTextBlock("run", 1, 0, null, streaming);
  const messages = p.applyEvent(
    "run",
    {
      type: "UsageReceived",
      round: 1,
      usage: {
        contextTokens: 100,
        inputTokens: 80,
        outputTokens: 20,
        reasoningTokens: null,
        cachedTokens: null,
      },
    },
    finished,
  );
  assert.equal(texts(messages).length, 1);
  assert.equal(texts(messages)[0].usage?.contextTokens, 100);
  assert.equal(texts(messages)[0].isStreaming, false);
});
test("applyResultFillsSingleBlockKeepsMultiBlockAndReplacesWithNotice", () => {
  const p = fixed();
  const single = p.appendTextDelta("run", 1, 0, "流式半截", []);
  assert.deepEqual(
    texts(Projector.applyResult("run", single, "完整回答", null)).map(
      (message) => message.content,
    ),
    ["完整回答"],
  );
  const multi = p.appendTextDelta(
    "run",
    1,
    1,
    "第二段",
    p.finalizeTextBlock("run", 1, 0, null, single),
  );
  assert.deepEqual(
    texts(Projector.applyResult("run", multi, "合并全文", null)).map(
      (message) => message.content,
    ),
    ["流式半截", "第二段"],
  );
  const failed = Projector.applyResult(
    "run",
    single,
    "",
    "RuntimeFailed",
    "错误",
  );
  assert.equal(failed.length, 1);
  assert.equal(notices(failed)[0].id, single[0].id);
  assert.equal(notices(failed)[0].detail, "错误");
  assert.equal(
    Projector.applyResult("other", [], "回答", null)[0].id,
    "assistant-other-1",
  );
});
test("retryKeepsFailedAttemptSeparateAndReplayClearsItsNotice", () => {
  const p = fixed();
  const partial = p.appendTextDelta("retry-run", 2, 0, "半截", []);
  const event: ModelRetryScheduled = {
    type: "ModelRetryScheduled",
    round: 2,
    attempt: 1,
    maxAttempts: 3,
    delayMs: 2000,
    reasonCode: "MODEL_TIMEOUT",
  };
  const retrying = p.scheduleModelRetry("retry-run", event, partial);
  assert.equal(Projector.resultTargetIndex("retry-run", retrying), -1);
  assert.equal(
    Projector.resultFallbackId("retry-run", retrying),
    "assistant-retry-run-3-result",
  );
  assert.deepEqual(
    p.scheduleModelRetry("retry-run", event, retrying),
    retrying,
  );
  const resumed = p.appendTextDelta("retry-run", 3, 0, "完整回答", retrying);
  assert.deepEqual(
    texts(resumed).map((message) => message.content),
    ["半截", "完整回答"],
  );
  assert.equal(texts(resumed)[0].isStreaming, false);
  assert.equal(notices(resumed)[0].code, "ModelRetry");
  assert.deepEqual(p.resetForReplay("retry-run", resumed), []);
});
test("runCompletionFinalizesUnclosedBlocksAndUnknownToolWithoutChangingOtherRuns", () => {
  const p = fixed();
  const run = "run-final";
  const known = [
    toolActivityMessage(
      `${run}-tool-1-call-success`,
      "observe_screen",
      "Success",
      "{}",
      null,
      "完成",
    ),
    toolActivityMessage(
      `${run}-tool-1-call-failed`,
      "run_command",
      "Failed",
      "{}",
      null,
      "执行失败",
    ),
  ];
  const other = [
    agentMessage("assistant-run-other-1-0", "仍在输出", true),
    thinkingMessage("run-other-thinking-1-0", "仍在思考", true),
    toolActivityMessage(
      "run-other-tool-1-call-1",
      "observe_screen",
      "Running",
      "{}",
    ),
  ];
  const unfinished = toolActivityMessage(
    `${run}-tool-1-call-unknown`,
    "run_command",
    "Running",
    "{}",
  );
  const finalized = p.finalizeRun(run, [
    ...other,
    ...known,
    agentMessage(`assistant-${run}`, "", true),
    agentMessage(`assistant-${run}-1-0`, "回答\n\n", true),
    thinkingMessage(`${run}-thinking-1-0`, "思考", true),
    unfinished,
  ]);
  assert.deepEqual(finalized.slice(0, other.length + known.length), [
    ...other,
    ...known,
  ]);
  const assistant = texts(finalized).filter((message) =>
    message.id.startsWith(`assistant-${run}`),
  );
  assert.ok(
    assistant.every(
      (message) => !message.isStreaming && message.renderMarkdown,
    ),
  );
  assert.equal(assistant.at(-1)?.content, "回答");
  const thinking = thoughts(finalized).filter((message) =>
    message.id.startsWith(run),
  );
  assert.equal(thinking.length, 1);
  assert.equal(thinking[0].isStreaming, false);
  assert.ok(thinking[0].collapsed);
  assert.deepEqual(finalized.at(-1), { ...unfinished, status: "Unknown" });
});
test("replayResetRemovesOnlyRebuildableTraceAndExplicitlyReplayedSupplements", () => {
  const p = fixed();
  const run = "run-replay";
  const user = userMessage(`user-${run}`, "分析截图", ["image-preview"]);
  const legacy = userMessage(`user-${run}-supplement-1`, "原始补充");
  const replayed = userMessage(`user-${run}-supplement-2`, "继续检查");
  const other = [
    userMessage("user-other-run", "之前的问题"),
    agentMessage("assistant-other-run-1-0", "之前的回答"),
    thinkingMessage("other-run-thinking-1-0", "之前的思考"),
    toolActivityMessage(
      "other-run-tool-1-call-1",
      "observe_screen",
      "Success",
      "{}",
    ),
    userMessage("user-other-run-supplement-2", "之前的补充"),
  ];
  const messages = [
    ...other,
    user,
    agentMessage(`assistant-${run}`, "", true),
    agentMessage(`assistant-${run}-1-0`, "半截回答", true),
    thinkingMessage(`${run}-thinking-1-fallback`, "半截思考", true),
    toolActivityMessage(
      `${run}-tool-1-call-1`,
      "observe_screen",
      "Unknown",
      "{}",
    ),
    legacy,
    replayed,
    noticeMessage(`assistant-${run}-2`, "RuntimeFailed"),
    noticeMessage(`interrupted-${run}`, "Interrupted"),
  ];
  const reset = p.resetForReplay(run, messages, new Set([2]));
  assert.deepEqual(reset, [...other, user, legacy]);
  assert.deepEqual(p.resetForReplay(run, reset, new Set([2])), reset);
});
test("repeatedReplayRebuildsTextReasoningAndToolsWithoutAccumulatingContent", () => {
  const p = fixed();
  const run = "run-repeat";
  const user = userMessage(`user-${run}`, "看屏幕");
  let messages: AgentChatMessageUi[] = [user];
  for (let i = 0; i < 3; i++) {
    messages = p.resetForReplay(run, messages);
    messages = p.appendReasoningDelta(run, 1, 0, "先检查", messages);
    messages = p.finalizeThinking(run, messages);
    messages = p.startTool(run, start(1, "call-1", "observe_screen"), messages);
    messages = p.finishTool(
      run,
      finish(1, "call-1", "observe_screen", "完成", 0, true),
      messages,
    );
    messages = p.appendTextDelta(run, 2, 0, "检查", messages);
    messages = p.appendTextDelta(run, 2, 0, "完成", messages);
    messages = p.finalizeText(run, messages);
    assert.equal(messages.length, 4);
    assert.deepEqual(messages[0], user);
    assert.deepEqual(
      thoughts(messages).map((value) => value.content),
      ["先检查"],
    );
    assert.deepEqual(
      texts(messages).map((value) => value.content),
      ["检查完成"],
    );
    assert.deepEqual(
      tools(messages).map((value) => value.status),
      ["Success"],
    );
  }
});
test("replayResetClearsOnlyThatRunsThinkingClockAndKeepsUnreplayedUserInputs", () => {
  let now = 1000;
  const p = new Projector(() => now);
  const supplement = userMessage("user-run-reset-supplement-1", "保留这条补充");
  let messages: AgentChatMessageUi[] = [supplement];
  messages = p.appendReasoningDelta("run-reset", 1, 0, "先前思考", messages);
  messages = p.appendReasoningDelta("run-other", 1, 0, "其他思考", messages);
  now = 9000;
  messages = p.resetForReplay("run-reset", messages);
  messages = p.appendReasoningDelta("run-reset", 1, 0, "恢复思考", messages);
  now = 11000;
  messages = p.finalizeThinking("run-reset", messages);
  messages = p.finalizeThinking("run-other", messages);
  assert.ok(messages.includes(supplement));
  assert.equal(
    thoughts(messages).find((value) => value.id.startsWith("run-reset-"))
      ?.elapsedSeconds,
    2,
  );
  assert.equal(
    thoughts(messages).find((value) => value.id.startsWith("run-other-"))
      ?.elapsedSeconds,
    10,
  );
});
test("projectsReasoningAndToolsByRoundAndToolCallId", () => {
  let now = 1000;
  const p = new Projector(() => now);
  const run = "run-1";
  let messages: AgentChatMessageUi[] = [userMessage(`user-${run}`, "看屏幕")];
  messages = p.appendReasoningDelta(run, 1, 0, "先观察", messages);
  now = 4000;
  messages = p.startTool(
    run,
    start(1, "call_observe_1", "observe_screen"),
    p.finalizeThinkingRound(run, 1, messages),
  );
  messages = p.finishTool(
    run,
    finish(1, "call_observe_1", "observe_screen", "ok=true, chars=10", 1),
    messages,
  );
  now = 5000;
  messages = p.appendReasoningDelta(run, 2, 0, "再确认", messages);
  messages = p.startTool(
    run,
    start(
      2,
      "call_observe_2",
      "run_command",
      "执行命令 · Android · root",
      "pm list packages | head",
    ),
    p.finalizeThinkingRound(run, 2, messages),
  );
  assert.deepEqual(
    messages.map((value) => value.id),
    [
      `user-${run}`,
      `${run}-thinking-1-0`,
      `${run}-tool-1-call_observe_1`,
      `${run}-thinking-2-0`,
      `${run}-tool-2-call_observe_2`,
    ],
  );
  assert.equal(thoughts(messages)[0].isStreaming, false);
  assert.equal(thoughts(messages)[0].elapsedSeconds, 3);
  assert.equal(tools(messages)[0].status, "Success");
  assert.equal(tools(messages)[0].imageCount, 1);
  assert.equal(tools(messages)[1].status, "Running");
  assert.equal(
    tools(messages)[1].argumentsSummary,
    "执行命令 · Android · root",
  );
  assert.equal(tools(messages)[1].command, "pm list packages | head");
});
test("keepsAssistantTextSeparatedByRound", () => {
  const p = fixed();
  const run = "run-text";
  let messages: AgentChatMessageUi[] = [
    userMessage(`user-${run}`, "分析一下"),
    agentMessage(`assistant-${run}-1`, "第一轮"),
  ];
  messages = p.appendReasoningDelta(run, 2, 0, "继续推理", messages);
  messages = p.startTool(
    run,
    start(2, "call_2", "observe_screen"),
    p.finalizeThinkingRound(run, 2, messages),
  );
  messages = p.appendTextDelta(run, 2, 1, "第二轮", messages);
  messages = p.appendTextDelta(run, 2, 1, "回答", messages);
  assert.deepEqual(
    messages.map((value) => value.id),
    [
      `user-${run}`,
      `assistant-${run}-1`,
      `${run}-thinking-2-0`,
      `${run}-tool-2-call_2`,
      `assistant-${run}-2-1`,
    ],
  );
  assert.equal(texts(messages).at(-1)?.content, "第二轮回答");
  assert.equal(texts(messages).at(-1)?.isStreaming, true);
});
test("keepsFallbackToolCallIdsDistinctAcrossRounds", () => {
  const p = fixed();
  const run = "run-fallback";
  let messages: AgentChatMessageUi[] = [userMessage(`user-${run}`, "操作手机")];
  messages = p.startTool(
    run,
    start(1, "tool_call_0", "search_apps", '{"query":"相机"}'),
    messages,
  );
  messages = p.finishTool(
    run,
    finish(1, "tool_call_0", "search_apps", "ok=true"),
    messages,
  );
  messages = p.startTool(
    run,
    start(2, "tool_call_0", "observe_screen", '{"include_screenshot":true}'),
    messages,
  );
  const rows = tools(messages);
  assert.equal(rows.length, 2);
  assert.equal(rows[0].toolName, "search_apps");
  assert.equal(rows[0].status, "Success");
  assert.equal(rows[1].toolName, "observe_screen");
  assert.equal(rows[1].status, "Running");
});
test("toolActivityFollowsAssistantTextStreamedInSameRound", () => {
  const p = fixed();
  const run = "run-order";
  let messages: AgentChatMessageUi[] = [userMessage(`user-${run}`, "搜一下")];
  messages = p.appendTextDelta(run, 1, 0, "先查找应用", messages);
  messages = p.startTool(run, start(1, "call_1", "search_apps"), messages);
  assert.deepEqual(
    messages.map((value) => value.id),
    [`user-${run}`, `assistant-${run}-1-0`, `${run}-tool-1-call_1`],
  );
});
test("finalizingTextTrimsTrailingWhitespace", () => {
  const p = fixed();
  const run = "run-trim";
  let messages: AgentChatMessageUi[] = [userMessage(`user-${run}`, "你好")];
  messages = p.appendTextDelta(run, 1, 0, "回答。\n\n", messages);
  messages = p.finalizeTextRound(run, 1, messages);
  assert.equal(texts(messages).at(-1)?.content, "回答。");
  assert.equal(texts(messages).at(-1)?.isStreaming, false);
});
test("interruptedToolBecomesUnknownInsteadOfFailed", () => {
  const p = fixed();
  const run = "run-interrupted";
  const running = p.startTool(
    run,
    start(1, "call-1", "run_command", "执行命令"),
    [userMessage(`user-${run}`, "重启设备")],
  );
  const rows = tools(p.interruptRunningTools("任务中断", running));
  assert.equal(rows.length, 1);
  assert.equal(rows[0].status, "Unknown");
  assert.equal(rows[0].resultSummary, "任务中断");
});
test("keepsInterleavedReasoningTextAndHostedToolInEventOrder", () => {
  const p = fixed();
  const run = "run-interleaved";
  let messages: AgentChatMessageUi[] = [
    userMessage(`user-${run}`, "搜索最新消息"),
  ];
  messages = p.appendReasoningDelta(run, 1, 0, "先判断需要搜索", messages);
  messages = p.appendTextDelta(run, 1, 1, "我先查一下。", messages);
  messages = p.startHostedTool(
    run,
    {
      type: "HostedToolStarted",
      round: 1,
      toolCallId: "ws_1",
      name: "网页搜索",
    },
    p.finalizeTextRound(run, 1, messages),
  );
  messages = p.finishHostedTool(
    run,
    {
      type: "HostedToolFinished",
      round: 1,
      toolCallId: "ws_1",
      name: "网页搜索",
      success: true,
    },
    messages,
  );
  messages = p.appendReasoningDelta(run, 1, 2, "整理搜索结果", messages);
  messages = p.appendTextDelta(run, 1, 3, "这是最终答案。", messages);
  assert.deepEqual(
    messages.map((value) => value.id),
    [
      `user-${run}`,
      `${run}-thinking-1-0`,
      `assistant-${run}-1-1`,
      `${run}-tool-1-ws_1`,
      `${run}-thinking-1-2`,
      `assistant-${run}-1-3`,
    ],
  );
  assert.equal(thoughts(messages)[0].isStreaming, false);
  assert.equal(texts(messages)[0].isStreaming, false);
  assert.equal(tools(messages)[0].status, "Success");
  assert.equal(thoughts(messages)[1].isStreaming, false);
  assert.equal(texts(messages)[1].isStreaming, true);
});
test("compactionResultMergesIntoExistingMarkerInsteadOfAppending", () => {
  const p = fixed();
  const run = "run-compact";
  const started = {
    ...noticeMessage(
      `assistant-${run}-compaction-op-1`,
      "ContextCompaction",
      "正在压缩上下文…",
    ),
    running: true,
  };
  const completed = {
    ...started,
    detail: "上下文已压缩：约 45,312 → 25,768 tokens",
    contextTokens: 25768,
    running: false,
  };
  const base = [userMessage(`user-${run}`, "整理上下文")];
  assert.deepEqual(
    Projector.mergeCompactionResultNotice(
      run,
      [...base, completed],
      true,
      "上下文压缩完成",
    ),
    [...base, completed],
  );
  assert.equal(
    notices(
      Projector.mergeCompactionResultNotice(
        run,
        [...base, completed],
        false,
        "上下文压缩失败",
      ),
    )[0].detail,
    "上下文压缩失败",
  );
  assert.equal(
    notices(
      Projector.mergeCompactionResultNotice(run, base, true, "上下文压缩完成"),
    )[0].id,
    `assistant-${run}-compaction-result`,
  );
  const finalized = notices(
    Projector.mergeCompactionResultNotice(
      run,
      [...base, started],
      true,
      "上下文压缩完成",
    ),
  );
  assert.equal(finalized[0].detail, "上下文压缩完成");
  assert.equal(finalized[0].running, false);
  const interrupted = notices(
    p.finishContextCompaction(run, [...base, started], "上下文压缩已中断"),
  );
  assert.equal(interrupted[0].detail, "上下文压缩已中断");
  assert.equal(interrupted[0].running, false);
  assert.deepEqual(
    p.finishContextCompaction(run, [...base, completed], "上下文压缩已中断"),
    [...base, completed],
  );
});
