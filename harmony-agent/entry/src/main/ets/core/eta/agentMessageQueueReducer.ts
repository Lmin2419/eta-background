// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/app/AgentMessageQueueReducer.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { AgentChatUiState, QueuedMessageUi } from './model/conversation';
export interface QueueTransition { state: AgentChatUiState; item: QueuedMessageUi | null; }
export class AgentMessageQueueReducer {
  static enqueue(state: AgentChatUiState, item: QueuedMessageUi): AgentChatUiState {
    const queuedMessages = item.immediateInterrupt ? [item, ...state.queuedMessages.map(x => ({ ...x, immediateInterrupt: false }))] : [...state.queuedMessages, item];
    return { ...state, input: '', pendingImages: [], pendingFileReferences: [], messageEdit: null, queuedMessages };
  }
  static promoteToInterrupt(state: AgentChatUiState, id: string): QueueTransition {
    const target = state.queuedMessages.find(x => x.id === id);
    if (!target) return { state, item: null };
    const rest = state.queuedMessages.filter(x => x.id !== id).map(x => ({ ...x, immediateInterrupt: false }));
    if (state.isStreaming && !state.isCompacting) {
      const promoted: QueuedMessageUi = { ...target, immediateInterrupt: true };
      return { state: { ...state, queuedMessages: [promoted, ...rest] }, item: promoted };
    }
    return { state: { ...state, queuedMessages: rest }, item: { ...target, immediateInterrupt: false } };
  }
  static recallToDraft(state: AgentChatUiState, currentInputText: string, id: string): AgentChatUiState | null {
    const target = state.queuedMessages.find(x => x.id === id);
    if (!target) return null;
    const input = currentInputText.trim().length === 0 ? target.content : target.content.trim().length === 0 ? currentInputText : currentInputText.trimEnd() + '\n' + target.content;
    const images = [...state.pendingImages, ...target.pendingImages];
    const files = [...state.pendingFileReferences, ...target.pendingFileReferences];
    return { ...state, input, pendingImages: images.filter((x, i) => images.findIndex(v => v.id === x.id) === i),
      pendingFileReferences: files.filter((x, i) => files.findIndex(v => v.id === x.id) === i),
      queuedMessages: state.queuedMessages.filter(x => x.id !== id), inputSyncVersion: state.inputSyncVersion + 1 };
  }
  static remove(state: AgentChatUiState, id: string): AgentChatUiState {
    return state.queuedMessages.some(x => x.id === id) ? { ...state, queuedMessages: state.queuedMessages.filter(x => x.id !== id) } : state;
  }
  static clearInterruptFlags(state: AgentChatUiState): AgentChatUiState {
    return state.queuedMessages.some(x => x.immediateInterrupt) ? { ...state, queuedMessages: state.queuedMessages.map(x => ({ ...x, immediateInterrupt: false })) } : state;
  }
  static pollNextToSendAfterRun(state: AgentChatUiState, stoppedByUser: boolean): QueueTransition {
    const first = state.queuedMessages[0];
    if (!first) return { state, item: null };
    if (!first.immediateInterrupt && stoppedByUser) return { state: this.clearInterruptFlags(state), item: null };
    return { state: { ...state, queuedMessages: state.queuedMessages.slice(1) }, item: { ...first, immediateInterrupt: false } };
  }
}
