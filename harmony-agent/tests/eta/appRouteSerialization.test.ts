// Ported from app/src/test/kotlin/io/github/mangi/eta/ui/navigation/AppRouteSerializationTest.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import test from "node:test";
import assert from "node:assert/strict";
import {
  AppRoute,
  AppRouteName as R,
  NewProviderType,
  encodeRoutes,
  decodeRoutes,
  decodeRoute,
} from "../../entry/src/main/ets/core/eta/routes";

test("legacyBrowserRouteRestoresTheManualPage", () => {
  const restored = decodeRoute(
    '{"type":"io.github.mangi.eta.ui.navigation.AppRoute.Browser"}',
  );
  assert.deepEqual(restored, new AppRoute(R.Browser));
});
test("allRoutesRoundTripWithMiuixNavJsonConfiguration", () => {
  const routes: AppRoute[] = [
    new AppRoute(R.Home),
    new AppRoute(R.Browser),
    new AppRoute(R.Browser, "conversation-a"),
    new AppRoute(R.Browser, "conversation-b"),
    new AppRoute(R.Terminal),
    new AppRoute(R.Tools),
    new AppRoute(R.WebToolsSettings),
    new AppRoute(R.Skills),
    new AppRoute(R.Subagents),
    new AppRoute(R.Characters),
    new AppRoute(R.CharacterDetail, "character"),
    new AppRoute(R.CharacterEditor),
    new AppRoute(R.CharacterEditor, "character"),
    new AppRoute(R.CharacterPersona),
    new AppRoute(R.CharacterMemory, "character"),
    new AppRoute(R.Permissions),
    new AppRoute(R.SystemEnhance),
    new AppRoute(R.Settings),
    new AppRoute(R.AppearanceSettings),
    new AppRoute(R.SpeechSettings),
    new AppRoute(R.SpeechRecognition),
    new AppRoute(R.SpeechSynthesis),
    new AppRoute(R.SpeechOss),
    new AppRoute(R.DataBackup),
    new AppRoute(R.Memory),
    new AppRoute(R.LinuxEnvironment),
    new AppRoute(R.SharedFolders),
    new AppRoute(R.Workspace),
    new AppRoute(R.LinuxFiles, "alpine"),
    new AppRoute(R.ModelProviders),
    new AppRoute(R.CommunityCatalog),
    new AppRoute(R.CommunityCatalogProvider, "provider"),
    new AppRoute(R.McpServers),
    new AppRoute(R.McpServerDetail, "mcp-server"),
    new AppRoute(R.ModelProviderDetail, "provider"),
    new AppRoute(R.ModelProviderNew, null, NewProviderType.OpenAiCompatible),
    new AppRoute(R.ModelProviderNew, null, NewProviderType.Anthropic),
    new AppRoute(R.ModelProviderNew, null, NewProviderType.Gemini),
  ];
  assert.deepEqual(decodeRoutes(encodeRoutes(routes)), routes);
});
