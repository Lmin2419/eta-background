// Ported from app/src/main/kotlin/io/github/mangi/eta/agent/model/ProviderUrls.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
export const normalizeBaseUrl = (url: string): string => url.trim().replace(/\/+$/, '');
const append = (url: string, path: string): string => normalizeBaseUrl(url) + '/' + path.replace(/^\/+/, '');
export const ProviderUrls = {
  openAiChatCompletionsUrl: (url: string): string => append(url, 'chat/completions'),
  openAiResponsesUrl: (url: string): string => append(url, 'responses'), openAiModelsUrl: (url: string): string => append(url, 'models'),
  anthropicMessagesUrl: (url: string): string => append(url, 'v1/messages'), anthropicModelsUrl: (url: string): string => append(url, 'v1/models'),
  geminiInteractionsUrl: (url: string): string => append(url, 'interactions'), geminiModelsUrl: (url: string): string => append(url, 'models'),
};
