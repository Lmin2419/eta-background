export interface Rect { left: number; top: number; right: number; bottom: number }
export interface UiNode { id: number; text: string; type: string; clickable: boolean; editable: boolean; bounds: Rect; scrollable?:boolean; resourceId?:string }
export interface Observation { id: string; capturedAt: number; width: number; height: number; fingerprint: string; nodes: UiNode[]; truncated: boolean; screenshot?: string; displayId?:number; rotation?:number; geometryEpoch?:number; bundleName?:string }
export interface ToolCall { id: string; type: string; function: { name: string; arguments: string } }
export interface ContentPart { type: string; text?: string; image_url?: { url: string } }
export interface Message {
  _eta_context_summary?:boolean;_eta_compacted_users?:number;_eta_summary_through_user?:number;
  _eta_observation?:boolean;_eta_gemini_run_start?:boolean;providerSuggestions?:string[];
  currentGoal?:boolean; role: string; content: string | ContentPart[] | null; tool_calls?: ToolCall[]; tool_call_id?: string; providerParts?:Record<string,unknown>[]; providerProtocol?:string }
export interface ModelReply { message: Message; finishReason: string; reasoning?:string;usage?:import('./usage').TokenUsage }
export interface ModelClient { complete(messages: Message[], control: RunControl, onText?: (text:string)=>void, onReasoning?: (text:string)=>void): Promise<ModelReply>; cancel(): void }
export interface DeviceTools { execute(name: string, args: Record<string, unknown>, control: RunControl): Promise<ToolResult>; invalidate(): void }
export interface ToolResult { images?:ContentPart[]; ok: boolean; code?: string; message?: string; observation?: Observation; evidence?:import('./actionEvidence').ActionEvidence; data?:Record<string,unknown> }
export interface RunEvent { usage?:import('./usage').TokenUsage; kind: string; text: string; stepId?: string; toolName?: string; detail?:string; targetBundle?:string; targetAbility?:string }
export type EventSink = (event: RunEvent) => void;

export class ChatDevice implements DeviceTools {
  invalidate():void{}
  async execute(_name:string,_args:Record<string,unknown>,_control:RunControl):Promise<ToolResult>{throw new Error('本轮未启用手机工具');}
}

export class RunControl {
  cancelled = false;
  paused = false;
  private wake: (() => void) | undefined;
  stop(): void { this.cancelled = true; this.wake?.(); }
  pause(): void { this.paused = true; }
  resume(): void { this.paused = false; this.wake?.(); }
  check(): void { if (this.cancelled) throw new Error('CANCELLED'); }
  async checkpoint(): Promise<void> {
    this.check();
    while (this.paused) {
      await new Promise<void>(resolve => { this.wake = resolve; });
      this.wake = undefined;
      this.check();
    }
  }
}

export class UnknownToolOutcomeError extends Error {}
