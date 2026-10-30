// Ported from app/src/main/kotlin/io/github/mangi/eta/agent/runtime/SearchSuggestionsPayload.kt @ 3b1794d
// Copyright © 2026 蛮吉 (Mangi-11). PolyForm Noncommercial 1.0.0.
function utf8Size(text: string): number {
  let size = 0;
  for (const char of text) {
    const point = char.codePointAt(0)!;
    size += point <= 0x7f ? 1 : point <= 0x7ff ? 2 : point <= 0xffff ? 3 : 4;
  }
  return size;
}
export class SearchSuggestionsBudget {
  private rounds = new Map<
    number,
    Array<{ index: number; html: string; bytes: number }>
  >();
  accept(round: number, index: number, html: string): boolean {
    if (round < 0 || index < 0 || !html.trim() || html.length > 64 * 1024)
      return false;
    const bytes = utf8Size(html);
    if (bytes > 64 * 1024) return false;
    const fragments = this.rounds.get(round) ?? [];
    if (
      fragments.some((item) => item.index === index && item.html === html) ||
      fragments.length >= 5 ||
      bytes + fragments.reduce((sum, item) => sum + item.bytes, 0) > 128 * 1024
    )
      return false;
    fragments.push({ index, html, bytes });
    this.rounds.set(round, fragments);
    return true;
  }
  clear(): void {
    this.rounds.clear();
  }
}
