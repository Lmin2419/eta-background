// Ported from app/src/main/kotlin/io/github/mangi/eta/agent/model/CitationFormatter.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
export interface CitationAnnotation { start: number | null; end: number | null; url: string; title: string; }
export function formatCitations(text: string, annotations: CitationAnnotation[]): string {
  const seen = new Set<string>(), unique = annotations.filter(a => { if (!/^https?:\/\//.test(a.url) || seen.has(a.url)) return false; seen.add(a.url); return true; });
  const valid = unique.map((c, index) => ({ c, index })).filter(({ c }) => c.end !== null && c.end >= 0 && c.end <= text.length && c.start !== null && c.start >= 0 && c.start <= c.end);
  let result = text; const url = (u: string): string => u.replace(/>/g, '%3E');
  for (const { c, index } of [...valid].sort((a, b) => b.c.end! - a.c.end!)) result = result.slice(0, c.end!) + ` [[${index + 1}]](<${url(c.url)}>)` + result.slice(c.end!);
  const invalid = unique.filter(c => !valid.some(v => v.c === c));
  if (invalid.length) { result += '\n\n来源：'; invalid.forEach((c, i) => { result += `\n- [${unique.indexOf(c) + 1}] [${c.title.trim() ? c.title : '来源 ' + (i + 1)}](<${url(c.url)}>)`; }); }
  return result;
}
