// Ported from app/src/main/kotlin/io/github/mangi/eta/agent/model/GeminiInteractionsProvider.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import {JsonObject,ProviderRequest,ProviderRunControl,ProviderEventSink,effectiveRequest,string} from './providerTypes';import {ProviderStreamingTransport} from './openAiResponsesProvider';
import {GeminiInteractionsStream} from './geminiInteractionsStream';import {buildGeminiRequest} from './geminiInteractionsRequest';import {pendingGeminiSuggestions} from './geminiEphemeralState';import {mergeProviderHeaders} from './providerRequestHeaders';import {ProviderUrls} from './providerUrls';
export async function completeGeminiInteractions(input:ProviderRequest,control:ProviderRunControl,transport:ProviderStreamingTransport,emit:ProviderEventSink=()=>{}):Promise<JsonObject>{
 const request=effectiveRequest(input),c=request.config,headers:Record<string,string>={};if(/[\r\n\u0000]/.test(c.apiKey))throw Error('API Key 包含无效字符');if(c.apiKey.trim())headers['x-goog-api-key']=c.apiKey;
 mergeProviderHeaders(headers,c.baseUrl,c.customHeaders||[],request.sessionId);for(const key of Object.keys(headers))if(['content-type','accept'].includes(key.toLowerCase()))delete headers[key];headers['Content-Type']='application/json; charset=utf-8';headers.Accept='text/event-stream';
 const body=buildGeminiRequest(c,request.messages,request.tools),previous=request.messages.slice().reverse().find(m=>m.role==='assistant'),pending=previous?pendingGeminiSuggestions(previous):[],parser=new GeminiInteractionsStream(control,emit,pending);
 control.check();emit({type:'RequestStarted'});await transport.stream(ProviderUrls.geminiInteractionsUrl(c.baseUrl),headers,JSON.stringify(body),chunk=>parser.feed(chunk),httpCode=>{control.check();emit({type:'ResponseHeaders',httpCode});});control.check();const result=parser.finish();emit({type:'Completed',reason:string(result.finish_reason)});return result;
}
