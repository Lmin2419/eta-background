// Ported from app/src/main/kotlin/io/github/mangi/eta/agent/roleplay/CharacterMacros.kt expand @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
// Card decoding/time/localization remain explicit inputs; E08 owns their UI integration.
export function expandCharacterMacros(text: string, values: Record<string, string>): string {
  const maximum = 2 * 1024 * 1024;
  const check = (length: number): void => { if (length > maximum) throw Error('CARD_MACRO_EXPANSION_LIMIT'); };
  const resolve = (source: string, active: Set<string>, depth: number): string => {
    check(source.length); if (depth >= 8) return source;
    const token = /\{\{([^{}]+)\}\}|<(USER|CHAR|BOT)>/gi;
    let output = '', offset = 0, match: RegExpExecArray | null;
    const append = (value: string): void => { check(output.length + value.length); output += value; };
    while ((match = token.exec(source)) !== null) {
      append(source.slice(offset, match.index)); const key = (match[1] || match[2] || '').trim().toLowerCase();
      append(key.startsWith('//') ? '' : active.has(key) || !Object.prototype.hasOwnProperty.call(values, key) ? match[0] : resolve(values[key], new Set([...active, key]), depth + 1));
      offset = match.index + match[0].length;
    }
    append(source.slice(offset)); return output;
  };
  return resolve(text, new Set(), 0);
}
