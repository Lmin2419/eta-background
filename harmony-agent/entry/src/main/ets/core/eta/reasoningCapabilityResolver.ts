// Ported from app/src/main/kotlin/io/github/mangi/eta/data/provider/ReasoningCapabilityResolver.kt,
// GeminiModelCapabilities.kt @ 3b1794d. Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { ModelReasoningCapabilities as Cap, ReasoningEffort as Effort } from './protocol/providerTypes';
export interface ReasoningModel { modelId: string; reasoning?: boolean | null; reasoningOverride?: boolean | null; reasoningCapabilities?: Cap | null; reasoningCapabilitiesOverride?: Cap | null; }
const lowToMax: Effort[] = ['low', 'medium', 'high', 'xhigh', 'max'], lowToXHigh: Effort[] = ['low', 'medium', 'high', 'xhigh'];
function cap(supportedEfforts: Effort[], canDisable = false, mandatory = false, defaultEffort: Effort | null = null, budget: number | null = null): Cap { return { supportedEfforts, defaultEffort, defaultEnabled: true, mandatory, canDisable, supportsBudget: budget !== null, maxBudgetTokens: budget }; }
const mandatoryDefault = (): Cap => ({ supportedEfforts: [], defaultEnabled: true, mandatory: true });
export function geminiReasoningCapabilities(id: string): Cap | null {
  const m = id.trim().replace(/^models\//, '').toLowerCase(), low: Effort[] = ['low', 'medium', 'high'], minimal: Effort[] = ['minimal', ...low];
  if (['gemini-4-argon', 'gemini-3.8-flash'].includes(m)) return cap(low, false, true, 'medium');
  if (m === 'gemini-3.6-flash') return cap(minimal, false, true, 'medium');
  if (m === 'gemini-3.5-flash-lite') return cap(minimal, false, true, 'minimal');
  if (m === 'gemini-3.1-pro-preview') return cap(low, false, true, 'high');
  if (m === 'gemini-3-flash-preview') return cap(minimal, false, true, 'high');
  if (m === 'gemini-3-pro-preview') return cap(['low', 'high'], false, true, 'high'); return null;
}
export function catalogReasoningCapabilities(source: string, id: string): Cap | null {
  const m = id.trim().toLowerCase();
  switch (source) {
    case 'openai':
      if (m === 'gpt-6-astra') return cap(lowToMax, false, true);
      if (m === 'gpt-6.1-sol') return cap(lowToMax, false, true, 'medium');
      if (['gpt-6-sol', 'gpt-6-luna', 'gpt-5.6', 'gpt-5.6-sol', 'gpt-5.6-terra', 'gpt-5.6-luna'].includes(m)) return cap(lowToMax, true, false, 'medium');
      return m === 'gpt-5.5' ? cap(lowToXHigh, true, false, 'medium') : null;
    case 'gemini': return geminiReasoningCapabilities(m);
    case 'anthropic':
      if (['claude-fable-5', 'claude-fable-5-1'].includes(m)) return cap(lowToMax, false, true, 'high');
      if (m === 'claude-opus-5-5') return cap(lowToMax, false, true, 'medium');
      return ['claude-opus-4-8', 'claude-sonnet-5'].includes(m) ? cap(lowToMax, true, false, 'high') : null;
    case 'bailian':
      if (m.startsWith('qwen3.8-')) return cap(['low', 'medium', 'xhigh'], true, false, 'xhigh');
      if (m.startsWith('qwen3.7-')) return cap(lowToMax, true, false, 'max', 262144);
      if (m === 'deepseek-v4.1-flash') return cap(['low', 'high', 'max'], true, false, 'high');
      if (m === 'kimi-k3') return cap(['low', 'high', 'max'], false, true, 'max');
      if (m.startsWith('kimi-k2.7-code')) return mandatoryDefault();
      return m.startsWith('kimi-k2.6') ? cap([], true) : null;
    case 'deepseek':
      if (m === 'deepseek-flash' || m.startsWith('deepseek-v4-flash')) return cap(['low', 'high', 'max'], true, false, 'high');
      return m.startsWith('deepseek-v4-pro') ? cap(['high', 'max'], true, false, 'high') : null;
    case 'moonshot':
      if (m.startsWith('kimi-k3')) return cap(['low', 'high', 'max'], false, true, 'max');
      if (m.startsWith('kimi-k2.7-code')) return mandatoryDefault();
      return m.startsWith('kimi-k2.6') || m.startsWith('kimi-k2.5') ? cap([], true) : null;
    case 'mimo': return m.startsWith('mimo-v2.5') || m.startsWith('mimo-v2.6') ? cap([], true) : null;
    case 'minimax': return mandatoryDefault();
    case 'stepfun':
      if (m === 'step-5-preview' || m === 'step-3.7-flash') return cap(['low', 'medium', 'high'], false, true, 'medium');
      return m.startsWith('step-3.5-flash-2603') ? cap(['low', 'high'], false, true) : mandatoryDefault();
    default: return null;
  }
}
function family(source: string, id: string): Cap | null {
  const m = id.trim().toLowerCase(), catalog = catalogReasoningCapabilities(source, m);
  if (source === 'bailian') { if (catalog) return catalog; if (m.includes('deepseek')) return cap(['high', 'max'], true, false, 'high'); if (m.startsWith('kimi-k3')) return cap(['max'], false, true, 'max'); }
  return ['deepseek', 'moonshot', 'mimo', 'minimax', 'stepfun'].includes(source) ? catalog : null;
}
export function resolveReasoningCapabilities(source: string, model: ReasoningModel, inferExactCatalogModel = false): Cap | null {
  const enabled = model.reasoningOverride ?? model.reasoning; if (enabled === false) return null;
  const explicit = model.reasoningCapabilitiesOverride ?? model.reasoningCapabilities; if (explicit) return explicit;
  if (enabled !== true) return inferExactCatalogModel ? catalogReasoningCapabilities(source, model.modelId) : null;
  return catalogReasoningCapabilities(source, model.modelId) || family(source, model.modelId) || mandatoryDefault();
}
export const effortDisplayName = (e: Effort): string => e === 'xhigh' ? 'XHigh' : e[0].toUpperCase() + e.slice(1);
