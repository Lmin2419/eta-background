// Ported from app/src/test/kotlin/io/github/mangi/eta/agent/mcp/McpProtocolValidationTest.kt (12), data/db/McpEntitiesTest.kt (1), agent/mcp/McpRunContextTest.kt (first 1) @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11). Two full model-executor tests await the separate tool-runtime task.
import test from 'node:test';import assert from 'node:assert/strict';
import {McpHttpClient,validateMcpEndpoint} from '../../entry/src/main/ets/core/eta/mcp/mcpHttpClient';
import {emptyMcpServer,MCP_LATEST,MCP_LEGACY,mcpFromEntity,mcpToEntity,activeMcpTools} from '../../entry/src/main/ets/core/eta/mcp/mcpTypes';
import {encodeMcpHeaderValue} from '../../entry/src/main/ets/core/eta/mcp/mcpToolHeaders';
import {object,array} from '../../entry/src/main/ets/core/eta/protocol/providerTypes';
import {fixture,respond,FetchMcpTransport,modernResult,legacyResult,initialize} from './mcpFixture';
const tool={name:'legacy',inputSchema:{type:'object'}};
test('McpProtocolValidation: modernDiscoveryUsesStatelessEnvelopeAndRoutingHeaders',async()=>{
 const f=await fixture((_,res)=>respond(res,200,modernResult(1,[{name:'search',inputSchema:{type:'object'}}],30000))),client=new McpHttpClient(emptyMcpServer('Test',f.url),null,new FetchMcpTransport());
 try{const result=await client.discoverTools();assert.equal(result.protocolVersion,MCP_LATEST);assert.equal(result.cacheTtlMs,30000);assert.deepEqual(result.tools.map(t=>t.name),['search']);assert.equal(f.received[0].headers['mcp-method'],'tools/list');assert.equal(object(object(f.received[0].body.params)?._meta)?.['io.modelcontextprotocol/protocolVersion'],MCP_LATEST);}finally{client.close();await f.close();}
});
test('McpProtocolValidation: autoModeFallsBackToLegacySessionHandshake',async()=>{
 let index=0;const f=await fixture((r,res)=>{if(r.method==='DELETE'){respond(res,200,'');return;}switch(index++){case 0:respond(res,400,'bad protocol');break;case 1:initialize(res,2);break;case 2:respond(res,202,'');break;default:respond(res,200,legacyResult(3,[tool]));}}),transport=new FetchMcpTransport(),client=new McpHttpClient(emptyMcpServer('Test',f.url),null,transport);
 try{const result=await client.discoverTools();assert.equal(result.protocolVersion,MCP_LEGACY);assert.deepEqual(result.tools.map(t=>t.name),['legacy']);client.close();await transport.releaseDone;const requests=f.received.filter(r=>r.method!=='DELETE');assert.equal(requests.length,4);assert.equal(object(requests[1].body.params)?._meta,undefined);assert.equal(object(requests[3].body.params)?._meta,undefined);assert.equal(f.received.at(-1)?.method,'DELETE');assert.equal(requests[3].headers['mcp-session-id'],'session-1');}finally{client.close();await f.close();}
});
test('McpProtocolValidation: autoModeFallsBackWhenModernToolListReturnsLegacyResultShape',async()=>{
 let index=0;const f=await fixture((r,res)=>{switch(index++){case 0:respond(res,200,legacyResult(1,[{name:'ignored-modern',inputSchema:{}}]));break;case 1:initialize(res,2,'2024-11-05','');break;case 2:respond(res,202,'');break;default:respond(res,200,legacyResult(3,[tool]));}}),client=new McpHttpClient(emptyMcpServer('Test',f.url),null,new FetchMcpTransport());
 try{const result=await client.discoverTools();assert.equal(result.protocolVersion,'2024-11-05');assert.deepEqual(result.tools.map(t=>t.name),['legacy']);assert.deepEqual(f.received.map(r=>r.body.method),['tools/list','initialize','notifications/initialized','tools/list']);assert.deepEqual(f.received.map(r=>r.headers['mcp-protocol-version']),[MCP_LATEST,MCP_LEGACY,'2024-11-05','2024-11-05']);}finally{client.close();await f.close();}
});
test('McpProtocolValidation: legacyHandshakeUsesOlderVersionSelectedByServer',async()=>{
 const versions:string[]=[];let index=0;const f=await fixture((r,res)=>{if(r.method==='DELETE'){respond(res,200,'');return;}switch(index++){case 0:respond(res,400,'legacy');break;case 1:initialize(res,r.body.id,'2024-11-05');break;case 2:versions.push(String(r.headers['mcp-protocol-version']));respond(res,202,'');break;default:versions.push(String(r.headers['mcp-protocol-version']));respond(res,200,legacyResult(r.body.id,[tool]));}}),transport=new FetchMcpTransport(),client=new McpHttpClient(emptyMcpServer('Test',f.url),null,transport);
 try{const result=await client.discoverTools();assert.equal(result.protocolVersion,'2024-11-05');assert.deepEqual(versions,['2024-11-05','2024-11-05']);assert.deepEqual(result.tools.map(t=>t.name),['legacy']);}finally{client.close();await transport.releaseDone;await f.close();}
});
test('McpProtocolValidation: modernJsonRpcHeaderErrorDoesNotFallBackToLegacy',async()=>{
 const f=await fixture((_,res)=>respond(res,400,{jsonrpc:'2.0',id:1,error:{code:-32020,message:'Header mismatch'}})),client=new McpHttpClient(emptyMcpServer('Test',f.url),null,new FetchMcpTransport());try{await assert.rejects(client.discoverTools());assert.equal(f.received.length,1);}finally{client.close();await f.close();}
});
test('McpProtocolValidation: sseParserCombinesDataLinesAndReturnsAtFinalEvent',{timeout:2500},async()=>{
 const f=await fixture((_,res)=>{res.writeHead(200,{'Content-Type':'text/event-stream'});res.write('data: {"jsonrpc":"2.0",\ndata: "id":1,"result":{"resultType":"complete","ttlMs":1000,"tools":[]}}\n\n');/* deliberately no res.end(): connection stays open */}),client=new McpHttpClient(emptyMcpServer('Test',f.url),null,new FetchMcpTransport());
 try{const start=performance.now(),result=await client.discoverTools();assert.deepEqual(result.tools,[]);assert.ok(performance.now()-start<2000);}finally{client.close();await f.close();}
});
test('McpProtocolValidation: legacyToolCallReinitializesOnceAfterSession404',async()=>{
 let inits=0,calls=0;const f=await fixture((r,res)=>{if(r.method==='DELETE'){respond(res,200,'');return;}switch(r.body.method){case 'tools/list':r.headers['mcp-protocol-version']===MCP_LATEST?respond(res,400,'legacy'):respond(res,200,legacyResult(3,[tool]));break;case 'initialize':initialize(res,++inits===1?2:5,MCP_LEGACY,'session-'+inits);break;case 'notifications/initialized':respond(res,202,'');break;case 'tools/call':++calls===1?respond(res,404,'expired'):respond(res,200,{jsonrpc:'2.0',id:6,result:{content:[{type:'text',text:'ok'}]}});break;}}),transport=new FetchMcpTransport(),client=new McpHttpClient(emptyMcpServer('Test',f.url),null,transport);
 try{const discovered=await client.discoverTools(),result=await client.callTool(discovered.tools[0],{});assert.equal(object(array(result.content)[0])?.text,'ok');assert.equal(inits,2);assert.equal(calls,2);}finally{client.close();await transport.releaseDone;await f.close();}
});
test('McpProtocolValidation: legacyToolListReinitializesOnceAfterSession404',async()=>{
 let inits=0,lists=0;const f=await fixture((r,res)=>{if(r.method==='DELETE'){respond(res,200,'');return;}switch(r.body.method){case 'tools/list':r.headers['mcp-protocol-version']===MCP_LATEST?respond(res,400,'legacy'):++lists===1?respond(res,404,'expired'):respond(res,200,legacyResult(r.body.id,[tool]));break;case 'initialize':initialize(res,r.body.id,MCP_LEGACY,'session-'+(++inits));break;case 'notifications/initialized':respond(res,202,'');break;}}),transport=new FetchMcpTransport(),client=new McpHttpClient(emptyMcpServer('Test',f.url),null,transport);
 try{const result=await client.discoverTools();assert.deepEqual(result.tools.map(t=>t.name),['legacy']);assert.equal(inits,2);assert.equal(lists,2);}finally{client.close();await transport.releaseDone;await f.close();}
});
test('McpProtocolValidation: closeCancelsActiveRequestWithoutWaitingForNetworkTimeout',{timeout:2500},async()=>{
 let started!:()=>void;const ready=new Promise<void>(resolve=>{started=resolve;}),f=await fixture(()=>started()),client=new McpHttpClient(emptyMcpServer('Test',f.url),null,new FetchMcpTransport());
 try{const failure=assert.rejects(client.discoverTools());await ready;const before=performance.now();client.close();assert.ok(performance.now()-before<500);await failure;}finally{client.close();await f.close();}
});
test('McpProtocolValidation: endpointAcceptsUserConfiguredHttpAndHttpsUrls',()=>{
 assert.equal(new URL(validateMcpEndpoint('https://example.com/mcp')).protocol,'https:');assert.equal(new URL(validateMcpEndpoint('http://127.0.0.1:8080/mcp')).protocol,'http:');assert.equal(new URL(validateMcpEndpoint('http://192.168.1.2:8080/mcp')).protocol,'http:');assert.equal(new URL(validateMcpEndpoint('https://user@example.com/mcp')).username,'user');assert.throws(()=>validateMcpEndpoint('not a url'));
});
test('McpProtocolValidation: modernDiscoveryKeepsSchemaKeywordsWithoutAvailabilityGate',async()=>{
 const schema={type:'object',anyOf:[{required:['query']},{required:['filter']}],properties:{query:{$ref:'#/$defs/query'}},$defs:{query:{type:'string'}}},f=await fixture((_,res)=>respond(res,200,modernResult(1,[{name:'search notes / advanced',inputSchema:schema}]))),client=new McpHttpClient(emptyMcpServer('Test',f.url),null,new FetchMcpTransport());
 try{const result=await client.discoverTools();assert.equal(result.tools[0].name,'search notes / advanced');assert.equal(result.tools[0].inputSchemaJson,JSON.stringify(schema));}finally{client.close();await f.close();}
});
test('McpProtocolValidation: modernToolCallMirrorsAnnotatedParameters',async()=>{
 const f=await fixture((_,res)=>respond(res,200,{jsonrpc:'2.0',id:1,result:{resultType:'complete',content:[]}})),client=new McpHttpClient({...emptyMcpServer('Test',f.url),lastProtocolVersion:MCP_LATEST},null,new FetchMcpTransport());
 try{await client.callTool({name:'执行任务',title:'',description:'',inputSchemaJson:JSON.stringify({type:'object',properties:{region:{type:'string','x-mcp-header':'Region'},page:{type:'integer','x-mcp-header':'Page'}}}),readOnlyHint:null,destructiveHint:null,idempotentHint:null,openWorldHint:null},{region:'华东',page:42});const h=f.received[0].headers;assert.equal(h['mcp-name'],encodeMcpHeaderValue('执行任务'));assert.equal(h['mcp-param-region'],encodeMcpHeaderValue('华东'));assert.equal(h['mcp-param-page'],'42');}finally{client.close();await f.close();}
});
test('McpEntities: legacyUnavailableReasonIsIgnoredWhenLoadingCachedTools',()=>{
 const server=mcpFromEntity({...mcpToEntity({...emptyMcpServer('Server','https://example.com/mcp',1),id:'server'}),toolsJson:'[{"name":"create_task","inputSchemaJson":"{\\"type\\":\\"object\\",\\"anyOf\\":[]}","unavailableReason":"暂不支持 schema 关键字 anyOf"}]',enabledToolNamesJson:'["create_task"]'});assert.deepEqual(activeMcpTools(server).map(t=>t.name),['create_task']);
});
test('McpRunContext: enabledToolIsActiveRegardlessOfSchemaKeywords',()=>{
 const server={...emptyMcpServer(),tools:[{name:'create_task',title:'',description:'',inputSchemaJson:'{"type":"object","anyOf":[]}',readOnlyHint:null,destructiveHint:null,idempotentHint:null,openWorldHint:null}],enabledToolNames:['create_task']};assert.deepEqual(activeMcpTools(server),server.tools);
});
