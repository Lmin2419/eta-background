// Ported from app/src/test/kotlin/io/github/mangi/eta/ui/model/AgentModelPickerProjectorTest.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import test from "node:test";
import assert from "node:assert/strict";
import {
  PickerModel,
  PickerProvider,
  AgentModelOptionUi,
  projectModelPicker,
  filterModelGroups,
  modelCount,
  latestContextUsage,
  contextUsageProgress,
  formatContextUsage,
  formatCompactTokenCount,
  enabledModel,
  NumberFormatter,
} from "../../entry/src/main/ets/core/eta/modelPicker";
import { agentMessage } from "../../entry/src/main/ets/core/eta/model/conversation";
const model = (id: string, patch: Partial<PickerModel> = {}): PickerModel => ({
  id,
  modelId: id,
  displayName: id,
  isEnabled: true,
  sortOrder: 0,
  contextWindow: null,
  ...patch,
});
const provider = (
  id: string,
  models: PickerModel[],
  patch: Partial<PickerProvider> = {},
): PickerProvider => ({
  id,
  name: id,
  sourceType: "custom",
  isEnabled: true,
  sortOrder: 0,
  authMode: "api_key",
  hasApiKey: true,
  models,
  ...patch,
});
const formatter = (locale = "en-US"): NumberFormatter => ({
  format: (value, min, max, grouping) =>
    new Intl.NumberFormat(locale, {
      minimumFractionDigits: min,
      maximumFractionDigits: max,
      useGrouping: grouping,
    }).format(value),
});
test("project_keepsOnlyEnabledProvidersAndModelsAndResolvesSelection", () => {
  const selected = model("model-selected", {
    displayName: "GPT 5.6",
    contextWindow: 1050000,
  });
  const state = projectModelPicker(
    [
      provider("disabled-provider", [model("hidden-provider-model")], {
        isEnabled: false,
      }),
      provider("no-enabled-models", [
        model("disabled-only-model", { isEnabled: false }),
      ]),
      provider(
        "openai",
        [model("disabled-model", { isEnabled: false }), selected],
        { name: "OpenAI", sourceType: "openai" },
      ),
    ],
    "openai",
    selected.id,
  );
  assert.deepEqual(
    state.providerGroups.map((g) => g.providerId),
    ["openai"],
  );
  assert.deepEqual(
    state.providerGroups[0].models.map((m) => m.id),
    [selected.id],
  );
  assert.equal(state.selectedModel?.id, selected.id);
  assert.equal(state.selectedModel?.contextWindow, 1050000);
});
test("project_hidesProvidersWithoutApiKeyButPreservesCurrentSelection", () => {
  const state = projectModelPicker(
    [
      provider("missing-key", [model("selected")], { hasApiKey: false }),
      provider("configured", [model("available")]),
    ],
    "missing-key",
    "selected",
  );
  assert.deepEqual(
    state.providerGroups.map((g) => g.providerId),
    ["configured"],
  );
  assert.equal(state.selectedModel?.id, "selected");
  assert.equal(state.selectedModel?.providerId, "missing-key");
});
test("project_showsBothOAuthProvidersWithoutApiKeys", () => {
  const state = projectModelPicker(
    [
      provider("chatgpt", [model("chatgpt-model")], {
        hasApiKey: false,
        authMode: "openai_chatgpt",
      }),
      provider("grok", [model("grok-model")], {
        hasApiKey: false,
        authMode: "xai_grok",
      }),
    ],
    "grok",
    "grok-model",
  );
  assert.deepEqual(
    state.providerGroups.map((g) => g.providerId),
    ["chatgpt", "grok"],
  );
  assert.deepEqual(
    state.providerGroups.flatMap((g) => g.models).map((m) => m.id),
    ["chatgpt-model", "grok-model"],
  );
  assert.equal(state.selectedModel?.id, "grok-model");
});
test("project_doesNotTreatUnknownAuthModesAsConfiguredApiKeys", () => {
  assert.deepEqual(
    projectModelPicker(
      [provider("unknown", [model("model")], { authMode: "unsupported" })],
      null,
      null,
    ).providerGroups,
    [],
  );
});
test("filterGroups_matchesModelNamesIdsAndProviderNames", () => {
  const state = projectModelPicker(
    [
      provider(
        "deepseek",
        [
          model("deepseek-v4.1-flash", { displayName: "DeepSeek V4.1 Flash" }),
          model("deepseek-r2", { displayName: "DeepSeek R2" }),
        ],
        { name: "DeepSeek" },
      ),
      provider("zhipu", [model("glm-5", { displayName: "GLM-5" })], {
        name: "智谱",
      }),
    ],
    null,
    null,
  );
  const ids = (query: string) =>
    filterModelGroups(state, query)
      .flatMap((g) => g.models)
      .map((m) => m.id);
  assert.deepEqual(filterModelGroups(state, "  "), state.providerGroups);
  assert.deepEqual(ids("v4flash"), ["deepseek-v4.1-flash"]);
  assert.deepEqual(ids("智谱"), ["glm-5"]);
  assert.deepEqual(ids("deepseek r2"), ["deepseek-r2"]);
  assert.deepEqual(ids("claude"), []);
  assert.equal(modelCount(state), 3);
});
test("latestContextUsage_usesLastMeasuredRoundAndSelectedWindow", () => {
  const selected = projectModelPicker(
    [provider("provider", [model("model", { contextWindow: 100000 })])],
    "provider",
    "model",
  ).selectedModel;
  const first = agentMessage("first", "one"),
    last = agentMessage("last", "three");
  first.usage = {
    contextTokens: 10000,
    inputTokens: null,
    outputTokens: null,
    reasoningTokens: null,
    cachedTokens: null,
  };
  last.usage = { ...first.usage, contextTokens: 82000 };
  const usage = latestContextUsage(
    [first, agentMessage("missing", "two"), last],
    selected,
  );
  assert.equal(usage.contextTokens, 82000);
  assert.equal(usage.contextWindow, 100000);
  assert.equal(
    contextUsageProgress(usage.contextTokens, usage.contextWindow),
    0.82,
  );
  assert.equal(
    formatContextUsage(usage, formatter()),
    "82K / 100K tokens · 82.0%",
  );
});
test("contextUsageProgress_handlesMissingInvalidAndOverflowValues", () => {
  assert.equal(contextUsageProgress(null, 100000), null);
  assert.equal(contextUsageProgress(10000, null), null);
  assert.equal(contextUsageProgress(10000, 0), null);
  assert.equal(contextUsageProgress(0, 100000), 0);
  assert.equal(contextUsageProgress(120000, 100000), 1);
  assert.equal(formatCompactTokenCount(1050000, formatter()), "1.05M");
  assert.equal(
    formatContextUsage(
      { contextTokens: null, contextWindow: 100000 },
      formatter(),
    ),
    "No usage data yet",
  );
  assert.equal(
    formatContextUsage(
      { contextTokens: 12000, contextWindow: null },
      formatter(),
    ),
    "Context window unknown, so automatic compaction is off",
  );
});
test("contextUsageFormattingUsesTheRequestedLocale", () => {
  assert.equal(
    formatContextUsage(
      { contextTokens: 82000, contextWindow: 100000 },
      formatter("de-DE"),
    ),
    "82K / 100K tokens · 82,0%",
  );
});
test("enabledModel_requiresBothProviderAndModelEnabled", () => {
  const providers = [
    provider("off", [model("in-disabled-provider")], { isEnabled: false }),
    provider("on", [
      model("available"),
      model("disabled", { isEnabled: false }),
    ]),
  ];
  assert.equal(enabledModel(providers, "available")?.providerId, "on");
  for (const id of ["disabled", "in-disabled-provider", "deleted", null])
    assert.equal(enabledModel(providers, id), null);
});
