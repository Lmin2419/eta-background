// Ported from app/src/main/kotlin/io/github/mangi/eta/agent/model/GeminiEphemeralState.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import {JsonObject,array,copy,object} from './providerTypes';
export const GEMINI_STEPS='_eta_gemini_steps',GEMINI_RUN_START='_eta_gemini_run_start',GEMINI_SUGGESTIONS='_eta_gemini_pending_suggestions';
export function geminiSteps(m:JsonObject):JsonObject[]|null{return Array.isArray(m[GEMINI_STEPS])?m[GEMINI_STEPS] as JsonObject[]:null;}
export function attachGeminiSteps(m:JsonObject,steps:JsonObject[]):void{m[GEMINI_STEPS]=copy(steps);}
export function pendingGeminiSuggestions(m:JsonObject):unknown[]{return array(m[GEMINI_SUGGESTIONS]);}
export function attachGeminiSuggestions(m:JsonObject,suggestions:unknown[]):void{if(suggestions.length)m[GEMINI_SUGGESTIONS]=copy(suggestions);}
export function copyGeminiState(source:JsonObject,target:JsonObject):void{const steps=geminiSteps(source);if(steps)attachGeminiSteps(target,steps);attachGeminiSuggestions(target,pendingGeminiSuggestions(source));}
export function markGeminiRunStart(m:JsonObject):void{m[GEMINI_RUN_START]=true;}
export function pendingGeminiToolResponse(messages:JsonObject[]):boolean{for(let i=messages.length-1;i>=0;i--)if(messages[i].role==='assistant')return !!geminiSteps(messages[i])?.some(s=>s.type==='function_call');return false;}
export function withoutGeminiState(m:JsonObject):JsonObject{if(![GEMINI_STEPS,GEMINI_RUN_START,GEMINI_SUGGESTIONS].some(k=>Object.prototype.hasOwnProperty.call(m,k)))return m;const result=copy(m);for(const key of [GEMINI_STEPS,GEMINI_RUN_START,GEMINI_SUGGESTIONS])delete result[key];return result;}
