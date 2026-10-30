// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/markdown/TexMathDelimiters.kt @ 3b1794d
// Copyright © 2026 蛮吉 (Mangi-11). PolyForm Noncommercial 1.0.0.
import { fenceMarker, runLength } from "./sourceLines";
function blankLineAfter(source: string, newline: number): boolean {
  const end = source.indexOf("\n", newline + 1);
  return source.slice(newline + 1, end < 0 ? source.length : end).trim() === "";
}
function findClose(
  source: string,
  from: number,
  closer: string,
): number | null {
  let index = from;
  while (index < source.length - 1) {
    if (source[index] === "\n" && blankLineAfter(source, index)) return null;
    if (source[index] === "\\" && source[index + 1] === closer)
      return source.slice(from, index).trim() ? index : null;
    index += source[index] === "\\" ? 2 : 1;
  }
  return null;
}
function codeSpanEnd(source: string, from: number, run: number): number | null {
  let index = from;
  while (index < source.length) {
    if (source[index] === "\n" && blankLineAfter(source, index)) return null;
    if (source[index] === "`") {
      const length = runLength(source, index, "`");
      if (length === run) return index + length;
      index += length;
    } else index++;
  }
  return null;
}
export function normalizeTexMath(source: string): string {
  if (!source.includes("\\(") && !source.includes("\\[")) return source;
  const output = source.split("");
  let fence: string | null = null;
  let lineStart = 0;
  let index = 0;
  while (index < source.length) {
    const char = source[index];
    if (char === "\n") {
      index++;
      lineStart = index;
      continue;
    }
    if (index === lineStart) {
      const newline = source.indexOf("\n", index);
      const end = newline < 0 ? source.length : newline;
      const line = source.slice(index, end);
      const parsed = fenceMarker(line);
      const marker = parsed ? parsed.marker.repeat(parsed.length) : null;
      if (fence === null && marker !== null) fence = marker;
      else if (
        fence !== null &&
        marker !== null &&
        marker[0] === fence[0] &&
        marker.length >= fence.length &&
        line.trim() === marker
      ) {
        fence = null;
        index = end;
        continue;
      }
      if (fence !== null) {
        index = end;
        continue;
      }
    }
    if (char === "`") {
      const run = runLength(source, index, "`");
      index = codeSpanEnd(source, index + run, run) ?? index + run;
    } else if (
      char === "\\" &&
      (source[index + 1] === "(" ||
        (source[index + 1] === "[" && !source.slice(lineStart, index).trim()))
    ) {
      const bracket = source[index + 1] === "[";
      let close = findClose(source, index + 2, bracket ? "]" : ")");
      if (close !== null && bracket) {
        const end = source.indexOf("\n", close + 2);
        if (source.slice(close + 2, end < 0 ? source.length : end).trim())
          close = null;
      }
      if (close === null) index += 2;
      else {
        output[index] = "$";
        output[index + 1] = "$";
        output[close] = "$";
        output[close + 1] = "$";
        index = close + 2;
      }
    } else index += char === "\\" ? 2 : 1;
  }
  return output.join("");
}
