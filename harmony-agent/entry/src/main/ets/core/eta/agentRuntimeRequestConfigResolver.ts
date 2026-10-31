// Ported from app/src/main/kotlin/io/github/mangi/eta/agent/runtime/AgentRuntimeRequestConfigResolver.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11). Pure wire port; OAuth/voice entry is not enabled in Harmony.
import { ProviderModelConfig, JsonObject, normalizeEffort, ReasoningEffort, copy } from './protocol/providerTypes';
export interface RuntimeModelConfig extends ProviderModelConfig { terminalTools?: boolean; browserTools?: boolean; deviceDirectTools?: boolean; deviceSensitiveReadTools?: boolean; deviceSensitiveActionTools?: boolean; subagentTools?: boolean; }
export interface RuntimeConfigRequest { runId: string; prompt: string; config: RuntimeModelConfig; handoff?: { id: string; source: string; payload: JsonObject }; }
export const AGENT_UI_SOURCE = 'agent_ui', ETA_VOICE_SOURCE = 'eta_voice';
export const effectiveEffort = (c: RuntimeModelConfig): ReasoningEffort => c.reasoningEffort ?? (c.thinkingEnabled ? 'default' : 'off');
export const usesDefaultRuntimeConfig = (r: RuntimeConfigRequest): boolean => r.handoff?.source === ETA_VOICE_SOURCE;
export const requiresRuntimeConfig = (r: RuntimeConfigRequest): boolean => usesDefaultRuntimeConfig(r) || ['openai_chatgpt', 'xai_grok'].includes(r.config.authMode || '');
export function constrainPresentation(r: RuntimeConfigRequest, isEtaProcess: boolean): RuntimeConfigRequest {
  return !r.config.supportsSearchSuggestions || isEtaProcess && [AGENT_UI_SOURCE, ETA_VOICE_SOURCE].includes(r.handoff?.source || '') ? r : { ...r, config: { ...r.config, supportsSearchSuggestions: false } };
}
export function applyRuntimeConfig(r: RuntimeConfigRequest, local: RuntimeModelConfig): RuntimeConfigRequest {
  if (!requiresRuntimeConfig(r)) return r; const entry = r.config, preserve = !usesDefaultRuntimeConfig(r);
  if (preserve && (local.providerId !== entry.providerId || local.authMode !== entry.authMode || local.model !== entry.model)) throw Error('账号登录配置已变更，请重新选择服务商和模型');
  const effort = preserve ? local.reasoningCapabilities ? normalizeEffort(local.reasoningCapabilities, effectiveEffort(entry)) : effectiveEffort(local) : effectiveEffort(local);
  const config: RuntimeModelConfig = { ...local, terminalTools: entry.terminalTools, browserTools: entry.browserTools, deviceDirectTools: entry.deviceDirectTools, deviceSensitiveReadTools: entry.deviceSensitiveReadTools, deviceSensitiveActionTools: entry.deviceSensitiveActionTools, subagentTools: entry.subagentTools, thinkingEnabled: effort !== 'off', reasoningEffort: effort, autoCompactionEnabled: local.autoCompactionEnabled !== false && entry.autoCompactionEnabled !== false, supportsSearchSuggestions: entry.supportsSearchSuggestions };
  const handoff = r.handoff; if (!handoff) return { ...r, config };
  // The archive envelope's other fields are preserved. No credential is copied into payload.
  const payload = handoff.payload; const valid = payload.type === 'external_archive' && typeof payload.userText === 'string' && !!payload.userText.trim() && typeof payload.conversationKey === 'string' && !!payload.conversationKey.trim();
  return { ...r, config, handoff: valid ? { ...handoff, payload: { ...copy(payload), thinkingEnabled: effort !== 'off', reasoningEffort: effort } } : handoff };
}
