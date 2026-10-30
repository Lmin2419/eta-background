// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/components/ChatConversationKey.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
export function chatConversationCompositionKey(conversationId: string | null): string { return conversationId === null ? 'conversation:draft' : 'conversation:' + conversationId; }
