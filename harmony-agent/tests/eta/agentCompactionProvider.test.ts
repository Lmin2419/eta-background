// Ported from app/src/test/kotlin/io/github/mangi/eta/agent/model/AgentCompactionProviderTest.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11). All 3 deferred E05b cases; full source tool fixture.
import test from 'node:test';import assert from 'node:assert/strict';import {createServer} from 'node:http';import {AddressInfo} from 'node:net';import {readFileSync} from 'node:fs';import {createHash} from 'node:crypto';
import {completeAnthropicMessages} from '../../entry/src/main/ets/core/eta/protocol/anthropicMessagesProvider';
import {completeOpenAiResponses} from '../../entry/src/main/ets/core/eta/protocol/openAiResponsesProvider';
import {completeOpenAiChatCompletions} from '../../entry/src/main/ets/core/eta/protocol/openAiChatCompletionsProvider';
import {JsonObject,ProviderPurpose} from '../../entry/src/main/ets/core/eta/protocol/providerTypes';
import {AgentModelFailure} from '../../entry/src/main/ets/core/eta/protocol/agentModelFailure';
import {personaMessage,projectRoleplayMessages} from '../../entry/src/main/ets/core/eta/context/roleplayRequestProjection';
import {RunControl} from '../../entry/src/main/ets/core/types';import {NodeProviderTransport} from './support/providerHttpFixture';
function tools():JsonObject[]{const bytes=readFileSync(new URL('./support/compactionToolsFixture.json',import.meta.url)),proof=JSON.parse(readFileSync(new URL('./support/compactionToolsFixture.provenance.json',import.meta.url),'utf8'));assert.equal(createHash('sha256').update(bytes).digest('hex'),proof.sha256);const rows=JSON.parse(bytes.toString());assert.equal(rows.length,proof.toolCount);assert.equal(rows.length,57);return rows;}
async function withRejection(block:(url:string,captured:()=>JsonObject)=>Promise<void>){let body:JsonObject={};const server=createServer(async(req,res)=>{let raw='';for await(const c of req)raw+=String(c);body=JSON.parse(raw);res.writeHead(400,{'Content-Type':'application/json'});res.end('{"error":{"code":"fixture_rejected"}}');});await new Promise<void>(r=>server.listen(0,'127.0.0.1',r));try{await block('http://127.0.0.1:'+(server.address() as AddressInfo).port,()=>body);}finally{server.closeAllConnections();await new Promise<void>(r=>server.close(()=>r()));}}
async function isolated(purpose:ProviderPurpose){for(const complete of [completeOpenAiChatCompletions,completeOpenAiResponses,completeAnthropicMessages])await withRejection(async(url,captured)=>{
 await assert.rejects(complete({config:{baseUrl:url,apiKey:'fixture',model:'fixture',openAiEndpointMode:complete===completeOpenAiResponses?'responses':'chat_completions',systemPrompt:'总结规则',hostedWebSearchEnabled:true,extraBodyJson:'{"tools":[{"type":"web_search"}],"input":"BAD_INPUT"}',customBody:[{key:'messages',value:'BAD_MESSAGES'}]},messages:[{role:'user',content:'总结以下历史'}],tools:tools(),sessionId:'',purpose},new RunControl(),new NodeProviderTransport(),()=>{}),AgentModelFailure);
 const body=captured(),raw=JSON.stringify(body);assert.ok(body);assert.equal(body.tools,undefined);assert.equal(body.tool_choice,undefined);assert.ok(!raw.includes('BAD_INPUT'));assert.ok(!raw.includes('BAD_MESSAGES'));assert.ok(raw.includes('总结以下历史'));
 });}
test('allProtocolsDisableToolsAndIgnoreCustomInputOverridesForSummaries',()=>isolated('compaction'));
test('allProtocolsDisableToolsAndIgnoreCustomInputOverridesForReplyRewrites',()=>isolated('reply_rewrite'));
test('anthropicPreservesDialogueDepthAndCollectsSystemDepth',async()=>{await withRejection(async(url,captured)=>{for(const role of ['user','assistant','system']){
 const card=JSON.parse('{"name":"林舟","extensions":{"depth_prompt":{"prompt":"深度设定","depth":1,"role":"'+role+'"}}}');const context={card,userName:'用户',userDescription:''},source=[personaMessage(context),{role:'user',content:'第一句',_eta_message_id:'first'},{role:'assistant',content:'第二句'}];
 await assert.rejects(completeAnthropicMessages({config:{baseUrl:url,apiKey:'fixture',model:'fixture',systemPrompt:''},messages:projectRoleplayMessages(context,source),tools:[],sessionId:''},new RunControl(),new NodeProviderTransport(),()=>{}),AgentModelFailure);
 const body=captured();assert.ok(!JSON.stringify(body).includes('_eta_message_id'));if(role==='system')assert.ok((body.system as {text:string}[])[0].text.includes('深度设定'));else{const text=JSON.stringify(body.messages);assert.ok(text.indexOf('第一句')<text.indexOf('深度设定'));assert.ok(text.indexOf('深度设定')<text.indexOf('第二句'));}
 }});});
