// Ported from app/src/test/kotlin/io/github/mangi/eta/ui/app/ConversationMarkdownExporterTest.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
// Filename prefix changes Eta → 随身助手 as required by ETA_PORT §3.
import test from 'node:test';
import assert from 'node:assert/strict';
import { ConversationMarkdownExporter as E, ExportLabels } from '../../entry/src/main/ets/core/eta/conversationMarkdownExporter';
import { userMessage as u, agentMessage as a, thinkingMessage as t, toolActivityMessage as tool, noticeMessage as n, AgentChatMessageUi } from '../../entry/src/main/ets/core/eta/model/conversation';
import { AgentFileReferencePromptCodec as Files } from '../../entry/src/main/ets/core/eta/model/agentFileReferencePrompt';
const labels: ExportLabels = { user: 'User', assistant: 'Assistant', thinking: 'Thinking', toolLineFormat: 'Tool: %1$s (%2$s)', toolsLineFormat: 'Tools: %1$s', argumentsFormat: 'Arguments: %1$s', resultFormat: 'Result: %1$s', imagesFormat: 'Images: %1$d', toolStatusRunning: 'Running', toolStatusSuccess: 'Completed', toolStatusFailed: 'Failed', toolStatusUnknown: 'Result unknown', noticeStopped: 'Stopped', noticeEmptyResult: 'Empty result', noticeModelRetry: 'Retrying', noticeRuntimeFailed: 'Runtime failed', noticeInterrupted: 'Interrupted' };
test('exportRendersFullConversationInOrder', () => {
  const messages: AgentChatMessageUi[] = [u('u1', '你好'), a('a1', '你好！**有什么可以帮你？**'), t('t1', '先思考'), tool('tool1', 'run_terminal_command', 'Success', 'ls -la', 'ls -la', 'ok'), { kind: 'toolSummary', id: 'ts1', tools: ['observe_screen', 'tap_element'] }, n('n1', 'Stopped', '用户取消'), a('a2', '结束')];
  const expected = '# 周会纪要\n\n## User\n\n你好\n\n## Assistant\n\n你好！**有什么可以帮你？**\n\n<details>\n<summary>Thinking</summary>\n\n先思考\n\n</details>\n\n> Tool: run_terminal_command (Completed)\n>\n> ls -la\n>\n> Arguments: ls -la\n>\n> Result: ok\n\n> Tools: observe_screen, tap_element\n\n> Stopped\n>\n> 用户取消\n\n## Assistant\n\n结束\n';
  assert.equal(E.export('周会纪要', messages, labels), expected);
});
test('exportParsesFileReferencesInUserContent', () => {
  const content = Files.format('总结一下这个文件', [{ displayName: 'notes.txt', absolutePath: '/sdcard/notes.txt', kind: 'File' }]);
  const result = E.export('t', [u('u', content)], labels); assert.ok(result.includes('总结一下这个文件')); assert.ok(result.includes('- /sdcard/notes.txt')); assert.ok(!result.includes('# Files mentioned by the user:'));
});
test('exportAnnotatesImagesWithoutEmbeddingDataUrls', () => {
  const result = E.export('t', [u('u', '看这张图', ['data:image/png;base64,AAAA', 'data:image/jpeg;base64,BBBB'])], labels);
  assert.ok(result.includes('_Images: 2_')); assert.ok(!result.includes('base64'));
});
test('exportSkipsBlankStreamingAndNonContentMessages', () => {
  assert.equal(E.export('t', [a('a', '', true), { kind: 'trace', id: 'r', capabilities: [] }, { kind: 'suggestions', id: 's', prompts: ['试试这个'] }], labels), '# t\n');
});
test('exportRendersToolStatusVariants', () => {
  const result = E.export('t', [tool('t1', 'observe_screen', 'Failed'), tool('t2', 'tap_element', 'Unknown', '确定')], labels);
  assert.ok(result.includes('> Tool: observe_screen (Failed)')); assert.ok(result.includes('> Tool: tap_element (Result unknown)')); assert.ok(result.includes('> Arguments: 确定'));
});
test('exportSanitizesMultilineTitle', () => { assert.ok(E.export('第一行\n第二行', [u('u', 'hi')], labels).startsWith('# 第一行 第二行\n')); });
test('defaultFileNameSanitizesTitle', () => {
  const name = E.defaultFileName('a/b\\c:d*e?f"g<h>i|j', 'Conversation', 0); assert.ok(name.startsWith('随身助手-abcdefghij-')); assert.ok(name.endsWith('.md')); assert.ok(!name.includes('/'));
});
test('defaultFileNameFallsBackWhenBlankAndCollapsesWhitespace', () => {
  assert.ok(E.defaultFileName('   ', 'Conversation', 0).startsWith('随身助手-Conversation-')); assert.ok(E.defaultFileName('hello   world\nagain', 'Conversation', 0).startsWith('随身助手-hello world again-'));
});
test('defaultFileNameTruncatesLongTitles', () => {
  const match = /^随身助手-(.+)-\d{8}-\d{4}\.md$/.exec(E.defaultFileName('长'.repeat(100), 'Conversation', 0)); assert.ok(match); assert.equal(match[1].length, 40);
});
