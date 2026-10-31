// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/model/AgentModelPickerUiState.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { ModelProfile, modelCatalog } from "../profiles";
import { resolveProviderSource } from "./providerSourceRegistry";
import { PickerProvider } from "./modelPicker";
import { implementedProtocol } from './protocol/foundationProviderBridge';
export function foundationPickerProviders(
  profiles: ModelProfile[],
): PickerProvider[] {
  return profiles.map(
    (p, index): PickerProvider => ({
      id: p.id,
      name: p.name,
      sourceType: resolveProviderSource(p.id,p.baseUrl,p.protocol),
      isEnabled: p.isEnabled!==false && implementedProtocol(p.protocol),
      sortOrder: index,
      authMode: "api_key",
      // 0.9 allows anonymous OpenAI-compatible endpoints. A saved profile is explicitly
      // configured; never read or copy ASSET keys into UI projections to decide visibility.
      hasApiKey: true,
      models: modelCatalog(p.models, p.model).map((model, order) => ({
        id: p.id + ":" + model,
        modelId: model,
        displayName: p.metadata?.find((m) => m.id === model)?.name || model,
        isEnabled:p.metadata?.find(m=>m.id===model)?.enabled!==false,
        sortOrder: order,
        contextWindow:
          p.metadata?.find((m) => m.id === model)?.contextWindow ?? null,
      })),
    }),
  );
}
