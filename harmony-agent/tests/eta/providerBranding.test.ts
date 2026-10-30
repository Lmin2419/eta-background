// Ported from app/src/test/kotlin/io/github/mangi/eta/ui/components/ProviderBrandingTest.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import test from "node:test";
import assert from "node:assert/strict";
import {
  modelBrandLogoRes,
  modelOrProviderBrandLogoRes,
} from "../../entry/src/main/ets/core/eta/providerBranding";
test("resolvesKnownModelFamiliesFromModelIds", () => {
  for (const [id, logo] of [
    ["gpt-5.6-sol", "provider_logo_openai"],
    ["openai/o3", "provider_logo_openai"],
    ["vendor/codex-mini", "provider_logo_openai"],
    ["claude-sonnet-5", "model_logo_claude"],
    ["kimi-k2.6", "provider_logo_kimi"],
    ["kimi/kimi-k3", "provider_logo_kimi"],
    ["moonshot-v1-128k", "provider_logo_kimi"],
    ["qwen3.7-plus", "model_logo_qwen"],
    ["QWQ-32B", "model_logo_qwen"],
    ["deepseek/deepseek-v4-pro", "provider_logo_deepseek"],
    ["mimo-v2.5-pro", "provider_logo_mimo"],
    ["MiniMax-M3", "provider_logo_minimax"],
    ["step-3.7-flash", "provider_logo_stepfun"],
    ["glm-5.2", "model_logo_zai"],
    ["vendor/glm-4.7", "model_logo_zai"],
    ["chatglm3", "model_logo_chatglm"],
    ["glm-3-turbo", "model_logo_chatglm"],
    ["google/gemini-3.6-flash", "model_logo_gemini"],
    ["google/gemma-4", "model_logo_gemma"],
    ["x-ai/grok-5", "model_logo_grok"],
    ["meta-llama/llama-5", "model_logo_meta"],
    ["mistralai/mixtral-8x22b", "model_logo_mistral"],
    ["doubao-2.0-pro", "model_logo_doubao"],
    ["tencent/hunyuan-turbo", "model_logo_hunyuan"],
    ["01-ai/yi-large", "model_logo_yi"],
  ])
    assert.equal(modelBrandLogoRes(id), logo, id);
});
test("avoidsShortNameAndOpaqueDeploymentFalsePositives", () => {
  for (const id of [
    "",
    "example/chat-model",
    "yield-model",
    "mimosa-v1",
    "step-by-step-model",
    "ep-20260817",
    "glimmer-2",
    "codexify-v1",
    "gptx-v1",
  ])
    assert.equal(modelBrandLogoRes(id), null, id);
});
test("prefersModelBrandAndFallsBackToProviderBrand", () => {
  assert.equal(
    modelOrProviderBrandLogoRes("kimi-k2.6", "bailian"),
    "provider_logo_kimi",
  );
  assert.equal(
    modelOrProviderBrandLogoRes("qwen3.7-plus", "openrouter"),
    "model_logo_qwen",
  );
  assert.equal(
    modelOrProviderBrandLogoRes("vendor/unknown-chat", "bailian"),
    "provider_logo_bailian",
  );
  assert.equal(
    modelOrProviderBrandLogoRes("vendor/unknown-chat", "custom"),
    null,
  );
});
