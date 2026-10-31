// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/model/AgentModelPickerUiState.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { ModelProfile, modelCatalog } from "../profiles";
import { PickerProvider } from "./modelPicker";
export function foundationPickerProviders(
  profiles: ModelProfile[],
): PickerProvider[] {
  return profiles.map(
    (p, index): PickerProvider => ({
      id: p.id,
      name: p.name,
      sourceType: "custom",
      isEnabled: true,
      sortOrder: index,
      authMode: "api_key",
      // 0.9 allows anonymous OpenAI-compatible endpoints. A saved profile is explicitly
      // configured; never read or copy ASSET keys into UI projections to decide visibility.
      hasApiKey: true,
      models: modelCatalog(p.models, p.model).map((model, order) => ({
        id: p.id + ":" + model,
        modelId: model,
        displayName: p.metadata?.find((m) => m.id === model)?.name || model,
        isEnabled: true,
        sortOrder: order,
        contextWindow:
          p.metadata?.find((m) => m.id === model)?.contextWindow ?? null,
      })),
    }),
  );
}
