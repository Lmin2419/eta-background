// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/components/ConversationPaneMotion.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
export interface ConversationPaneMotion { progress: number; foregroundOffset: number; panelOffset: number; }
export function conversationPaneMotion(offset: number, paneWidth: number): ConversationPaneMotion {
  const width = Number.isFinite(paneWidth) && paneWidth > 0 ? paneWidth : 0;
  const displacement = Number.isFinite(offset) ? Math.min(width, Math.max(0, offset)) : 0;
  return { progress: width > 0 ? displacement / width : 0, foregroundOffset: displacement, panelOffset: displacement - width };
}
