// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/app/ConversationMarkdownExporter.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { AgentChatMessageUi, SystemNoticeCode, ToolActivityStatusUi } from './model/conversation';
import { AgentFileReferencePromptCodec } from './model/agentFileReferencePrompt';
export interface ExportLabels {
  user: string; assistant: string; thinking: string; toolLineFormat: string; toolsLineFormat: string;
  argumentsFormat: string; resultFormat: string; imagesFormat: string;
  toolStatusRunning: string; toolStatusSuccess: string; toolStatusFailed: string; toolStatusUnknown: string;
  noticeStopped: string; noticeEmptyResult: string; noticeModelRetry: string; noticeRuntimeFailed: string; noticeInterrupted: string;
  noticeContextCompaction?: string;
}
function format(pattern: string, args: (string | number)[]): string {
  let next = 0;
  return pattern.replace(/%(?:(\d+)\$)?([sd%])/g, (_whole: string, position: string | undefined, kind: string): string => {
    if (kind === '%') return '%';
    const index = position ? Number(position) - 1 : next++;
    if (index >= args.length || index < 0) throw new Error('Missing format argument');
    return String(args[index]);
  });
}
function quote(lines: string[]): string { return lines.join('\n\n').split(/\r\n|\n|\r/).map(x => x.length === 0 ? '>' : '> ' + x).join('\n'); }
function statusLabel(status: ToolActivityStatusUi, l: ExportLabels): string {
  switch (status) { case 'Running': return l.toolStatusRunning; case 'Success': return l.toolStatusSuccess; case 'Failed': return l.toolStatusFailed; case 'Unknown': return l.toolStatusUnknown; }
}
function noticeLabel(code: SystemNoticeCode, l: ExportLabels): string {
  switch (code) { case 'Stopped': return l.noticeStopped; case 'EmptyResult': return l.noticeEmptyResult; case 'ModelRetry': return l.noticeModelRetry; case 'RuntimeFailed': return l.noticeRuntimeFailed; case 'Interrupted': return l.noticeInterrupted; case 'ContextCompaction': return l.noticeContextCompaction ?? '上下文压缩'; }
}
export class ConversationMarkdownExporter {
  static export(title: string, messages: AgentChatMessageUi[], labels: ExportLabels): string {
    let result = '# ' + title.replace(/\n/g, ' ') + '\n';
    for (const message of messages) { const block = this.block(message, labels); if (block !== null) result += '\n' + block + '\n'; }
    return result;
  }
  static defaultFileName(title: string, fallback: string, nowMillis: number = Date.now()): string {
    const clean = title.replace(/[\t\n\v\f\r ]+/g, ' ').replace(/[\\/:*?"<>|\p{C}]/gu, '').trim().slice(0, 40).trim() || fallback;
    const d = new Date(nowMillis), pad = (n: number): string => String(n).padStart(2, '0');
    const stamp = String(d.getFullYear()).padStart(4, '0') + pad(d.getMonth() + 1) + pad(d.getDate()) + '-' + pad(d.getHours()) + pad(d.getMinutes());
    return `随身助手-${clean}-${stamp}.md`;
  }
  private static block(message: AgentChatMessageUi, labels: ExportLabels): string | null {
    switch (message.kind) {
      case 'user': {
        const prompt = AgentFileReferencePromptCodec.parse(message.content);
        let result = '## ' + labels.user;
        if (prompt.request.trim()) result += '\n\n' + prompt.request;
        for (const ref of prompt.references) result += '\n\n- ' + ref.absolutePath;
        if (message.images.length > 0) result += '\n\n_' + format(labels.imagesFormat, [message.images.length]) + '_';
        return result;
      }
      case 'assistant': return message.content.trim() ? `## ${labels.assistant}\n\n${message.content}` : null;
      case 'thinking': return message.content.trim() ? `<details>\n<summary>${labels.thinking}</summary>\n\n${message.content}\n\n</details>` : null;
      case 'tool': {
        const parts = [format(labels.toolLineFormat, [message.toolName, statusLabel(message.status, labels)])];
        if (message.command?.trim()) parts.push(message.command);
        if (message.argumentsSummary.trim()) parts.push(format(labels.argumentsFormat, [message.argumentsSummary]));
        if (message.resultSummary?.trim()) parts.push(format(labels.resultFormat, [message.resultSummary]));
        return quote(parts);
      }
      case 'toolSummary': return quote([format(labels.toolsLineFormat, [message.tools.join(', ')])]);
      case 'notice': return quote(message.detail?.trim() ? [noticeLabel(message.code, labels), message.detail] : [noticeLabel(message.code, labels)]);
      default: return null;
    }
  }
}
