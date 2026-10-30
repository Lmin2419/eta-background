// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/markdown/MarkdownDocumentBuilder.kt @ 3b1794d
// Copyright © 2026 蛮吉 (Mangi-11). PolyForm Noncommercial 1.0.0.
// Pure TS block scanning replaces the JVM-only intellij-markdown AST adapter.
import {
  defaultInlineStyle,
  firstRevealKey,
  MarkdownAlert,
  MarkdownBlock,
  MarkdownDocument,
  MarkdownInlineStyle,
  MarkdownListItem,
  MarkdownTableCell,
  plainText,
  revealKeys,
} from "./markdownModel";
import { inlineText, normalizeLinkLabel } from "./markdownInline";
import {
  containsUnescapedPipe,
  fenceMarker,
  SourceLine,
  sourceLines,
  validTableDelimiter,
} from "./sourceLines";
interface CachedBlock {
  source: string;
  block: MarkdownBlock;
}
function definition(line: string): RegExpExecArray | null {
  return /^ {0,3}\[([^\]]+)\]:\s*(?:<([^>]+)>|(\S+))(?:\s+.*)?$/.exec(line);
}
function listMarker(line: string): RegExpExecArray | null {
  return /^( {0,3})([-+*]|\d{1,9}[.)])(?:[ \t]+(.*)|$)/.exec(line);
}
function startsBlock(line: string): boolean {
  return (
    !!fenceMarker(line) ||
    /^ {0,3}(?:#{1,6}(?:\s|$)|>|(?:[-*_]\s*){3,}$)/.test(line) ||
    !!listMarker(line) ||
    !!definition(line)
  );
}
function tableCells(
  line: SourceLine,
): Array<{ offset: number; source: string }> {
  let start = 0;
  let escaped = false;
  let ticks = 0;
  const result: Array<{ offset: number; source: string }> = [];
  const leading = line.text.search(/\S/);
  if (line.text[leading] === "|") start = leading + 1;
  let index = start;
  for (; index < line.text.length; index++) {
    const char = line.text[index];
    if (escaped) {
      escaped = false;
      continue;
    }
    if (char === "\\") {
      escaped = true;
      continue;
    }
    if (char === "`") ticks = ticks ? 0 : 1;
    if (char === "|" && !ticks) {
      result.push({
        offset: line.start + start,
        source: line.text.slice(start, index),
      });
      start = index + 1;
    }
  }
  if (start < line.text.length && line.text.slice(start).trim())
    result.push({ offset: line.start + start, source: line.text.slice(start) });
  return result;
}
function parseBlocks(
  lines: SourceLine[],
  links: Map<string, string> | null,
): MarkdownBlock[] {
  const blocks: MarkdownBlock[] = [];
  let index = 0;
  while (index < lines.length) {
    const line = lines[index];
    const raw = line.text;
    const offset = line.start;
    if (!raw.trim() || definition(raw)) {
      index++;
      continue;
    }
    const fence = fenceMarker(raw);
    if (fence) {
      const info = raw.trimStart().slice(fence.length).trim().split(/\s/)[0];
      const body: string[] = [];
      let closed = false;
      index++;
      while (index < lines.length) {
        const close = fenceMarker(lines[index].text);
        if (
          close &&
          close.marker === fence.marker &&
          close.length >= fence.length &&
          close.isClosing
        ) {
          closed = true;
          index++;
          break;
        }
        body.push(
          lines[index++].text.replace(
            new RegExp("^ {0," + (raw.length - raw.trimStart().length) + "}"),
            "",
          ),
        );
      }
      blocks.push({
        kind: "code",
        offset,
        language: info || null,
        text: plainText(
          closed ? body.join("\n") : body.join("\n").replace(/\n$/, ""),
        ),
        isMath: false,
      });
      continue;
    }
    if (/^(?: {4}|\t)/.test(raw)) {
      const body: string[] = [];
      while (
        index < lines.length &&
        (/^(?: {4}|\t)/.test(lines[index].text) || !lines[index].text.trim())
      )
        body.push(lines[index++].text.replace(/^(?: {1,4}|\t)/, ""));
      blocks.push({
        kind: "code",
        offset,
        language: null,
        text: plainText(body.join("\n").replace(/\n+$/, "")),
        isMath: false,
      });
      continue;
    }
    if (/^ {0,3}(?:(?:\*\s*){3,}|(?:-\s*){3,}|(?:_\s*){3,})$/.test(raw)) {
      blocks.push({ kind: "rule", offset });
      index++;
      continue;
    }
    const heading = /^ {0,3}(#{1,6})(?:\s+(.*)|$)/.exec(raw);
    if (heading) {
      blocks.push({
        kind: "heading",
        offset,
        level: heading[1].length,
        text: inlineText((heading[2] ?? "").replace(/\s+#+\s*$/, ""), links),
      });
      index++;
      continue;
    }
    if (/^ {0,3}>/.test(raw)) {
      const inner: SourceLine[] = [];
      while (index < lines.length && /^ {0,3}>/.test(lines[index].text)) {
        const item = lines[index++];
        const prefix = /^ {0,3}> ?/.exec(item.text)![0];
        inner.push({
          start: item.start + prefix.length,
          text: item.text.slice(prefix.length),
        });
      }
      const alert = /^\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)\]\s*$/i.exec(
        inner[0].text,
      );
      if (alert) {
        const value = alert[1].toLowerCase();
        blocks.push({
          kind: "alert",
          offset,
          alertKind: (value[0].toUpperCase() +
            value.slice(1)) as MarkdownAlert["alertKind"],
          blocks: parseBlocks(inner.slice(1), links),
        });
      } else
        blocks.push({
          kind: "quote",
          offset,
          blocks: parseBlocks(inner, links),
        });
      continue;
    }
    const marker = listMarker(raw);
    if (marker) {
      const ordered = /^\d/.test(marker[2]);
      const startNumber = ordered ? parseInt(marker[2], 10) : null;
      const items: MarkdownListItem[] = [];
      let loose = false;
      while (index < lines.length) {
        const first = lines[index];
        const itemMarker = listMarker(first.text);
        if (!itemMarker || /^\d/.test(itemMarker[2]) !== ordered) break;
        const prefixLength = first.text.length - (itemMarker[3] ?? "").length;
        let content = itemMarker[3] ?? "";
        let contentOffset = first.start + prefixLength;
        let checked: boolean | null = null;
        const task = /^\[([ xX])\](?:\s+|$)/.exec(content);
        if (task) {
          checked = task[1].toLowerCase() === "x";
          content = content.slice(task[0].length);
          contentOffset += task[0].length;
        }
        const itemLines: SourceLine[] = [
          { start: contentOffset, text: content },
        ];
        index++;
        while (index < lines.length) {
          const next = lines[index];
          if (!next.text.trim()) {
            let cursor = index;
            while (cursor < lines.length && !lines[cursor].text.trim())
              cursor++;
            if (
              cursor >= lines.length ||
              (!listMarker(lines[cursor].text) &&
                !/^\s/.test(lines[cursor].text))
            )
              break;
            loose = true;
            itemLines.push(next);
            index++;
            continue;
          }
          const indent = next.text.length - next.text.trimStart().length;
          if (indent >= prefixLength) {
            itemLines.push({
              start: next.start + prefixLength,
              text: next.text.slice(prefixLength),
            });
            index++;
          } else if (!startsBlock(next.text) && indent > 0) {
            itemLines.push({
              start: next.start + indent,
              text: next.text.slice(indent),
            });
            index++;
          } else break;
        }
        const children = parseBlocks(itemLines, links);
        items.push({
          offset: first.start,
          number: startNumber === null ? null : startNumber + items.length,
          checked,
          blocks: children,
          markerRevealKey: firstRevealKey(children),
        });
      }
      blocks.push({ kind: "list", offset, ordered, loose, items });
      continue;
    }
    if (
      index + 1 < lines.length &&
      containsUnescapedPipe(raw) &&
      validTableDelimiter(lines[index + 1].text)
    ) {
      const header = tableCells(line).map((cell) => ({
        kind: "cell" as const,
        offset: cell.offset,
        text: inlineText(cell.source.trim(), links),
      }));
      const specs = tableCells(lines[index + 1]);
      const alignments = header.map((_, column) => {
        const spec = specs[column]?.source.trim() ?? "";
        return spec.startsWith(":") && spec.endsWith(":")
          ? ("Center" as const)
          : spec.endsWith(":")
            ? ("End" as const)
            : ("Start" as const);
      });
      const rows: MarkdownTableCell[][] = [];
      index += 2;
      while (
        index < lines.length &&
        lines[index].text.trim() &&
        containsUnescapedPipe(lines[index].text)
      ) {
        const row = lines[index++];
        const cells = tableCells(row);
        rows.push(
          header.map((_, column) => ({
            kind: "cell",
            offset: cells[column]?.offset ?? row.start + row.text.length,
            text: cells[column]
              ? inlineText(cells[column].source.trim(), links)
              : plainText(""),
          })),
        );
      }
      blocks.push({ kind: "table", offset, alignments, header, rows });
      continue;
    }
    const paragraph = [line];
    index++;
    while (
      index < lines.length &&
      lines[index].text.trim() &&
      !startsBlock(lines[index].text)
    ) {
      if (/^ {0,3}(?:=+|-+)\s*$/.test(lines[index].text)) break;
      if (
        index + 1 < lines.length &&
        containsUnescapedPipe(lines[index].text) &&
        validTableDelimiter(lines[index + 1].text)
      )
        break;
      paragraph.push(lines[index++]);
    }
    const joined = paragraph.map((value) => value.text).join("\n");
    if (
      index < lines.length &&
      /^ {0,3}(?:=+|-+)\s*$/.test(lines[index].text)
    ) {
      blocks.push({
        kind: "heading",
        offset,
        level: lines[index++].text.trim()[0] === "=" ? 1 : 2,
        text: inlineText(joined, links),
      });
    } else if (
      /^\s*\$\$[\s\S]+\$\$\s*$/.test(joined) &&
      joined.trim().slice(2, -2).indexOf("$$") < 0
    ) {
      blocks.push({
        kind: "code",
        offset,
        language: "LaTeX",
        text: plainText(joined.trim().slice(2, -2).trim()),
        isMath: true,
      });
    } else {
      const text = inlineText(joined, links);
      if (text.text) blocks.push({ kind: "paragraph", offset, text });
    }
  }
  return blocks;
}
export class MarkdownDocumentBuilder {
  private cache = new Map<number, CachedBlock>();
  private cacheStyle = "";
  private cacheLinks: Map<string, string> | null = null;
  build(
    source: string,
    complete: boolean,
    style: MarkdownInlineStyle = defaultInlineStyle,
  ): MarkdownDocument {
    const lines = sourceLines(source);
    const links = complete ? new Map<string, string>() : null;
    if (links)
      for (const line of lines) {
        const value = definition(line.text);
        if (value && !links.has(normalizeLinkLabel(value[1])))
          links.set(normalizeLinkLabel(value[1]), value[2] ?? value[3]);
      }
    const signature = JSON.stringify(style);
    if (signature !== this.cacheStyle || links !== this.cacheLinks)
      this.cache.clear();
    this.cacheStyle = signature;
    this.cacheLinks = links;
    const parsed = parseBlocks(lines, links);
    const next = new Map<number, CachedBlock>();
    const blocks = parsed.map((block, index) => {
      const slice = source
        .slice(block.offset, parsed[index + 1]?.offset ?? source.length)
        .trimEnd();
      const old = this.cache.get(block.offset);
      const result = old?.source === slice ? old.block : block;
      next.set(block.offset, { source: slice, block: result });
      return result;
    });
    this.cache = next;
    return { blocks, revealKeys: revealKeys(blocks), inlineStyle: style };
  }
}
