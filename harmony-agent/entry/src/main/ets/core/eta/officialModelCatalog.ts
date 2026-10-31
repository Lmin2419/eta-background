// Ported from app/src/main/kotlin/io/github/mangi/eta/data/provider/OfficialModelCatalog.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import type { ModelMetadata } from '../modelMetadata';
import { OFFICIAL_MODELS } from './officialModels.generated';
import { catalogReasoningCapabilities } from './reasoningCapabilityResolver';
import { resolveProviderSource } from './providerSourceRegistry';
import { copy } from './protocol/providerTypes';
export interface CatalogProvider { id: string; baseUrl: string; protocol?: string; }
export const OFFICIAL_CATALOG_REVISION = 4;
const additions: Record<number, Record<string, string[]>> = {
  1: { openai: ['gpt-6-astra','gpt-6-sol','gpt-6-luna'], anthropic: ['claude-fable-5-1','claude-opus-5-5'],
    bailian: ['qwen3.8-max','qwen3.8-flash','deepseek-v4.1-flash','kimi-k3'], deepseek: ['deepseek-flash'], mimo: ['mimo-v2.6-pro','mimo-v2.6-flash'], stepfun: ['step-5-preview'] },
  2: { openai: ['gpt-6.1-sol'] }, 4: { gemini: ['gemini-3.8-flash','gemini-4-argon','gemini-3.5-flash-lite'] }
};
export function catalogSource(p: CatalogProvider): string {
  const source = resolveProviderSource(p.id,p.baseUrl,p.protocol);
  return source === 'custom' && p.protocol === 'responses' ? 'openai' : source;
}
export function officialModels(p: CatalogProvider): ModelMetadata[] {
  const source = catalogSource(p);
  return copy(OFFICIAL_MODELS[source] || []).map((m,sortOrder) => ({ ...m, sortOrder, vision: m.inputModalities?.includes('image'), reasoningCapabilities: catalogReasoningCapabilities(source,m.id) || undefined }));
}
export function officialModelsAddedSince(p: CatalogProvider, revision: number): ModelMetadata[] {
  const ids = Object.entries(additions).filter(([r])=>Number(r)>revision).flatMap(([,groups])=>groups[catalogSource(p)]||[]);
  return officialModels(p).filter(m=>ids.includes(m.id));
}
/** Catalog windows are metadata; this does not silently enable compaction on imported/custom models. */
export function enrichOfficialModels(p: CatalogProvider, models: ModelMetadata[]): ModelMetadata[] {
  const catalog = officialModels(p);
  return models.map(m=>{
    const official = catalog.find(o=>o.id.toLowerCase()===m.id.toLowerCase()); if(!official)return m;
    return { ...m, name: !m.name.trim()||m.name===m.id ? official.name : m.name, ownedBy:m.ownedBy??official.ownedBy,
      catalogContextWindow:m.catalogContextWindow??official.catalogContextWindow, inputModalities:m.inputModalities?.length?m.inputModalities:official.inputModalities,
      outputModalities:m.outputModalities?.length?m.outputModalities:official.outputModalities, vision:m.vision??official.vision,
      attachment:m.attachment??official.attachment, toolCall:m.toolCall??official.toolCall, reasoning:m.reasoning??official.reasoning,
      reasoningCapabilities:m.reasoning===false?undefined:m.reasoningCapabilities??official.reasoningCapabilities,
      structuredOutput:m.structuredOutput??official.structuredOutput, supportsTemperature:m.supportsTemperature??official.supportsTemperature };
  });
}
