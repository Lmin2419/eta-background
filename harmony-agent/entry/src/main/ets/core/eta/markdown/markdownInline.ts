// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/markdown/MarkdownInline.kt @ 3b1794d
// Copyright © 2026 蛮吉 (Mangi-11). PolyForm Noncommercial 1.0.0.
import { InlineSpan, MarkdownText } from "./markdownModel";
import { runLength } from "./sourceLines";
export function safeLinkUrl(url: string): string | null {
  const trimmed = url.trim();
  const colon = trimmed.indexOf(":");
  return colon >= 0 &&
    ["http", "https", "mailto"].includes(trimmed.slice(0, colon).toLowerCase())
    ? trimmed
    : null;
}
const entities = new Map<string, string>([
  ["amp", "&"],
  ["lt", "<"],
  ["gt", ">"],
  ["quot", '"'],
  ["apos", "'"],
  ["nbsp", "\u00a0"],
  ["copy", "©"],
  ["reg", "®"],
  ["trade", "™"],
  ["hellip", "…"],
  ["mdash", "—"],
  ["ndash", "–"],
  ["times", "×"],
  ["middot", "·"],
]);
export function decodeMarkdownText(text: string): string {
  return text.replace(
    /\\([!"#$%&'()*+,\-./:;<=>?@[\]\\^_`{|}~])|&([^;]{1,9});/g,
    (raw: string, escaped: string | undefined, entity: string | undefined) => {
      if (escaped !== undefined) return escaped;
      if (entity === undefined) return raw;
      if (!entity.startsWith("#")) return entities.get(entity) ?? raw;
      const hex = /^#x/i.test(entity);
      const number = entity.slice(hex ? 2 : 1);
      if (!(hex ? /^[0-9a-f]+$/i : /^\d+$/).test(number)) return raw;
      const point = parseInt(number, hex ? 16 : 10);
      return point > 0 && point <= 0x10ffff ? String.fromCodePoint(point) : raw;
    },
  );
}
export function normalizeLinkLabel(label: string): string {
  return label.trim().replace(/\s+/g, " ").toLowerCase();
}
function closeBracket(
  source: string,
  start: number,
  opener: string,
  closer: string,
): number {
  let depth = 1;
  for (let index = start + 1; index < source.length; index++) {
    if (source[index] === "\\") index++;
    else if (source[index] === opener) depth++;
    else if (source[index] === closer && --depth === 0) return index;
  }
  return -1;
}
/** Rich text uses UTF-16 ranges, matching Compose AnnotatedString and ArkUI spans. */
export function inlineText(
  source: string,
  links: Map<string, string> | null = null,
): MarkdownText {
  let text = "";
  const spans: InlineSpan[] = [];
  const appendStyled = (
    content: MarkdownText,
    kind?: InlineSpan["kind"],
    url?: string,
  ): void => {
    const start = text.length;
    text += content.text;
    for (const span of content.spans)
      spans.push({ ...span, start: start + span.start, end: start + span.end });
    if (kind && content.text.length)
      spans.push({ start, end: text.length, kind, ...(url ? { url } : {}) });
  };
  const code = (content: string, padding = "\u2009"): void =>
    appendStyled({ text: padding + content + padding, spans: [] }, "code");
  let index = 0;
  while (index < source.length) {
    const rest = source.slice(index);
    const char = source[index];
    if (char === "\\" && index + 1 < source.length) {
      if (source[index + 1] === "\n") {
        text += "\n";
        index += 2;
        continue;
      }
      const decoded = decodeMarkdownText(source.slice(index, index + 2));
      if (decoded.length === 1) {
        text += decoded;
        index += 2;
        continue;
      }
    }
    if (char === "`") {
      const length = runLength(source, index, "`");
      let cursor = index + length;
      let end = -1;
      while (cursor < source.length) {
        if (source[cursor] !== "`") {
          cursor++;
          continue;
        }
        const run = runLength(source, cursor, "`");
        if (run === length) {
          end = cursor;
          break;
        }
        cursor += run;
      }
      if (end >= 0) {
        let content = source.slice(index + length, end).replace(/\n/g, " ");
        if (content.startsWith(" ") && content.endsWith(" ") && content.trim())
          content = content.slice(1, -1);
        code(content);
        index = end + length;
        continue;
      }
      text += "`".repeat(length);
      index += length;
      continue;
    }
    if (rest.startsWith("$$")) {
      const end = source.indexOf("$$", index + 2);
      if (end >= 0 && source.slice(index + 2, end).trim()) {
        code(source.slice(index + 2, end).trim());
        index = end + 2;
        continue;
      }
    }
    const kbd = /^<kbd\s*>/i.exec(rest);
    if (kbd) {
      const close = /<\/kbd\s*>/i.exec(rest.slice(kbd[0].length));
      if (close && close.index > 0) {
        const content = inlineText(
          rest.slice(kbd[0].length, kbd[0].length + close.index),
          links,
        );
        appendStyled(
          {
            text: " " + content.text + " ",
            spans: content.spans.map((span) => ({
              ...span,
              start: span.start + 1,
              end: span.end + 1,
            })),
          },
          "code",
        );
        index += kbd[0].length + close.index + close[0].length;
        continue;
      }
    }
    const br = /^<br\s*\/?>/i.exec(rest);
    if (br) {
      text += "\n";
      index += br[0].length;
      continue;
    }
    const image = rest.startsWith("![");
    const start = image ? index + 1 : index;
    if (source[start] === "[") {
      const end = closeBracket(source, start, "[", "]");
      if (end >= 0) {
        const label = source.slice(start + 1, end);
        let destination: string | null = null;
        let consumed = end + 1;
        if (source[end + 1] === "(") {
          const finish = closeBracket(source, end + 1, "(", ")");
          if (finish >= 0) {
            const raw = source.slice(end + 2, finish).trim();
            const match = /^(?:<([^>]*)>|(\S*?))(?:\s+["'].*["'])?$/.exec(raw);
            destination = decodeMarkdownText(
              match ? (match[1] ?? match[2]) : raw,
            );
            consumed = finish + 1;
          }
        } else {
          let reference = label;
          if (source[end + 1] === "[") {
            const finish = closeBracket(source, end + 1, "[", "]");
            if (finish >= 0) {
              reference = source.slice(end + 2, finish) || label;
              consumed = finish + 1;
            }
          }
          destination = links?.get(normalizeLinkLabel(reference)) ?? null;
        }
        if (destination !== null) {
          const safe = safeLinkUrl(destination);
          const content = image
            ? { text: label || destination, spans: [] }
            : inlineText(label, links);
          appendStyled(content, safe ? "link" : undefined, safe ?? undefined);
          index = consumed;
          continue;
        }
        // Undefined reference links remain literal, including their brackets.
        if (consumed > end + 1 || links) {
          text += decodeMarkdownText(source.slice(index, consumed));
          index = consumed;
          continue;
        }
      }
    }
    const auto =
      /^(?:<(https?:\/\/[^>]+|mailto:[^>]+)>|(https?:\/\/[^\s<>]+|www\.[^\s<>]+))/.exec(
        rest,
      );
    if (auto) {
      const raw = (auto[1] ?? auto[2]).replace(/[.,!?;]+$/, "");
      const url = safeLinkUrl(raw.startsWith("www.") ? "https://" + raw : raw);
      appendStyled(
        { text: raw, spans: [] },
        url ? "link" : undefined,
        url ?? undefined,
      );
      index += auto[1] ? auto[0].length : raw.length;
      continue;
    }
    const delimiter = ["**", "__", "~~", "*", "_"].find((value) =>
      rest.startsWith(value),
    );
    if (
      delimiter &&
      source[index + delimiter.length] !== undefined &&
      !/\s/.test(source[index + delimiter.length]) &&
      !(
        delimiter.includes("_") &&
        /[\p{L}\p{N}]/u.test(source[index - 1] ?? "") &&
        /[\p{L}\p{N}]/u.test(source[index + delimiter.length])
      )
    ) {
      let cursor = index + delimiter.length;
      let end = -1;
      while (cursor < source.length) {
        if (source[cursor] === "\\") {
          cursor += 2;
          continue;
        }
        if (source[cursor] === "`") {
          const run = runLength(source, cursor, "`");
          const close = source.indexOf("`".repeat(run), cursor + run);
          cursor = close < 0 ? cursor + run : close + run;
          continue;
        }
        if (
          source.startsWith(delimiter, cursor) &&
          !/\s/.test(source[cursor - 1])
        ) {
          end = cursor;
          break;
        }
        cursor++;
      }
      if (end >= 0) {
        appendStyled(
          inlineText(source.slice(index + delimiter.length, end), links),
          delimiter === "~~"
            ? "strike"
            : delimiter.length === 2
              ? "bold"
              : "italic",
        );
        index = end + delimiter.length;
        continue;
      }
    }
    if (char === "\n") {
      text = text.replace(/[ \t]+$/, "") + "\n";
      index++;
      while (/[ \t]/.test(source[index] ?? "") && index < source.length)
        index++;
      continue;
    }
    if (char === "&") {
      const entity = /^&[^;\s]{1,9};/.exec(rest);
      if (entity) {
        text += decodeMarkdownText(entity[0]);
        index += entity[0].length;
        continue;
      }
    }
    if (char === " " || char === "\t") {
      if (text && !text.endsWith("\n") && !text.endsWith(" ")) text += " ";
    } else text += char;
    index++;
  }
  text = text.trimEnd();
  return {
    text,
    spans: spans
      .filter((span) => span.start < text.length)
      .map((span) => ({ ...span, end: Math.min(span.end, text.length) })),
  };
}
