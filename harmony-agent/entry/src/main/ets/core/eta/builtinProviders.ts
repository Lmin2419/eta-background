// Ported from app/src/main/kotlin/io/github/mangi/eta/data/provider/BuiltinProviders.kt,
// data/repository/ProviderRepository.kt @ 3b1794d. Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import type { ModelProfile, ProfileDocument } from '../profiles';
import { officialModels, officialModelsAddedSince, catalogSource, OFFICIAL_CATALOG_REVISION } from './officialModelCatalog';
export const DEFAULT_ETA_SYSTEM_PROMPT = '你是随身助手，运行在 HarmonyOS 设备上的 AI 助手。你可以回答问题、与用户交流，也可以通过当前可用的工具了解设备情况并执行操作。回答使用用户的语言，简洁、直接、自然。';
export const BUILTIN_PROVIDERS: ModelProfile[] = [
  ['builtin-openai','OpenAI','https://api.openai.com/v1','responses'],
  ['builtin-anthropic','Anthropic','https://api.anthropic.com','anthropic'],
  ['builtin-dashscope','阿里百炼','https://dashscope.aliyuncs.com/compatible-mode/v1','chat_completions'],
  ['builtin-deepseek','DeepSeek','https://api.deepseek.com','chat_completions'],
  ['builtin-kimi','Kimi','https://api.moonshot.cn/v1','chat_completions'],
  ['builtin-mimo','MiMo','https://api.xiaomimimo.com/v1','chat_completions'],
  ['builtin-minimax','MiniMax','https://api.minimaxi.com/v1','chat_completions'],
  ['builtin-stepfun','StepFun','https://api.stepfun.com/v1','chat_completions'],
  ['builtin-siliconflow','硅基流动','https://api.siliconflow.cn/v1','chat_completions'],
  ['builtin-openrouter','OpenRouter','https://openrouter.ai/api/v1','chat_completions'],
  ['builtin-xai','xAI','https://api.x.ai/v1','chat_completions'],
  ['builtin-gemini','Gemini','https://generativelanguage.googleapis.com/v1beta','gemini']
].map(([id,name,baseUrl,protocol])=>({id,name,baseUrl,protocol:protocol as ModelProfile['protocol'],model:'',models:[],metadata:[],screenshots:true,
  allowInsecureHttp:false,streaming:true,isEnabled:true,isBuiltIn:true,systemPrompt:DEFAULT_ETA_SYSTEM_PROMPT}));
export function seedOfficialModels(p: ModelProfile): ModelProfile {
  if(p.models?.length)return p;const metadata=officialModels(p);return {...p,metadata,models:metadata.map(m=>m.id),model:metadata[0]?.id||''};
}
export function mergeBuiltinProviders(document: ProfileDocument): ProfileDocument {
  const revision=document.officialCatalogRevision||0;
  const profiles=document.profiles.map(p=>{
    if(revision>=OFFICIAL_CATALOG_REVISION)return p;
    const meta=(p.metadata||[]).filter(m=>(p.models||[]).includes(m.id)),source=catalogSource(p);
    let models=meta.slice(),ids=(p.models||[]).slice();
    if(source==='openai'&&revision<2){
      const current=officialModels(p); const remove=meta.filter(m=>m.source==='catalog'&&!current.some(c=>c.id.toLowerCase()===m.id.toLowerCase())).map(m=>m.id);
      ids=ids.filter(id=>!remove.includes(id));models=models.filter(m=>!remove.includes(m.id));
      // A remote catalog or an intentionally cleared connection must stay untouched.
      if(meta.some(m=>m.source==='catalog')&&!meta.some(m=>m.source==='provider')){
        for(const m of officialModelsAddedSince(p,revision))if(!ids.some(id=>id.toLowerCase()===m.id.toLowerCase())){ids.push(m.id);models.push(m);}
      }
    }else if(p.isBuiltIn&&BUILTIN_PROVIDERS.some(b=>b.id===p.id)){
      for(const m of officialModelsAddedSince(p,revision))if(!ids.some(id=>id.toLowerCase()===m.id.toLowerCase())){ids.push(m.id);models.push(m);}
    }
    if(source==='openai'&&revision<3){
      const presets=officialModels(p);models=models.map(m=>m.contextWindow!==undefined||m.source==='custom'?m:{...m,contextWindow:m.catalogContextWindow??(m.source==='catalog'?presets.find(o=>o.id.toLowerCase()===m.id.toLowerCase())?.catalogContextWindow:undefined)});
    }
    // Harmony had no earlier catalog capability serialization: no legacy Kotlin JSON to replace.
    return {...p,models:ids,metadata:models,model:ids.includes(p.model)?p.model:ids[0]||''};
  });
  for(const preset of BUILTIN_PROVIDERS)if(!profiles.some(p=>p.id===preset.id))profiles.push(seedOfficialModels(preset));
  return {...document,profiles,selectedId:document.selectedId||profiles.find(p=>p.isEnabled!==false)?.id||'',officialCatalogRevision:OFFICIAL_CATALOG_REVISION};
}
