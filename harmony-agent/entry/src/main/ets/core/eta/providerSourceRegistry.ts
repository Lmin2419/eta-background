// Ported from app/src/main/kotlin/io/github/mangi/eta/data/provider/ProviderSourceRegistry.kt,
// BuiltinProviders.kt @ 3b1794d. Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
export const builtInProviderSources:Record<string,string>={'builtin-openai':'openai','builtin-anthropic':'anthropic','builtin-dashscope':'bailian','builtin-deepseek':'deepseek','builtin-kimi':'moonshot','builtin-mimo':'mimo','builtin-minimax':'minimax','builtin-stepfun':'stepfun','builtin-siliconflow':'siliconflow','builtin-openrouter':'openrouter','builtin-xai':'xai','builtin-gemini':'gemini'};
export function resolveProviderSource(id:string,baseUrl:string,type='chat_completions'):string{
 if(type==='gemini')return 'gemini';if(builtInProviderSources[id])return builtInProviderSources[id];
 const host=/^https?:\/\/([^/:?#]+)(?::\d+)?(?:\/|$)/i.exec(baseUrl.trim())?.[1].toLowerCase()||'';
 const sources:Record<string,string>={'api.openai.com':'openai','api.x.ai':'xai','cli-chat-proxy.grok.com':'xai','api.anthropic.com':'anthropic','api.deepseek.com':'deepseek','api.moonshot.cn':'moonshot','api.moonshot.ai':'moonshot','api.xiaomimimo.com':'mimo','api.minimax.io':'minimax','api.minimaxi.com':'minimax','api.stepfun.com':'stepfun','dashscope.aliyuncs.com':'bailian','api.siliconflow.cn':'siliconflow','openrouter.ai':'openrouter'};
 return sources[host]||(host.endsWith('.maas.aliyuncs.com')?'bailian':type==='anthropic'?'anthropic':'custom');
}
