// Harmony adapter for app/src/main/kotlin/io/github/mangi/eta/data/repository/RuntimeConfigRepository.kt,
// ui/app/AgentAppState.kt @ 3b1794d. Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import type { ModelProfile } from '../profiles';
import type { ModelSettings } from '../model';
import { resolveReasoningCapabilities, geminiReasoningCapabilities } from './reasoningCapabilityResolver';
import { resolveProviderSource } from './providerSourceRegistry';
import { normalizeEffort, ReasoningEffort } from './protocol/providerTypes';
import { parseEffort } from './storage/conversationCodec';
import { RuntimePreferences } from './runtimePreferences';
export function foundationReasoningConfig(profile: ModelProfile, model: string, preferences: RuntimePreferences): Partial<ModelSettings> {
  const metadata = profile.metadata?.find(m => m.id === model), source = resolveProviderSource(profile.id, profile.baseUrl, profile.protocol), infer = source === 'custom' && profile.protocol === 'responses';
  const capabilities = profile.protocol === 'gemini' ? geminiReasoningCapabilities(model) || { supportedEfforts: [] } : resolveReasoningCapabilities(infer ? 'openai' : source, { modelId: model, ...metadata }, infer);
  const selected = profile.reasoningEffort ? parseEffort(profile.reasoningEffort) as ReasoningEffort : 'default';
  const effort = profile.protocol==='gemini' ? normalizeEffort(capabilities!,preferences.thinkingEnabled?selected:'off') : !preferences.thinkingEnabled ? 'off' : capabilities ? normalizeEffort(capabilities, selected) : 'off';
  return { providerSourceType: source, reasoningCapabilities: capabilities, thinkingEnabled: effort !== 'off', reasoningEffort: effort,
    contextWindow: metadata?.contextWindow, autoCompactionEnabled: preferences.autoCompactionEnabled };
}
