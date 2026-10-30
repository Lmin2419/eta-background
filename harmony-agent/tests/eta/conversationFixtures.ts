// Test fixtures derived from Eta ui/app/*Test.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { AgentChatUiState, emptyChatState, userMessage, agentMessage, thinkingMessage, toolActivityMessage, conversationMessage } from '../../entry/src/main/ets/core/eta/model/conversation';
export function state(patch: Partial<AgentChatUiState> = {}): AgentChatUiState { return { ...emptyChatState(), ...patch }; }
export function conversationState(): AgentChatUiState {
  return state({ input: '草稿', messages: [userMessage('user-1', '第一问'), thinkingMessage('thinking-1', '思考'), toolActivityMessage('tool-1', 'test', 'Success', '{}'), agentMessage('assistant-1', '第一答'), userMessage('user-2', '第二问', ['data:image/png;base64,AA==']), agentMessage('assistant-2', '第二答'), userMessage('user-3', '第三问'), agentMessage('assistant-3', '第三答')],
    history: [conversationMessage('user', '第一问'), { ...conversationMessage('assistant'), toolCallsJson: '[]' }, conversationMessage('tool', '结果'), conversationMessage('assistant', '第一答'), conversationMessage('user', '第二问'), conversationMessage('assistant', '第二答'), conversationMessage('user', '第三问'), conversationMessage('assistant', '第三答')] });
}
