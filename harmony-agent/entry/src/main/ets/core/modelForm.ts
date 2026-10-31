import { ProviderHeaderDraft } from './eta/providerConfigDraft';
import { ModelMetadata } from './modelMetadata';
import { fetchModels, HttpTransport, ModelSettings, testModel } from './model';
import { ModelProfile, modelCatalog } from './profiles';

export interface ModelDraft extends ModelSettings {
  metadata?:ModelMetadata[];
  systemPrompt?:string;isEnabled?:boolean;isBuiltIn?:boolean;headerDrafts?:ProviderHeaderDraft[];
  id: string;
  name: string;
  rememberKey: boolean;
  allowInsecureHttp: boolean;
  models: string[];
  streaming: boolean;
}
export function copyModelDraft(draft: ModelDraft):ModelDraft { return {...draft,models:draft.models.slice(),metadata:draft.metadata?JSON.parse(JSON.stringify(draft.metadata)):undefined,extraHeaders:{...draft.extraHeaders},headerDrafts:draft.headerDrafts?.map(h=>({...h}))}; }
export function emptyModelDraft(): ModelDraft {
  return {id:'',name:'我的模型',baseUrl:'',model:'',apiKey:'',rememberKey:false,screenshots:true,allowInsecureHttp:false,models:[],streaming:true,protocol:'chat_completions',isEnabled:true,systemPrompt:''};
}
export function profileDraft(profile: ModelProfile, key: string): ModelDraft {
  return {isBuiltIn:profile.isBuiltIn,id:profile.id,name:profile.name,baseUrl:profile.baseUrl,model:profile.model,apiKey:key,
    rememberKey:Boolean(profile.secretRef),screenshots:profile.screenshots,allowInsecureHttp:profile.allowInsecureHttp,models:modelCatalog(profile.models,profile.model),streaming:profile.streaming!==false,protocol:profile.protocol||'chat_completions',maxOutputTokens:profile.maxOutputTokens,reasoningEffort:profile.reasoningEffort,metadata:profile.metadata,extraHeaders:{...profile.extraHeaders},systemPrompt:profile.systemPrompt||'',isEnabled:profile.isEnabled!==false};
}
/** UI callbacks may echo the current text on remount/blur. Only a real destination edit invalidates its key. */
export function editModelAddress(draft: ModelDraft, address: string): ModelDraft {
  if (draft.baseUrl === address) return draft;
  if (draft.baseUrl.trim().replace(/\/+$/,'') === address.trim().replace(/\/+$/,'')) return {...draft,baseUrl:address};
  return {...draft,baseUrl:address,apiKey:'',models:[],metadata:[],extraHeaders:{},headerDrafts:[]};
}
export function modelSnapshot(draft: ModelDraft): ModelSettings {
  return {baseUrl:draft.baseUrl,model:draft.model,apiKey:draft.apiKey,screenshots:draft.screenshots,allowInsecureHttp:draft.allowInsecureHttp,streaming:draft.streaming,protocol:draft.protocol,maxOutputTokens:draft.maxOutputTokens,reasoningEffort:draft.reasoningEffort,reasoningCapabilities:draft.reasoningCapabilities,extraHeaders:{...draft.extraHeaders},systemPrompt:draft.systemPrompt,providerId:draft.id};
}
export function sameModelRequest(draft: ModelDraft, request: ModelSettings): boolean {
  return draft.baseUrl===request.baseUrl && draft.apiKey===request.apiKey && draft.model===request.model && draft.allowInsecureHttp===request.allowInsecureHttp && (draft.protocol||'chat_completions')===(request.protocol||'chat_completions') && draft.streaming===request.streaming && JSON.stringify(draft.extraHeaders||{})===JSON.stringify(request.extraHeaders||{});
}
export interface ModelProbeResult { message: string; ids: string[];metadata?:ModelMetadata[] }
/** Read-only diagnostics: snapshot before any await, return result data, never assign to the form. */
export async function probeModel(list: boolean, settings: ModelSettings, io: HttpTransport): Promise<ModelProbeResult> {
  const request: ModelSettings = {...settings};
  if (list) {
    const result=await fetchModels(request,io);
    return {ids:result.ids,metadata:result.metadata,message:'已获取 '+result.ids.length+' 个模型'+(result.partial?'（部分目录）':'')+'，请选择或手填模型 ID'};
  }
  return {ids:[],message:await testModel(request,io)};
}
