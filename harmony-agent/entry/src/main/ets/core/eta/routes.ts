// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/navigation/AppRoute.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
// C routes retain their wire identities for compatibility, but are never offered by Harmony navigation.
export enum AppRouteName {
  Home = "Home",
  Browser = "Browser",
  Terminal = "Terminal",
  Tools = "Tools",
  WebToolsSettings = "WebToolsSettings",
  Skills = "Skills",
  Subagents = "Subagents",
  Characters = "Characters",
  CharacterDetail = "CharacterDetail",
  CharacterEditor = "CharacterEditor",
  CharacterPersona = "CharacterPersona",
  CharacterMemory = "CharacterMemory",
  Permissions = "Permissions",
  SystemEnhance = "SystemEnhance",
  Settings = "Settings",
  AppearanceSettings = "AppearanceSettings",
  SpeechSettings = "SpeechSettings",
  SpeechRecognition = "SpeechRecognition",
  SpeechSynthesis = "SpeechSynthesis",
  SpeechOss = "SpeechOss",
  DataBackup = "DataBackup",
  Memory = "Memory",
  LinuxEnvironment = "LinuxEnvironment",
  SharedFolders = "SharedFolders",
  Workspace = "Workspace",
  LinuxFiles = "LinuxFiles",
  ModelProviders = "ModelProviders",
  CommunityCatalog = "CommunityCatalog",
  CommunityCatalogProvider = "CommunityCatalogProvider",
  McpServers = "McpServers",
  McpServerDetail = "McpServerDetail",
  ModelProviderDetail = "ModelProviderDetail",
  ModelProviderNew = "ModelProviderNew",
}
export enum NewProviderType {
  OpenAiCompatible = "OpenAiCompatible",
  Anthropic = "Anthropic",
  Gemini = "Gemini",
}
export type RouteCapability = "A" | "B" | "C";
export const MANUAL_BROWSER_SESSION_KEY = "browser:manual";
const ROUTE_TYPE_PREFIX = "io.github.mangi.eta.ui.navigation.AppRoute.";
export const APP_ROUTE_NAMES: readonly AppRouteName[] = [
  AppRouteName.Home,
  AppRouteName.Browser,
  AppRouteName.Terminal,
  AppRouteName.Tools,
  AppRouteName.WebToolsSettings,
  AppRouteName.Skills,
  AppRouteName.Subagents,
  AppRouteName.Characters,
  AppRouteName.CharacterDetail,
  AppRouteName.CharacterEditor,
  AppRouteName.CharacterPersona,
  AppRouteName.CharacterMemory,
  AppRouteName.Permissions,
  AppRouteName.SystemEnhance,
  AppRouteName.Settings,
  AppRouteName.AppearanceSettings,
  AppRouteName.SpeechSettings,
  AppRouteName.SpeechRecognition,
  AppRouteName.SpeechSynthesis,
  AppRouteName.SpeechOss,
  AppRouteName.DataBackup,
  AppRouteName.Memory,
  AppRouteName.LinuxEnvironment,
  AppRouteName.SharedFolders,
  AppRouteName.Workspace,
  AppRouteName.LinuxFiles,
  AppRouteName.ModelProviders,
  AppRouteName.CommunityCatalog,
  AppRouteName.CommunityCatalogProvider,
  AppRouteName.McpServers,
  AppRouteName.McpServerDetail,
  AppRouteName.ModelProviderDetail,
  AppRouteName.ModelProviderNew,
];

export function routeCapability(name: AppRouteName): RouteCapability {
  switch (name) {
    case AppRouteName.Terminal:
    case AppRouteName.LinuxEnvironment:
    case AppRouteName.SharedFolders:
    case AppRouteName.LinuxFiles:
    case AppRouteName.SystemEnhance:
      return "C";
    case AppRouteName.Browser:
    case AppRouteName.Workspace:
    case AppRouteName.SpeechSettings:
    case AppRouteName.SpeechRecognition:
    case AppRouteName.SpeechSynthesis:
    case AppRouteName.SpeechOss:
      return "B";
    default:
      return "A";
  }
}
export function routeHidden(name: AppRouteName): boolean {
  return routeCapability(name) === "C";
}

/** Immutable value identity replacing Kotlin data object / data class. */
export class AppRoute {
  readonly name: AppRouteName;
  readonly argument: string | null;
  readonly providerType: NewProviderType | null;
  constructor(
    name: AppRouteName,
    argument: string | null = null,
    providerType: NewProviderType | null = null,
  ) {
    this.name = name;
    this.argument =
      name === AppRouteName.Browser
        ? (argument ?? MANUAL_BROWSER_SESSION_KEY)
        : argument;
    this.providerType = providerType;
    if (!APP_ROUTE_NAMES.includes(name)) throw new Error("Unknown Eta route");
    if (requiresArgument(name) && typeof this.argument !== "string")
      throw new Error("Missing route argument: " + name);
    if (name === AppRouteName.ModelProviderNew && !isProviderType(providerType))
      throw new Error("Missing provider type");
    if (
      !requiresArgument(name) &&
      name !== AppRouteName.CharacterEditor &&
      argument !== null
    )
      throw new Error("Unexpected route argument");
    if (name !== AppRouteName.ModelProviderNew && providerType !== null)
      throw new Error("Unexpected provider type");
  }
}
function requiresArgument(name: AppRouteName): boolean {
  return (
    name === AppRouteName.Browser ||
    name === AppRouteName.CharacterDetail ||
    name === AppRouteName.CharacterMemory ||
    name === AppRouteName.LinuxFiles ||
    name === AppRouteName.CommunityCatalogProvider ||
    name === AppRouteName.McpServerDetail ||
    name === AppRouteName.ModelProviderDetail
  );
}
function isProviderType(
  value: string | null | undefined,
): value is NewProviderType {
  return (
    value === NewProviderType.OpenAiCompatible ||
    value === NewProviderType.Anthropic ||
    value === NewProviderType.Gemini
  );
}
interface RouteWire {
  type: string;
  sessionKey?: string;
  characterId?: string | null;
  distribution?: string;
  catalogId?: string;
  serverId?: string;
  providerId?: string;
  providerType?: string;
}
function toWire(route: AppRoute): RouteWire {
  const out: RouteWire = { type: ROUTE_TYPE_PREFIX + route.name };
  switch (route.name) {
    case AppRouteName.Browser:
      if (route.argument !== MANUAL_BROWSER_SESSION_KEY)
        out.sessionKey = route.argument!;
      break;
    case AppRouteName.CharacterDetail:
    case AppRouteName.CharacterMemory:
      out.characterId = route.argument;
      break;
    case AppRouteName.CharacterEditor:
      if (route.argument !== null) out.characterId = route.argument;
      break;
    case AppRouteName.LinuxFiles:
      out.distribution = route.argument!;
      break;
    case AppRouteName.CommunityCatalogProvider:
      out.catalogId = route.argument!;
      break;
    case AppRouteName.McpServerDetail:
      out.serverId = route.argument!;
      break;
    case AppRouteName.ModelProviderDetail:
      out.providerId = route.argument!;
      break;
    case AppRouteName.ModelProviderNew:
      out.providerType = route.providerType!;
      break;
  }
  return out;
}
function fromWire(wire: RouteWire): AppRoute {
  if (
    !wire ||
    typeof wire !== "object" ||
    typeof wire.type !== "string" ||
    !wire.type.startsWith(ROUTE_TYPE_PREFIX)
  ) {
    throw new Error("Invalid route identity");
  }
  const name = APP_ROUTE_NAMES.find((n) => ROUTE_TYPE_PREFIX + n === wire.type);
  if (!name) throw new Error("Unknown Eta route");
  let argument: string | null = null;
  switch (name) {
    case AppRouteName.Browser:
      argument =
        wire.sessionKey === undefined
          ? MANUAL_BROWSER_SESSION_KEY
          : requiredString(wire.sessionKey);
      break;
    case AppRouteName.CharacterDetail:
    case AppRouteName.CharacterMemory:
      argument = requiredString(wire.characterId);
      break;
    case AppRouteName.CharacterEditor:
      argument =
        wire.characterId == null ? null : requiredString(wire.characterId);
      break;
    case AppRouteName.LinuxFiles:
      argument = requiredString(wire.distribution);
      break;
    case AppRouteName.CommunityCatalogProvider:
      argument = requiredString(wire.catalogId);
      break;
    case AppRouteName.McpServerDetail:
      argument = requiredString(wire.serverId);
      break;
    case AppRouteName.ModelProviderDetail:
      argument = requiredString(wire.providerId);
      break;
  }
  if (
    name === AppRouteName.ModelProviderNew &&
    !isProviderType(wire.providerType)
  )
    throw new Error("Unknown provider type");
  return new AppRoute(
    name,
    argument,
    name === AppRouteName.ModelProviderNew
      ? (wire.providerType as NewProviderType)
      : null,
  );
}
function requiredString(value: string | null | undefined): string {
  if (typeof value !== "string") throw new Error("Invalid route argument");
  return value;
}
export function encodeRoute(route: AppRoute): string {
  return JSON.stringify(toWire(route));
}
export function decodeRoute(encoded: string): AppRoute {
  return fromWire(JSON.parse(encoded) as RouteWire);
}
export function encodeRoutes(routes: readonly AppRoute[]): string {
  return JSON.stringify(routes.map(toWire));
}
export function decodeRoutes(encoded: string): AppRoute[] {
  const values = JSON.parse(encoded) as RouteWire[];
  if (!Array.isArray(values)) throw new Error("Invalid route stack");
  return values.map(fromWire);
}
export function routeEquals(left: AppRoute, right: AppRoute): boolean {
  return (
    left.name === right.name &&
    left.argument === right.argument &&
    left.providerType === right.providerType
  );
}
