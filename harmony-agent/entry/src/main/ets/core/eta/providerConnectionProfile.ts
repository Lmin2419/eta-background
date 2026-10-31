// Ported from app/src/main/kotlin/io/github/mangi/eta/data/provider/ProviderConnectionProfile.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11). Pure target guard; OAuth remains hidden (C).
export interface ProviderConnectionProfile {authMode:string;baseUrl:string;endpointMode:string;sourceType:string;modelsUrl:string;inferenceUrl:string;}
export function connectionProfile(authMode:string,version:string):ProviderConnectionProfile|null {
  if(authMode==='api_key')return null;
  const codex=authMode==='openai_chatgpt';if(!codex&&authMode!=='xai_grok')throw Error('不支持的服务商认证方式');
  const baseUrl=codex?'https://chatgpt.com/backend-api/codex':'https://cli-chat-proxy.grok.com/v1',endpointMode=codex?'responses':'chat_completions';
  return {authMode,baseUrl,endpointMode,sourceType:codex?'openai':'xai',modelsUrl:baseUrl+'/models'+(codex?'?client_version='+encodeURIComponent(version.split('-')[0].split('+')[0]):''),inferenceUrl:baseUrl+(codex?'/responses':'/chat/completions')};
}
export function requireConnectionTarget(profile:ProviderConnectionProfile,url:string):void {
  if(url!==profile.modelsUrl&&url!==profile.inferenceUrl)throw Error('订阅凭据只能用于对应服务商的官方模型接口');
}
