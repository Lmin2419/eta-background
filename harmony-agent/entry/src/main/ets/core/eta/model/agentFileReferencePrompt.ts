// Ported from app/src/main/kotlin/io/github/mangi/eta/agent/model/AgentFileReference.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
// Only prompt format/parse used by the exporter; no filesystem access or tool enablement.
import { AgentFileReference } from './conversation';
export interface AgentFileReferencePrompt { request: string; references: AgentFileReference[]; }
const FILES_HEADER = '# Files mentioned by the user:';
const REQUEST_HEADER = '## My request:';
function unique(references: AgentFileReference[]): AgentFileReference[] { return references.filter((x, i) => references.findIndex(y => y.absolutePath === x.absolutePath) === i); }
export class AgentFileReferencePromptCodec {
  static format(request: string, references: AgentFileReference[]): string {
    const refs = unique(references);
    if (refs.length === 0) return request;
    return FILES_HEADER + '\n\n' + refs.map(x => `## ${x.displayName}${x.kind === 'Directory' ? '/' : ''}: ${x.absolutePath}\n`).join('\n') + '\n' + REQUEST_HEADER + (request.length > 0 ? '\n' + request : '');
  }
  static parse(content: string): AgentFileReferencePrompt {
    const original: AgentFileReferencePrompt = { request: content, references: [] };
    const prefix = FILES_HEADER + '\n\n', delimiter = '\n\n' + REQUEST_HEADER;
    if (!content.startsWith(prefix)) return original;
    const index = content.indexOf(delimiter, prefix.length);
    if (index < 0) return original;
    const references: AgentFileReference[] = [];
    for (const line of content.slice(prefix.length, index).split('\n\n')) {
      if (!line.startsWith('## ') || line.includes('\n')) return original;
      const body = line.slice(3), split = body.lastIndexOf(': /');
      if (split <= 0) return original;
      const label = body.slice(0, split), absolutePath = body.slice(split + 2);
      const directory = label.endsWith('/'), displayName = directory ? label.slice(0, -1) : label;
      if (!displayName.trim() || /[\u0000-\u001f\u007f-\u009f]/.test(displayName + absolutePath) || !absolutePath.startsWith('/')) return original;
      references.push({ displayName, absolutePath, kind: directory ? 'Directory' : 'File' });
    }
    const start = index + delimiter.length;
    if (start < content.length && content[start] !== '\n') return original;
    return { request: start === content.length ? '' : content.slice(start + 1), references: unique(references) };
  }
}
