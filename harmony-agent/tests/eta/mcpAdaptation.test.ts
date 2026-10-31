// Harmony adaptation regressions for app/src/main/kotlin/io/github/mangi/eta/ui/screens/mcp/McpServersScreen.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11). These are additional tests, not counted as translated Eta tests.
import test from 'node:test';import assert from 'node:assert/strict';
import {ProfileStorage,SecretStorage} from '../../entry/src/main/ets/core/profiles';
import {McpServerRepository} from '../../entry/src/main/ets/core/eta/mcp/mcpRepository';
import {McpUiStore} from '../../entry/src/main/ets/core/eta/mcp/mcpUiStore';
import {emptyMcpServer,MCP_LATEST,MCP_LEGACY,mcpToEntity} from '../../entry/src/main/ets/core/eta/mcp/mcpTypes';
import {McpHttpClient,McpTransport,McpWireRequest,McpWireResponse,applyMcpDiscovery} from '../../entry/src/main/ets/core/eta/mcp/mcpHttpClient';
import {fixture,FetchMcpTransport,modernResult,respond} from './mcpFixture';
import {FoundationController} from '../../entry/src/main/ets/core/foundation/controller';
import {ConversationRepository} from '../../entry/src/main/ets/core/conversations';
import {ProfileRepository} from '../../entry/src/main/ets/core/profiles';
import {DraftRepository} from '../../entry/src/main/ets/core/drafts';
class Storage implements ProfileStorage{value='';fail=false;async read(){return this.value;}async write(value:string){if(this.fail)throw Error('disk full');this.value=value;}}
class Secrets implements SecretStorage{values=new Map<string,string>();fail=false;async get(id:string){if(!this.values.has(id))throw Error('missing secret');return this.values.get(id)!;}async put(id:string,value:string){if(this.fail)throw Error('ASSET failed');this.values.set(id,value);}async remove(id:string){this.values.delete(id);}}
function setup(){const storage=new Storage(),secrets=new Secrets();let id=0;return {storage,secrets,repository:new McpServerRepository(storage,secrets,()=>String(++id))};}
const safe={name:'read',title:'Read',description:'',inputSchemaJson:'{}',readOnlyHint:true,destructiveHint:null,idempotentHint:null,openWorldHint:null},risky={...safe,name:'write',readOnlyHint:null};
function deferred<T>(){let resolve!:(value:T)=>void;const promise=new Promise<T>(r=>{resolve=r;});return {promise,resolve};}
class HeldTransport implements McpTransport {
 result=deferred<McpWireResponse>();started=deferred<void>();callback:((chunk:string,headers:Record<string,string>)=>boolean)|null=null;cancelled=false;released:McpWireRequest[]=[];
 exchange(_request:McpWireRequest,onChunk:(chunk:string,headers:Record<string,string>)=>boolean):Promise<McpWireResponse>{this.callback=onChunk;this.started.resolve();return this.result.promise;}
 finish(body:object,headers:Record<string,string>={}):void{this.callback?.(JSON.stringify(body),headers);this.result.resolve({status:200,headers});}
 cancel():void{this.cancelled=true;}
 release(request:McpWireRequest):void{this.released.push(request);}
}
test('MCP adaptation: unauthenticated servers never require a secret and RDB contains no bearer token',async()=>{
 const h=setup(),none=await h.repository.add(emptyMcpServer('No auth','https://example.com/mcp'),''),auth=await h.repository.add({...emptyMcpServer('Auth','https://example.com/mcp'),authorizationType:'bearer'},'fixture-secret');assert.equal(await h.repository.token(none.id),'');assert.equal(await h.repository.token(auth.id),'fixture-secret');await h.repository.setEnabled(none.id,false);await h.repository.delete(none.id);assert.equal(h.storage.value.includes('fixture-secret'),false);assert.equal((await h.repository.list()).length,1);
});
test('MCP adaptation: failed add cleans its ASSET alias and retains previous document',async()=>{
 const h=setup();h.storage.fail=true;await assert.rejects(h.repository.add({...emptyMcpServer(),authorizationType:'bearer'},'secret'));assert.equal(h.secrets.values.size,0);assert.equal(h.storage.value,'');
});
test('MCP adaptation: failed token update and delete restore previous secret without changing RDB',async()=>{
 const h=setup(),server=await h.repository.add({...emptyMcpServer(),authorizationType:'bearer'},'old'),before=h.storage.value;h.storage.fail=true;await assert.rejects(h.repository.update(server,'new'));assert.equal(await h.repository.token(server.id),'old');await assert.rejects(h.repository.delete(server.id));assert.equal(await h.repository.token(server.id),'old');assert.equal(h.storage.value,before);
});
test('MCP adaptation: ASSET failure cannot commit metadata and token updates invalidate cache',async()=>{
 const h=setup(),server=await h.repository.add({...emptyMcpServer(),authorizationType:'bearer',lastProtocolVersion:MCP_LATEST,toolsExpireAt:999},'old'),before=h.storage.value;h.secrets.fail=true;await assert.rejects(h.repository.update(server,'new'));assert.equal(h.storage.value,before);h.secrets.fail=false;await h.repository.update(server,'new');assert.equal(await h.repository.token(server.id),'new');const saved=(await h.repository.list())[0];assert.equal(saved.lastProtocolVersion,null);assert.equal(saved.toolsExpireAt,null);
});
test('MCP adaptation: token boundary uses UTF-8 bytes and rejects header injection',async()=>{
 const h=setup();for(const token of ['x\r\nAuthorization: other','界'.repeat(342)])await assert.rejects(h.repository.add({...emptyMcpServer(),authorizationType:'bearer'},token));assert.equal((await h.repository.list()).length,0);await h.repository.add({...emptyMcpServer(),authorizationType:'bearer'},'x'.repeat(1024));
});
test('MCP adaptation: concurrent switches preserve distinct tools and server order',async()=>{
 const h=setup(),a=await h.repository.add({...emptyMcpServer('A'),tools:[safe,risky]},''),b=await h.repository.add(emptyMcpServer('B'),'');await Promise.all([h.repository.setToolEnabled(a.id,safe.name,true),h.repository.setToolEnabled(a.id,risky.name,true)]);assert.deepEqual((await h.repository.list()).map(s=>s.id),[a.id,b.id]);assert.deepEqual((await h.repository.list())[0].enabledToolNames,['read','write']);
});
test('MCP adaptation: cancellation ignores late discovery and cannot save an add draft',async()=>{
 const h=setup(),transport=new HeldTransport(),store=new McpUiStore(h.repository,()=>transport,()=>{});await store.open();store.show('add');store.draft('name','Example');store.draft('url','https://example.com/mcp');const add=store.add();await transport.started.promise;store.close();transport.finish(modernResult(1));await add;assert.equal(transport.cancelled,true);assert.equal((await h.repository.list()).length,0);assert.equal(store.snapshot().draft.token,'');assert.equal(store.snapshot().notice,'');
});
test('MCP adaptation: failed discovery preserves all draft fields but never exposes remote error text',async()=>{
 const h=setup(),f=await fixture((_,res)=>respond(res,401,{error:{code:-32001,message:'remote secret-response'}})),store=new McpUiStore(h.repository,()=>new FetchMcpTransport(),()=>{});try{await store.open();store.show('add');store.draft('name','Example');store.draft('url',f.url);store.draft('token','fixture-secret');const expected=store.snapshot().draft;await store.add();assert.deepEqual(store.snapshot().draft,expected);assert.equal(store.snapshot().notice,'connection_failed');assert.equal((await h.repository.list()).length,0);assert.equal(JSON.stringify(store.snapshot()).includes('remote secret-response'),false);}finally{store.close();await f.close();}
});
test('MCP adaptation: successful add probes before saving and defaults every discovered tool off',async()=>{
 const h=setup(),f=await fixture((r,res)=>respond(res,200,modernResult(r.body.id,[{name:'read',inputSchema:{}}]))),store=new McpUiStore(h.repository,()=>new FetchMcpTransport(),()=>{},()=>100);try{await store.open();store.show('add');store.draft('name',' Example ');store.draft('url',f.url);await store.add();const state=store.snapshot();assert.equal(state.modal,'');assert.equal(state.servers[0].name,'Example');assert.equal(state.servers[0].tools.length,1);assert.deepEqual(state.servers[0].enabledToolNames,[]);assert.equal(state.notice,'added');assert.equal(state.noticeCount,1);assert.equal(state.servers[0].toolsExpireAt,1100);}finally{store.close();await f.close();}
});
test('MCP adaptation: only risky enable requires confirmation; disabling and read-only enable are direct',async()=>{
 const h=setup(),server=await h.repository.add({...emptyMcpServer(),tools:[safe,risky]},''),store=new McpUiStore(h.repository,()=>new FetchMcpTransport(),()=>{});await store.open(server.id);await store.setTool('write',true);assert.equal(store.snapshot().modal,'risk');assert.deepEqual((await h.repository.list())[0].enabledToolNames,[]);store.dismiss();await store.setTool('read',true);assert.deepEqual((await h.repository.list())[0].enabledToolNames,['read']);await store.setTool('write',true);await store.confirmRisk();await store.setTool('write',false);assert.deepEqual((await h.repository.list())[0].enabledToolNames,['read']);
});
test('MCP adaptation: refresh intersects enabled names and uses source TTL semantics',()=>{
 const server={...emptyMcpServer(),tools:[safe,risky],enabledToolNames:['read','write']};const updated=applyMcpDiscovery(server,{protocolVersion:MCP_LATEST,tools:[safe],cacheTtlMs:123},100);assert.deepEqual(updated.enabledToolNames,['read']);assert.equal(updated.toolsExpireAt,223);assert.equal(applyMcpDiscovery(server,{protocolVersion:MCP_LEGACY,tools:[],cacheTtlMs:null},100).toolsExpireAt,null);
});
test('MCP adaptation: replacing token with blank removes ASSET; delete failure keeps detail/dialog',async()=>{
 const h=setup(),server=await h.repository.add({...emptyMcpServer(),authorizationType:'bearer'},'secret'),store=new McpUiStore(h.repository,()=>new FetchMcpTransport(),()=>{});await store.open(server.id);store.show('token');await store.saveToken();assert.equal((await h.repository.list())[0].authorizationType,'none');assert.equal(h.secrets.values.size,0);store.show('delete');h.storage.fail=true;assert.equal(await store.delete(),false);assert.equal(store.snapshot().selectedId,server.id);assert.equal(store.snapshot().modal,'delete');h.storage.fail=false;assert.equal(await store.delete(),true);assert.equal(store.snapshot().servers.length,0);
});
test('MCP adaptation: late initialization releases newly allocated session after cancellation',async()=>{
 const transport=new HeldTransport(),client=new McpHttpClient({...emptyMcpServer('Test','https://example.com/mcp'),protocolMode:MCP_LEGACY},null,transport),failure=assert.rejects(client.discoverTools());await transport.started.promise;client.close();transport.finish({jsonrpc:'2.0',id:1,result:{protocolVersion:MCP_LEGACY}},{'mcp-session-id':'late-session'});await failure;assert.equal(transport.released.length,1);assert.equal(transport.released[0].headers['Mcp-Session-Id'],'late-session');
});
test('MCP adaptation: modern pagination is limited to eight pages and 128 tools',async()=>{
 let calls=0;const f=await fixture((r,res)=>{calls++;respond(res,200,{jsonrpc:'2.0',id:r.body.id,result:{resultType:'complete',ttlMs:1000/calls,nextCursor:'more',tools:Array.from({length:20},(_,i)=>({name:'t'+calls+'-'+i,inputSchema:{}}))}});}),client=new McpHttpClient(emptyMcpServer('Test',f.url),null,new FetchMcpTransport());try{const result=await client.discoverTools();assert.equal(result.tools.length,128);assert.equal(calls,7);assert.equal(result.cacheTtlMs,Math.trunc(1000/7));}finally{client.close();await f.close();}
});
test('MCP adaptation: response body is bounded and mismatched JSON-RPC id cannot become a catalog',async()=>{
 for(const body of [{jsonrpc:'2.0',id:99,result:{resultType:'complete',tools:[]}},'x'.repeat(1048577)]){const f=await fixture((_,res)=>respond(res,200,body)),client=new McpHttpClient(emptyMcpServer('Test',f.url),null,new FetchMcpTransport());try{await assert.rejects(client.discoverTools());assert.equal(f.received.length,1);}finally{client.close();await f.close();}}
});
test('MCP adaptation: corrupt document is not treated as an empty server list',async()=>{
 const h=setup();h.storage.value='{broken';await assert.rejects(h.repository.add(emptyMcpServer(),''));assert.equal(h.storage.value,'{broken');h.storage.value=JSON.stringify([mcpToEntity({...emptyMcpServer(),id:'old',tools:[safe]})]);assert.equal((await h.repository.list())[0].tools[0].name,'read');
});
async function controllerFixture(){const h=setup(),transport=new HeldTransport();let id=0;const profiles=new ProfileRepository(new Storage(),new Secrets(),()=>String(++id)),chats=new ConversationRepository(new Storage(),()=>String(++id),Date.now,()=>{},2),drafts=new DraftRepository(new Storage());const controller=new FoundationController({chats,profiles,drafts,mcp:h.repository,mcpTransport:()=>transport,transport:()=>({get:async()=>'',post:async()=>'',cancel(){}}),uuid:()=>String(++id),load:async()=>{await profiles.load();await chats.load();await drafts.load();}});await controller.initialize();return {...h,transport,controller};}
async function loaded(controller:FoundationController){for(let i=0;i<100&&controller.snapshot().mcp.loading;i++)await new Promise<void>(r=>setImmediate(r));assert.equal(controller.snapshot().mcp.loading,false);}
test('MCP integration: detail back restores list; deleting a server returns to refreshed list',async()=>{
 const h=await controllerFixture(),server=await h.repository.add(emptyMcpServer('Example','https://example.com/mcp'),'');h.controller.openMcp();await loaded(h.controller);h.controller.openMcp(server.id);await loaded(h.controller);assert.equal(h.controller.snapshot().screen,'mcpDetail');h.controller.back();await loaded(h.controller);assert.equal(h.controller.snapshot().screen,'mcp');assert.equal(h.controller.snapshot().mcp.selectedId,'');h.controller.openMcp(server.id);await loaded(h.controller);h.controller.mcp!.show('delete');await h.controller.deleteMcp();await loaded(h.controller);assert.equal(h.controller.snapshot().screen,'mcp');assert.equal(h.controller.snapshot().mcp.servers.length,0);
});
test('MCP integration: switching conversation cancels pending catalog and clears transient token',async()=>{
 const h=await controllerFixture();h.controller.openMcp();await loaded(h.controller);h.controller.mcp!.show('add');h.controller.mcp!.draft('name','Example');h.controller.mcp!.draft('url','https://example.com/mcp');h.controller.mcp!.draft('token','fixture-secret');const pending=h.controller.mcp!.add();await h.transport.started.promise;await h.controller.selectConversation(h.controller.snapshot().chat.id);h.transport.finish(modernResult(1));await pending;assert.equal(h.transport.cancelled,true);assert.equal(h.controller.snapshot().mcp.draft.token,'');assert.equal((await h.repository.list()).length,0);assert.equal(h.controller.snapshot().screen,'chat');
});
