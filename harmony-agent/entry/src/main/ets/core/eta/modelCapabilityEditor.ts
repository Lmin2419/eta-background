// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/pages/providers/ProviderModelsTab.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import type { ModelMetadata } from '../modelMetadata';
import { REASONING_EFFORTS, ReasoningEffort, ModelReasoningCapabilities, selectableEfforts } from './protocol/providerTypes';
import { geminiRequestReasoningCapabilities } from './geminiModelCapabilities';
export const editableReasoningEfforts:ReasoningEffort[]=REASONING_EFFORTS.filter(e=>e!=='default');
export interface CapabilityDraft { overrideActive:boolean;enabled:boolean;efforts:ReasoningEffort[]; }
export interface ModelCapabilityEdit {reasoningOverride?:boolean;reasoningCapabilitiesOverride?:ModelReasoningCapabilities;}
function effective(m?:ModelMetadata):ModelReasoningCapabilities|undefined{return m?.reasoningCapabilitiesOverride??m?.reasoningCapabilities;}
export function supportsModelReasoning(m?:ModelMetadata):boolean {return m?.reasoningOverride??m?.reasoning??!!effective(m);}
export function capabilityDraft(m?:ModelMetadata):CapabilityDraft {const cap=effective(m);return {overrideActive:m?.reasoningOverride!==undefined||!!m?.reasoningCapabilitiesOverride,enabled:supportsModelReasoning(m),efforts:[...new Set([...(cap?selectableEfforts(cap):[]),'default' as ReasoningEffort])]};}
export function toggleReasoning(d:CapabilityDraft,enabled:boolean):CapabilityDraft{return {...d,overrideActive:true,enabled};}
export function toggleEffort(d:CapabilityDraft,e:ReasoningEffort):CapabilityDraft{return {...d,overrideActive:true,efforts:d.efforts.includes(e)?d.efforts.filter(v=>v!==e):d.efforts.concat(e)};}
export function resetAutomaticReasoning(m?:ModelMetadata):CapabilityDraft {return capabilityDraft(m?{...m,reasoningOverride:undefined,reasoningCapabilitiesOverride:undefined}:undefined);}
export function updatedCapabilities(d:CapabilityDraft,m?:ModelMetadata,nativeGemini=false):ModelCapabilityEdit {
  if(!d.overrideActive||nativeGemini)return {reasoningOverride:undefined,reasoningCapabilitiesOverride:undefined};
  const prior=effective(m),canDisable=d.efforts.includes('off');
  return {reasoningOverride:d.enabled,reasoningCapabilitiesOverride:d.enabled?{...prior,supportedEfforts:editableReasoningEfforts.filter(e=>e!=='off'&&d.efforts.includes(e)),defaultEffort:prior?.defaultEffort&&d.efforts.includes(prior.defaultEffort)?prior.defaultEffort:undefined,defaultEnabled:true,mandatory:!canDisable,canDisable}:undefined};
}
export function editorEfforts(d:CapabilityDraft,modelId:string,nativeGemini:boolean):ReasoningEffort[]{return nativeGemini?geminiRequestReasoningCapabilities(modelId).supportedEfforts:editableReasoningEfforts;}
export function compactModelTokens(count:number):string {return count>=1000000?(count/1000000).toLocaleString('en-US',{maximumFractionDigits:2})+'M':count>=1000?(count/1000).toLocaleString('en-US',{maximumFractionDigits:1})+'K':String(count);}
