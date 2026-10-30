// Ported from app/src/test/kotlin/io/github/mangi/eta/ui/markdown/StreamingMarkdownParsingTest.kt @ 3b1794d
// Copyright © 2026 蛮吉 (Mangi-11). PolyForm Noncommercial 1.0.0.
import test from "node:test";
import assert from "node:assert/strict";
import {
  MarkdownTargets,
  MarkdownParseCancellation,
  markdownTarget,
  consumeStreamingMarkdownTargets,
} from "../../entry/src/main/ets/core/eta/markdown/streamingMarkdownState";
import {
  StreamingGfmParserSession,
  StreamingGfmSnapshot,
} from "../../entry/src/main/ets/core/eta/markdown/streamingGfmParser";
test("continuousAppendsPublishProgressBeforeTheStreamEnds", async () => {
  const targets = new MarkdownTargets();
  const parser = new StreamingGfmParserSession();
  const published: StreamingGfmSnapshot[] = [];
  const chunk = "继续分析这一段内容。\n\n";
  targets.send(markdownTarget(chunk, true));
  let count = 0;
  await consumeStreamingMarkdownTargets(
    targets,
    async (target) => {
      count++;
      if (count < 20)
        targets.send({ ...target, content: target.content + chunk });
      else targets.close();
      return parser.parse(target.content, !target.isStreaming);
    },
    (value) => published.push(value),
  );
  assert.equal(published.length, 20);
  assert.ok(published.every((value) => !value.isComplete));
  assert.deepEqual(
    published.map((value) => value.originalSource),
    Array.from({ length: 20 }, (_, index) => chunk.repeat(index + 1)),
  );
});
test("pendingTargetsAreCoalescedWithoutParsingEveryNetworkChunk", async () => {
  const targets = new MarkdownTargets();
  const parser = new StreamingGfmParserSession();
  const parsed: string[] = [];
  for (let i = 0; i < 100; i++)
    targets.send(markdownTarget("内容".repeat(i + 1), true));
  targets.close();
  await consumeStreamingMarkdownTargets(
    targets,
    async (target) => {
      parsed.push(target.content);
      return parser.parse(target.content, !target.isStreaming);
    },
    () => {},
  );
  assert.deepEqual(parsed, ["内容".repeat(100)]);
});
test("correctedSourceDoesNotPublishAnObsoleteSnapshot", async () => {
  const targets = new MarkdownTargets();
  const parser = new StreamingGfmParserSession();
  const published: StreamingGfmSnapshot[] = [];
  targets.send(markdownTarget("旧内容", true));
  await consumeStreamingMarkdownTargets(
    targets,
    async (target) => {
      if (target.content === "旧内容") {
        targets.send(markdownTarget("修正后的内容", false));
        targets.close();
      }
      return parser.parse(target.content, !target.isStreaming);
    },
    (value) => published.push(value),
  );
  assert.deepEqual(
    published.map((value) => value.originalSource),
    ["修正后的内容"],
  );
  assert.ok(published[0].isComplete);
});
test("completionWithUnchangedTextStillPublishesTheFinalSnapshot", async () => {
  const targets = new MarkdownTargets();
  const parser = new StreamingGfmParserSession();
  const published: StreamingGfmSnapshot[] = [];
  targets.send(markdownTarget("**最终内容**", true));
  await consumeStreamingMarkdownTargets(
    targets,
    async (target) => {
      if (target.isStreaming) {
        targets.send({ ...target, isStreaming: false });
        targets.close();
      }
      return parser.parse(target.content, !target.isStreaming);
    },
    (value) => published.push(value),
  );
  assert.deepEqual(
    published.map((value) => value.isComplete),
    [false, true],
  );
  assert.ok(
    published[published.length - 1].completedDocumentFor("**最终内容**"),
  );
});
test("resumedStreamDoesNotPublishAnObsoleteCompletion", async () => {
  const targets = new MarkdownTargets();
  const parser = new StreamingGfmParserSession();
  const published: StreamingGfmSnapshot[] = [];
  targets.send(markdownTarget("同一份内容", false));
  await consumeStreamingMarkdownTargets(
    targets,
    async (target) => {
      if (!target.isStreaming) {
        targets.send({ ...target, isStreaming: true });
        targets.close();
      }
      return parser.parse(target.content, !target.isStreaming);
    },
    (value) => published.push(value),
  );
  assert.equal(published.length, 1);
  assert.equal(published[0].isComplete, false);
});
test("disposingConsumerCancelsParsingWithoutPublishingLateResults", async () => {
  const targets = new MarkdownTargets();
  const cancel = new MarkdownParseCancellation();
  const published: StreamingGfmSnapshot[] = [];
  targets.send(markdownTarget("思考内容", true));
  let notifyStarted: () => void = () => {};
  const started = new Promise<void>((resolve) => {
    notifyStarted = resolve;
  });
  let finish: (snapshot: StreamingGfmSnapshot) => void = () => {};
  const task = consumeStreamingMarkdownTargets(
    targets,
    () => {
      notifyStarted();
      return new Promise((resolve) => {
        finish = resolve;
      });
    },
    (snapshot) => published.push(snapshot),
    cancel,
  );
  await started;
  cancel.cancel();
  await task;
  assert.ok(cancel.cancelled);
  assert.deepEqual(published, []);
  finish(new StreamingGfmParserSession().parse("思考内容", true));
  await Promise.resolve();
  assert.deepEqual(published, []);
});
