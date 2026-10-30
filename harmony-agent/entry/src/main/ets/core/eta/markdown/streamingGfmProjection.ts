// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/markdown/StreamingGfmParser.kt @ 3b1794d
// Copyright © 2026 蛮吉 (Mangi-11). PolyForm Noncommercial 1.0.0.
import {
  containsUnescapedPipe,
  fenceMarker,
  Fence,
  runLength,
  sourceLines,
  validTableDelimiter,
} from "./sourceLines";
function ambiguousTableStart(source: string): number | null {
  const lines = sourceLines(source);
  let blockStart = 0;
  for (let index = 0; index < lines.length; index++)
    if (!lines[index].text.trim()) blockStart = index + 1;
  const block = lines.slice(blockStart);
  if (!block.length) return null;
  if (
    block.some(
      (line, index) =>
        index > 0 &&
        containsUnescapedPipe(block[index - 1].text) &&
        validTableDelimiter(line.text),
    )
  )
    return null;
  const current = block[block.length - 1];
  const previous = block[block.length - 2];
  if (previous && containsUnescapedPipe(previous.text)) {
    if (!current.text) return source.endsWith("\n\n") ? null : previous.start;
    if (/^[|:\-\s]+$/.test(current.text))
      return validTableDelimiter(current.text) ? null : previous.start;
  }
  return current.text.trim() && containsUnescapedPipe(current.text)
    ? current.start
    : null;
}
function findOpenFence(source: string): Fence | null {
  let open: Fence | null = null;
  for (const line of sourceLines(source)) {
    const marker = fenceMarker(line.text);
    if (!marker) continue;
    if (!open) open = marker;
    else if (
      marker.marker === open.marker &&
      marker.length >= open.length &&
      marker.isClosing
    )
      open = null;
  }
  return open;
}
function pendingLinkStart(source: string): number | null {
  const brackets: number[] = [];
  let ticks = 0;
  let open: number | null = null;
  let depth = 0;
  let lastStart: number | null = null;
  let lastEnd = -1;
  let index = 0;
  while (index < source.length) {
    const char = source[index];
    if (char === "\\") {
      index += 2;
      continue;
    }
    if (char === "`") {
      const run = runLength(source, index, "`");
      ticks = ticks === 0 ? run : ticks === run ? 0 : ticks;
      index += run;
      continue;
    }
    if (ticks) {
      index++;
      continue;
    }
    if (open !== null) {
      if (char === "(") depth++;
      else if (char === ")" && --depth === 0) open = null;
      index++;
      continue;
    }
    if (char === "[") brackets.push(index);
    else if (char === "]" && brackets.length) {
      lastStart = brackets.pop()!;
      lastEnd = index;
    } else if (char === "(" && lastEnd === index - 1) {
      open = lastStart;
      depth = 1;
    }
    index++;
  }
  const pending = open ?? brackets[brackets.length - 1] ?? null;
  return pending !== null && pending > 0 && source[pending - 1] === "!"
    ? pending - 1
    : pending;
}
function inlineClosures(source: string): string {
  let ticks = 0;
  const stack: string[] = [];
  let index = 0;
  while (index < source.length) {
    if (source[index] === "\\") {
      index += 2;
      continue;
    }
    if (source[index] === "`") {
      const run = runLength(source, index, "`");
      ticks = ticks === 0 ? run : ticks === run ? 0 : ticks;
      index += run;
      continue;
    }
    if (ticks) {
      index++;
      continue;
    }
    const delimiter = ["**", "__", "~~", "*", "_"].find((value) =>
      source.startsWith(value, index),
    );
    if (!delimiter) {
      index++;
      continue;
    }
    const previous = source[index - 1];
    const next = source[index + delimiter.length];
    const canOpen = next !== undefined && !/\s/.test(next);
    const canClose = previous !== undefined && !/\s/.test(previous);
    const intraword =
      delimiter.includes("_") &&
      previous !== undefined &&
      next !== undefined &&
      /[\p{L}\p{N}]/u.test(previous) &&
      /[\p{L}\p{N}]/u.test(next);
    if (canClose && stack[stack.length - 1] === delimiter) stack.pop();
    else if (canOpen && !intraword) stack.push(delimiter);
    index += delimiter.length;
  }
  return "`".repeat(ticks) + stack.reverse().join("");
}
export function projectStreamingGfm(
  source: string,
  isComplete: boolean,
): string {
  if (isComplete || !source) return source;
  let projected = source.slice(0, ambiguousTableStart(source) ?? source.length);
  const fence = findOpenFence(projected);
  if (fence)
    return (
      projected +
      (projected.endsWith("\n") ? "" : "\n") +
      fence.marker.repeat(fence.length)
    );
  const pending = pendingLinkStart(projected);
  if (pending !== null) projected = projected.slice(0, pending);
  return projected + inlineClosures(projected);
}
