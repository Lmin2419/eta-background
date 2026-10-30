// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/model/AgentModelPickerUiState.kt,
// ModelSearchQuery.kt; ui/components/AgentModelPicker.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { AgentChatMessageUi } from "./model/conversation";
import { EtaModelPickerDefaults } from "./tokens";
export interface PickerModel {
  id: string;
  modelId: string;
  displayName: string;
  isEnabled: boolean;
  sortOrder: number;
  contextWindow: number | null;
}
export interface PickerProvider {
  id: string;
  name: string;
  sourceType: string;
  isEnabled: boolean;
  sortOrder: number;
  authMode: string;
  hasApiKey: boolean;
  models: PickerModel[];
}
export interface AgentModelOptionUi {
  id: string;
  providerId: string;
  providerName: string;
  providerSourceType: string;
  modelId: string;
  displayName: string;
  contextWindow: number | null;
}
export interface AgentModelProviderGroupUi {
  providerId: string;
  providerName: string;
  providerSourceType: string;
  models: AgentModelOptionUi[];
}
export interface AgentModelPickerUiState {
  providerGroups: AgentModelProviderGroupUi[];
  selectedModel: AgentModelOptionUi | null;
  isChanging: boolean;
}
export interface AgentContextUsageUi {
  contextTokens: number | null;
  contextWindow: number | null;
}
const oauth = (mode: string): boolean =>
  mode === "openai_chatgpt" || mode === "xai_grok";
function option(
  provider: PickerProvider,
  model: PickerModel,
): AgentModelOptionUi {
  return {
    id: model.id,
    providerId: provider.id,
    providerName: provider.name,
    providerSourceType: provider.sourceType,
    modelId: model.modelId,
    displayName: model.displayName.trim() ? model.displayName : model.modelId,
    contextWindow: model.contextWindow,
  };
}
export function projectModelPicker(
  providers: PickerProvider[],
  providerId: string | null,
  modelId: string | null,
): AgentModelPickerUiState {
  const enabled = providers
    .filter(
      (p) => p.isEnabled && (p.authMode === "api_key" || oauth(p.authMode)),
    )
    .slice()
    .sort((a, b) => a.sortOrder - b.sortOrder);
  const selectedProvider = enabled.find((p) => p.id === providerId);
  const selected = selectedProvider?.models.find(
    (m) => m.id === modelId && m.isEnabled,
  );
  let selectedModel =
    selectedProvider && selected ? option(selectedProvider, selected) : null;
  if (!selectedModel)
    for (const p of enabled) {
      const m = p.models.find((m) => m.id === modelId && m.isEnabled);
      if (m) {
        selectedModel = option(p, m);
        break;
      }
    }
  const providerGroups: AgentModelProviderGroupUi[] = [];
  for (const p of enabled.filter((p) => oauth(p.authMode) || p.hasApiKey)) {
    const models = p.models
      .filter((m) => m.isEnabled)
      .slice()
      .sort((a, b) => a.sortOrder - b.sortOrder)
      .map((m) => option(p, m));
    if (models.length)
      providerGroups.push({
        providerId: p.id,
        providerName: p.name,
        providerSourceType: p.sourceType,
        models,
      });
  }
  return { providerGroups, selectedModel, isChanging: false };
}
export function enabledModel(
  providers: PickerProvider[],
  id: string | null,
): AgentModelOptionUi | null {
  if (id === null) return null;
  for (const p of providers.filter((p) => p.isEnabled)) {
    const m = p.models.find((m) => m.isEnabled && m.id === id);
    if (m) return option(p, m);
  }
  return null;
}
export function modelCount(state: AgentModelPickerUiState): number {
  return state.providerGroups.reduce((n, g) => n + g.models.length, 0);
}
export class ModelSearchQuery {
  private tokens: string[];
  constructor(query: string) {
    this.tokens = query
      .toLowerCase()
      .split(/[^\p{L}\p{N}]+/u)
      .filter((t) => !!t);
  }
  get isEmpty(): boolean {
    return this.tokens.length === 0;
  }
  matches(fields: string[]): boolean {
    const normalized = fields.map((f) =>
      f.toLowerCase().replace(/[^\p{L}\p{N}]/gu, ""),
    );
    return this.tokens.every((token) =>
      normalized.some((field) => {
        let i = 0;
        for (const char of field) {
          if (char === token[i]) i++;
          if (i === token.length) return true;
        }
        return false;
      }),
    );
  }
}
export function filterModelGroups(
  state: AgentModelPickerUiState,
  query: string,
  provider: string | null = null,
): AgentModelProviderGroupUi[] {
  const search = new ModelSearchQuery(query);
  const result: AgentModelProviderGroupUi[] = [];
  for (const group of state.providerGroups) {
    if (provider !== null && group.providerId !== provider) continue;
    const models = group.models.filter((m) =>
      search.matches([m.displayName, m.modelId, group.providerName]),
    );
    if (models.length) result.push({ ...group, models });
  }
  return result;
}
export function latestContextUsage(
  messages: AgentChatMessageUi[],
  selected: AgentModelOptionUi | null,
): AgentContextUsageUi {
  for (let i = messages.length - 1; i >= 0; i--) {
    const m = messages[i];
    if (
      m.kind === "assistant" &&
      m.usage?.contextTokens !== null &&
      m.usage?.contextTokens !== undefined
    )
      return {
        contextTokens: m.usage.contextTokens,
        contextWindow: selected?.contextWindow ?? null,
      };
    if (m.kind === "notice" && m.code === "ContextCompaction") break;
  }
  return {
    contextTokens: null,
    contextWindow: selected?.contextWindow ?? null,
  };
}
export function contextUsageProgress(
  tokens: number | null,
  window: number | null,
): number | null {
  return tokens === null || tokens < 0 || window === null || window <= 0
    ? null
    : Math.min(1, Math.max(0, tokens / window));
}
export interface NumberFormatter {
  format(
    value: number,
    minimum: number,
    maximum: number,
    grouping: boolean,
  ): string;
}
export function formatCompactTokenCount(
  value: number,
  format: NumberFormatter,
): string {
  const abs = Math.abs(value),
    divisor = abs >= 1000000 ? 1000000 : abs >= 1000 ? 1000 : 1;
  return (
    format.format(value / divisor, 0, divisor === 1 ? 0 : 2, divisor === 1) +
    (divisor === 1000000 ? "M" : divisor === 1000 ? "K" : "")
  );
}
export function formatContextUsage(
  usage: AgentContextUsageUi,
  format: NumberFormatter,
  noUsage = "No usage data yet",
  noLimit = "Context window unknown, so automatic compaction is off",
): string {
  if (usage.contextWindow === null || usage.contextWindow <= 0) return noLimit;
  if (usage.contextTokens === null) return noUsage;
  return (
    formatCompactTokenCount(usage.contextTokens, format) +
    " / " +
    formatCompactTokenCount(usage.contextWindow, format) +
    " tokens · " +
    format.format(
      (usage.contextTokens / usage.contextWindow) * 100,
      1,
      1,
      true,
    ) +
    "%"
  );
}
export class ModelPickerSession {
  query = "";
  provider: string | null = null;
  pendingId: string | null = null;
  private timer: ReturnType<typeof setTimeout> | undefined;
  private generation = 0;
  open(): void {
    this.cancel();
    this.query = "";
    this.provider = null;
  }
  cancel(): void {
    this.generation++;
    clearTimeout(this.timer);
    this.timer = undefined;
    this.pendingId = null;
  }
  choose(id: string, confirm: (id: string) => void): boolean {
    if (this.pendingId !== null) return false;
    this.pendingId = id;
    const version = this.generation;
    this.timer = setTimeout(() => {
      if (version === this.generation) {
        this.timer = undefined;
        confirm(id);
      }
    }, EtaModelPickerDefaults.SelectionConfirmMs);
    return true;
  }
}
