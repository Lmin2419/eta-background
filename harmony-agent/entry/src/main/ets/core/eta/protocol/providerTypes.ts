// Ported from app/src/main/kotlin/io/github/mangi/eta/agent/model/AgentProvider.kt,
// AgentModelClient.kt and data/model/Reasoning.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
export type JsonObject = Record<string, unknown>;
export function object(value: unknown): JsonObject | null { return value !== null && typeof value === 'object' && !Array.isArray(value) ? value as JsonObject : null; }
export function array(value: unknown): unknown[] { return Array.isArray(value) ? value : []; }
export function string(value: unknown, fallback = ''): string { return value === null || value === undefined ? fallback : typeof value === 'object' ? JSON.stringify(value) : String(value); }
export function integer(value: unknown, fallback = -1): number { const v = typeof value === 'number' || typeof value === 'string' && /^-?\d+$/.test(value) ? Number(value) : NaN; return Number.isFinite(v) ? Math.trunc(v) : fallback; }
export function copy<T>(value: T): T { return JSON.parse(JSON.stringify(value)) as T; }
export const REASONING_EFFORTS = ['off', 'default', 'minimal', 'low', 'medium', 'high', 'xhigh', 'max'] as const;
export type ReasoningEffort = typeof REASONING_EFFORTS[number];
export interface ModelReasoningCapabilities {
  supportedEfforts: ReasoningEffort[]; defaultEffort?: ReasoningEffort | null; defaultEnabled?: boolean | null;
  mandatory?: boolean; canDisable?: boolean; supportsBudget?: boolean; maxBudgetTokens?: number | null; supportsMaxTokens?: boolean | null;
}
export function selectableEfforts(c: ModelReasoningCapabilities): ReasoningEffort[] { return REASONING_EFFORTS.filter(e => e === 'off' ? c.canDisable && !c.mandatory : e === 'default' || c.supportedEfforts.includes(e)); }
export function normalizeEffort(c: ModelReasoningCapabilities, effort: ReasoningEffort): ReasoningEffort {
  const list = selectableEfforts(c); if (list.includes(effort)) return effort; if (effort === 'off' || effort === 'default') return 'default';
  const lower = list.filter(e => e !== 'off' && REASONING_EFFORTS.indexOf(e) <= REASONING_EFFORTS.indexOf(effort)); return lower[lower.length - 1] || 'default';
}
export interface CustomHeader { name: string; value: string; }
export interface CustomBody { key: string; value: unknown; }
export interface ProviderModelConfig {
  baseUrl: string; apiKey: string; model: string; systemPrompt: string;
  providerId?: string; providerSourceType?: string; providerType?: string; openAiEndpointMode?: string;
  authMode?: string; hostedWebSearchEnabled?: boolean; supportsSearchSuggestions?: boolean;
  thinkingEnabled?: boolean; reasoningEffort?: ReasoningEffort | null; reasoningCapabilities?: ModelReasoningCapabilities | null;
  extraBodyJson?: string; customBody?: CustomBody[]; customHeaders?: CustomHeader[]; anthropicVersion?: string;
  contextWindow?: number | null; autoCompactionEnabled?: boolean;
}
export type ProviderPurpose = 'chat' | 'compaction' | 'reply_rewrite' | 'transcript_refine';
export interface ProviderRequest { config: ProviderModelConfig; messages: JsonObject[]; tools: JsonObject[]; sessionId: string; purpose?: ProviderPurpose; }
export function effectiveRequest(request: ProviderRequest): ProviderRequest {
  return !request.purpose || request.purpose === 'chat' ? request : { ...request, config: { ...request.config, hostedWebSearchEnabled: false, extraBodyJson: '', customBody: [] }, tools: [] };
}
export interface ProviderRunControl { check(): void; }
export type AssistantBlockKind = 'TEXT' | 'THINKING' | 'TOOL_CALL';
export interface ProviderUsage { contextTokens: number | null; inputTokens: number | null; outputTokens: number | null; cachedTokens: number | null; reasoningTokens: number | null; }
export type ProviderEvent =
  { type: 'RequestStarted' } | { type: 'ResponseHeaders'; httpCode: number } |
  { type: 'BlockStart'; kind: AssistantBlockKind; index: number; blockId: string | null; name: string | null } |
  { type: 'BlockDelta'; kind: AssistantBlockKind; index: number; delta: string } |
  { type: 'BlockEnd'; kind: AssistantBlockKind; index: number; blockId: string | null; name: string | null; content: string; replaceContent: boolean } |
  { type: 'HostedToolStarted'; id: string; name: string } | { type: 'HostedToolFinished'; id: string; name: string; success: boolean } |
  { type: 'Usage'; usage: ProviderUsage; contextInputTokens?: number | null } | { type: 'Completed'; reason: string | null } |
  { type: 'SearchSuggestions'; index: number; html: string };
export type ProviderEventSink = (event: ProviderEvent) => void;
