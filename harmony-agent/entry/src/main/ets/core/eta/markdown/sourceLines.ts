// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/markdown/StreamingGfmParser.kt @ 3b1794d
// Copyright © 2026 蛮吉 (Mangi-11). PolyForm Noncommercial 1.0.0.
export interface SourceLine {
  start: number;
  text: string;
}
export interface Fence {
  marker: string;
  length: number;
  isClosing: boolean;
}
export function sourceLines(source: string): SourceLine[] {
  const result: SourceLine[] = [];
  let start = 0;
  for (let index = 0; index < source.length; index++) {
    if (source[index] !== "\n") continue;
    result.push({
      start,
      text: source.slice(
        start,
        index > start && source[index - 1] === "\r" ? index - 1 : index,
      ),
    });
    start = index + 1;
  }
  result.push({ start, text: source.slice(start) });
  return result;
}
export function runLength(source: string, start: number, char: string): number {
  let end = start;
  while (source[end] === char && end < source.length) end++;
  return end - start;
}
export function fenceMarker(line: string): Fence | null {
  const match = /^( {0,3})(`{3,}|~{3,})([^\n]*)$/.exec(line);
  return match
    ? {
        marker: match[2][0],
        length: match[2].length,
        isClosing: match[3].trim() === "",
      }
    : null;
}
export function containsUnescapedPipe(line: string): boolean {
  let escaped = false;
  for (const char of line) {
    if (escaped) escaped = false;
    else if (char === "\\") escaped = true;
    else if (char === "|") return true;
  }
  return false;
}
export function validTableDelimiter(line: string): boolean {
  return line
    .trim()
    .replace(/^\|/, "")
    .replace(/\|$/, "")
    .split("|")
    .every((cell) => /^:?-{3,}:?$/.test(cell.trim()));
}
