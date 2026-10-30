// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/app/AgentRunEventCoalescer.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { AssistantBlockDelta } from './model/conversation';
export class AgentRunEventCoalescer {
  private pendingByRun = new Map<string, AssistantBlockDelta>();
  append(runId: string, event: AssistantBlockDelta): AssistantBlockDelta | null {
    const pending = this.pendingByRun.get(runId);
    if (pending && pending.round === event.round && pending.kind === event.kind && pending.index === event.index) {
      this.pendingByRun.set(runId, { ...pending, delta: pending.delta + event.delta, deltaChars: pending.deltaChars + event.deltaChars });
      return null;
    }
    this.pendingByRun.set(runId, { ...event });
    return pending ?? null;
  }
  flush(runId: string): AssistantBlockDelta | null {
    const pending = this.pendingByRun.get(runId); this.pendingByRun.delete(runId); return pending ?? null;
  }
}
