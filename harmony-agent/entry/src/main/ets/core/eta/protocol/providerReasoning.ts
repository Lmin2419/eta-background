// Ported from app/src/main/kotlin/io/github/mangi/eta/agent/model/ProviderReasoning.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { resolveProviderSource } from '../providerSourceRegistry';
import { JsonObject, ProviderModelConfig, ReasoningEffort, selectableEfforts, object, integer } from './providerTypes';
const label = (e: ReasoningEffort): string => e === 'xhigh' ? 'XHigh' : e[0].toUpperCase() + e.slice(1);
function unsupported(provider: string, effort: ReasoningEffort): never { throw Error(provider + ' 不支持 ' + label(effort) + ' thinking effort'); }
export function validatedEffort(config: ProviderModelConfig): ReasoningEffort {
  const effort = config.reasoningEffort ?? (config.thinkingEnabled ? 'default' : 'off'), capabilities = config.reasoningCapabilities;
  if (capabilities && !selectableEfforts(capabilities).includes(effort)) throw Error('当前模型不支持 ' + label(effort) + ' thinking effort');
  if (capabilities?.mandatory && effort === 'off') throw Error('当前模型强制启用思考，不能选择 Off'); return effort;
}
function named(r: JsonObject, e: ReasoningEffort): void { r.reasoning_effort = e === 'off' ? 'none' : e; }
function toggle(r: JsonObject, e: ReasoningEffort, keep = false): void { r.thinking = { type: e === 'off' ? 'disabled' : 'enabled', ...(e !== 'off' && keep ? { keep: 'all' } : {}) }; }
function toggleOnly(r: JsonObject, provider: string, e: ReasoningEffort, keep = false): void { if (e !== 'off') unsupported(provider, e); toggle(r, e, keep); }
function legacy(source: string, m: string): boolean {
  switch (source) {
    case 'openai': return m.startsWith('gpt-5') || m.startsWith('gpt-6') || m.startsWith('o');
    case 'anthropic': return m.startsWith('claude-'); case 'bailian': return m.startsWith('qwen3.7-') || m.startsWith('qwen3.8-') || m.startsWith('kimi-') || m.includes('deepseek');
    case 'deepseek': case 'openrouter': return true; case 'moonshot': return m.startsWith('kimi-');
    case 'mimo': return m.startsWith('mimo-v2.5') || m.startsWith('mimo-v2.6');
    case 'stepfun': return m === 'step-5-preview' || m === 'step-3.7-flash' || m.startsWith('step-3.5-flash-2603'); default: return false;
  }
}
export function applyResponsesReasoning(r: JsonObject, c: ProviderModelConfig): void {
  if (!c.reasoningCapabilities) return; const e = validatedEffort(c);
  r.reasoning = e === 'off' ? { effort: 'none' } : { summary: 'auto', ...(e !== 'default' ? { effort: e } : {}) };
}
export function applyAnthropicReasoning(r: JsonObject, c: ProviderModelConfig): void {
  const e = validatedEffort(c); if (e === 'default') return;
  if (e === 'off') { r.thinking = { type: 'disabled' }; const output = object(r.output_config); if (output) { delete output.effort; if (!Object.keys(output).length) delete r.output_config; } return; }
  if (e === 'minimal') unsupported('Anthropic', e); r.thinking = { type: 'adaptive', display: 'summarized' }; r.output_config = { ...(object(r.output_config) || {}), effort: e };
  if (e === 'xhigh' || e === 'max') r.max_tokens = Math.max(integer(r.max_tokens, 0), 65536);
}
export function applyOpenAiCompatibleReasoning(r: JsonObject, c: ProviderModelConfig): void {
  const e = validatedEffort(c), source = c.providerSourceType || resolveProviderSource(c.providerId || '', c.baseUrl, c.providerType), m = c.model.trim().toLowerCase();
  if (!c.reasoningCapabilities && !legacy(source, m)) return;
  if (e === 'default') { if (!Object.prototype.hasOwnProperty.call(r, 'thinking') && ['moonshot', 'bailian'].includes(source) && m.startsWith('kimi-k2.6')) r.thinking = { type: 'enabled', keep: 'all' }; return; }
  switch (source) {
    case 'bailian':
      if (m.startsWith('qwen3.7-')) {
        if (e === 'off') { r.enable_thinking = false; delete r.thinking_budget; return; }
        if (e === 'minimal') unsupported('百炼 Qwen', e);
        const budget = { low: 4096, medium: 16384, high: 32768, xhigh: 65536, max: c.reasoningCapabilities?.maxBudgetTokens ?? 65536 }[e];
        const limit = integer(r.max_completion_tokens, budget); r.enable_thinking = true;
        r.thinking_budget = limit > 0 ? Math.min(budget, Math.max(1, limit - Math.max(2048, Math.floor(limit / 8)))) : budget;
        if (r.max_completion_tokens === undefined || r.max_completion_tokens === null) r.thinking_budget = budget; return;
      }
      if (m.startsWith('qwen3.8-')) delete r.thinking_budget;
      if (m.startsWith('kimi-k2.6') || m.startsWith('kimi-k2.5')) toggleOnly(r, '百炼 Kimi', e, m.startsWith('kimi-k2.6')); else named(r, e); return;
    case 'siliconflow':
      if (e === 'off') { r.enable_thinking = false; delete r.thinking_budget; return; }
      r.thinking_budget = Math.min({ minimal: 128, low: 1024, medium: 4096, high: 8192, xhigh: 16384, max: 32768 }[e], c.reasoningCapabilities?.maxBudgetTokens ?? 32768);
      if (c.reasoningCapabilities?.canDisable) r.enable_thinking = true; return;
    case 'deepseek':
      toggle(r, e); if (e === 'off') { delete r.reasoning_effort; return; } if (e === 'minimal') unsupported('DeepSeek', e);
      r.reasoning_effort = e === 'low' ? 'low' : e === 'medium' || e === 'high' ? 'high' : 'max'; return;
    case 'moonshot':
      if (m.startsWith('kimi-k3') && !['low', 'high', 'max'].includes(e)) unsupported('Kimi K3', e);
      if (m.startsWith('kimi-k2.7-code')) unsupported('Kimi K2.7 Code', e);
      if (m.startsWith('kimi-k2.6') || m.startsWith('kimi-k2.5')) toggleOnly(r, 'Kimi', e, m.startsWith('kimi-k2.6')); else named(r, e); return;
    case 'mimo': toggleOnly(r, 'MiMo', e); return; case 'minimax': unsupported('MiniMax', e);
    case 'openrouter': r.reasoning = { ...(object(r.reasoning) || {}), effort: e === 'off' ? 'none' : e }; return;
    case 'stepfun': if (!(m === 'step-5-preview' || m === 'step-3.7-flash' ? ['low', 'medium', 'high'] : ['low', 'high']).includes(e)) unsupported('StepFun', e); named(r, e); return;
    case 'openai': case 'xai': case 'custom': named(r, e); return;
  }
}
