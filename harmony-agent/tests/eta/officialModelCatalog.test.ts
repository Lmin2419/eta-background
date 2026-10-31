// Ported from app/src/test/kotlin/io/github/mangi/eta/data/provider/{BuiltinProvidersTest,OfficialModelCatalogTest,GeminiModelCapabilitiesTest}.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11). 2 + 4 + 3 original cases; no skipped fixtures.
import test from 'node:test';import assert from 'node:assert/strict';
import { BUILTIN_PROVIDERS } from '../../entry/src/main/ets/core/eta/builtinProviders';
import { officialModels,officialModelsAddedSince,OFFICIAL_CATALOG_REVISION } from '../../entry/src/main/ets/core/eta/officialModelCatalog';
import { resolveProviderSource } from '../../entry/src/main/ets/core/eta/providerSourceRegistry';
import { geminiReasoningCapabilities,geminiSupportsHostedSearch } from '../../entry/src/main/ets/core/eta/geminiModelCapabilities';
import { selectableEfforts,normalizeEffort } from '../../entry/src/main/ets/core/eta/protocol/providerTypes';
const provider=(id:string)=>BUILTIN_PROVIDERS.find(p=>p.id==='builtin-'+id)!;
const models=(id:string)=>officialModels(provider(id));
const model=(p:string,id:string)=>models(p).find(m=>m.id===id)!;
test('builtInsKeepStablePresetMetadataWithoutInliningModels',()=>{
 assert.equal(provider('openai').protocol,'responses');assert.equal(provider('anthropic').protocol,'anthropic');
 for(const id of ['openai','dashscope','mimo','minimax','stepfun'])assert.equal(provider(id).models!.length,0);
 assert.equal(provider('kimi').baseUrl,'https://api.moonshot.cn/v1');
 for(const [id,source] of [['dashscope','bailian'],['kimi','moonshot'],['mimo','mimo'],['minimax','minimax'],['stepfun','stepfun'],['xai','xai']])assert.equal(resolveProviderSource(provider(id).id,provider(id).baseUrl,provider(id).protocol),source);
 assert.equal(provider('xai').baseUrl,'https://api.x.ai/v1');
 // Hosted search is deliberately absent from Harmony's production profile; protocol default is false.
 assert.equal(Object.prototype.hasOwnProperty.call(provider('openai'),'hostedWebSearchEnabled'),false);
});
test('xaiBrandingUsesExactOfficialHosts',()=>{for(const host of ['api.x.ai','cli-chat-proxy.grok.com'])assert.equal(resolveProviderSource('custom','https://'+host+'/v1'),'xai');assert.equal(resolveProviderSource('custom','https://api.x.ai.example.invalid/v1'),'custom');});
test('otherCatalogWindowsRemainMetadataUntilTheUserConfiguresAWindow',()=>{const m=models('deepseek')[0];assert.ok(m.catalogContextWindow!>0);assert.equal(m.contextWindow,undefined);assert.equal({...m,contextWindow:64000}.contextWindow,64000);});
test('openAiPresetsHaveTheDocumentedWindowConfiguredForRuntime',()=>{const ms=models('openai');assert.deepEqual(new Set(ms.map(m=>m.id)),new Set(['gpt-6.1-sol','gpt-6-luna','gpt-6-astra']));for(const m of ms){assert.equal(m.catalogContextWindow,1050000);assert.equal(m.contextWindow,1050000);assert.equal({...m,contextWindow:64000}.contextWindow,64000);}});
test('revisionOnlyOffersNewModelsAndEveryModelIdIsUnique',()=>{for(const p of BUILTIN_PROVIDERS){const ms=officialModels(p),added=officialModelsAddedSince(p,0);assert.equal(ms.length,new Set(ms.map(m=>m.id.toLowerCase())).size);assert.deepEqual(ms.map(m=>m.sortOrder),ms.map((_,i)=>i));assert.ok(ms.every(m=>m.source==='catalog'));assert.ok(added.every(a=>ms.some(m=>m.id===a.id)));assert.deepEqual(officialModelsAddedSince(p,OFFICIAL_CATALOG_REVISION),[]);}});
test('newPresetsUseCurrentPlatformIdsAndCapabilities',()=>{
 assert.deepEqual(new Set(models('openai').map(m=>m.id)),new Set(['gpt-6.1-sol','gpt-6-luna','gpt-6-astra']));assert.equal(model('openai','gpt-6.1-sol').catalogContextWindow,1050000);assert.equal(model('openai','gpt-6-astra').catalogContextWindow,1050000);
 assert.equal(model('anthropic','claude-opus-5-5').toolCall,true);assert.equal(model('dashscope','qwen3.8-flash').vision,true);assert.equal(model('dashscope','kimi-k3').inputModalities!.includes('video'),false);
 assert.equal(model('deepseek','deepseek-flash').vision,true);assert.equal(models('deepseek').some(m=>m.id==='deepseek-v4-flash'),false);assert.equal(models('kimi').some(m=>m.id==='kimi-k2.5'),false);
 assert.ok(model('mimo','mimo-v2.6-pro').inputModalities!.includes('audio'));assert.ok(model('minimax','MiniMax-M3').inputModalities!.includes('video'));assert.equal(model('stepfun','step-5-preview').catalogContextWindow,1000000);
});
test('nativeProxyIdentityDoesNotChangeExistingCompatibleConnections',()=>{assert.equal(resolveProviderSource('proxy','https://proxy.example/v1beta','gemini'),'gemini');assert.equal(resolveProviderSource('old','https://generativelanguage.googleapis.com/v1beta/openai','chat_completions'),'custom');});
test('currentPresetsExposeOnlyDocumentedThinkingLevelsAndCannotDisableThinking',()=>{
 assert.equal(provider('gemini').protocol,'gemini');assert.equal(Object.prototype.hasOwnProperty.call(provider('gemini'),'hostedWebSearchEnabled'),false);
 const ms=models('gemini');assert.deepEqual(ms.map(m=>m.id),['gemini-3.8-flash','gemini-4-argon','gemini-3.5-flash-lite']);
 for(const m of ms){assert.equal(m.contextWindow,1048576);assert.ok(m.vision&&m.toolCall);assert.ok(geminiSupportsHostedSearch(m.id));const cap=m.reasoningCapabilities!;assert.ok(cap.mandatory);assert.equal(selectableEfforts(cap).includes('off'),false);assert.equal(normalizeEffort(cap,'off'),'default');}
 assert.deepEqual(ms[0].reasoningCapabilities!.supportedEfforts,['low','medium','high']);assert.equal(ms[2].reasoningCapabilities!.defaultEffort,'minimal');assert.equal(ms[2].reasoningCapabilities!.supportedEfforts[0],'minimal');
});
test('searchCapabilitiesRequireConfirmedModelIdentity',()=>{for(const id of ['gemini-4-argon','gemini-3.8-flash','gemini-3.6-flash','gemini-3.5-flash-lite','gemini-3.1-pro-preview','gemini-3-flash-preview','gemini-3-pro-preview'])assert.ok(geminiSupportsHostedSearch(' models/'+id+' '));for(const id of ['gemini-4-unknown','gemini-3.8-flash-tts','gemini-3.8-live','gemini-2.5-pro']){assert.equal(geminiSupportsHostedSearch(id),false);assert.equal(geminiReasoningCapabilities(id),null);}});
