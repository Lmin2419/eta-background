import test from 'node:test';
import assert from 'node:assert/strict';
import { fitContext } from '../entry/src/main/ets/core/contextBudget';
import { prepareBackup, validateBackup, crc32, decodeBase64 } from '../entry/src/main/ets/core/backup';
import { ExtensionRepository, parseSkill, parseRole, resourcePath, parseExtensions, characterPngPayload } from '../entry/src/main/ets/core/extensions';
import { McpClient, RpcTransport } from '../entry/src/main/ets/core/mcp';
import { SubtaskTools } from '../entry/src/main/ets/core/subtasks';
import { RunControl, Message, UnknownToolOutcomeError } from '../entry/src/main/ets/core/types';
import { DraftRepository } from '../entry/src/main/ets/core/drafts';
import { WorkspaceTools, WorkspaceBackend } from '../entry/src/main/ets/core/workspace';
import { BrowserTools, BrowserPort } from '../entry/src/main/ets/core/browser';
import { wavPcm } from '../entry/src/main/ets/core/audio';
import { AgentLoop } from '../entry/src/main/ets/core/loop';
import { diagnoseModel } from '../entry/src/main/ets/core/modelDiagnostics';
const emptyExtensions=()=>({version:1 as const,skills:[],roles:[],mcp:[],apps:[],roleSelections:{},secondaryProfileId:'',subtasks:false});
const control=()=>new RunControl();
test('context trimming preserves current goal and entire current tool chain',()=>{
 const messages:Message[]=[{role:'system',content:'policy'},{role:'user',content:'old goal'.repeat(1000)},{role:'assistant',content:'old answer'.repeat(1000)},{role:'user',content:'current',currentGoal:true},{role:'assistant',content:null,tool_calls:[{id:'x',type:'function',function:{name:'tap',arguments:'{}'}}]},{role:'tool',tool_call_id:'x',content:'outcome unknown'}];
 const r=fitContext(messages,8000,1000);assert.equal(r.omittedMessages,2);assert.ok(r.messages.some(m=>m.currentGoal));assert.equal(r.messages.at(-1)?.content,'outcome unknown');assert.ok(r.summary.includes('旧用户目标'));assert.ok(messages.length===6);
});
test('context budget never drops oversized current goal and rejects absent anchor',()=>{
 assert.throws(()=>fitContext([{role:'user',content:'x'}]),/目标标记/);
 assert.throws(()=>fitContext([{role:'system',content:'s'},{role:'user',content:'x'.repeat(10000),currentGoal:true}],8000,1000),/超过上下文预算/);
});
test('backup strips credentials, validates CRC and refuses traversal before any restore',()=>{
 const profiles={selectedId:'p',profiles:[{id:'p',name:'service',baseUrl:'https://a.example/v1',model:'m',screenshots:false,allowInsecureHttp:false,secretRef:'model-key-secret'}]};
 const raw=prepareBackup({version:1,selectedId:'',conversations:[]},profiles,emptyExtensions(),[{name:'a.txt',base64:'aGVsbG8=',crc32:crc32(new TextEncoder().encode('hello'))}]);
 assert.equal(raw.profiles.profiles[0].secretRef,undefined);assert.equal(new TextDecoder().decode(decodeBase64(raw.files[0].base64)),'hello');
 raw.files[0].base64='aGVsbGE=';assert.throws(()=>validateBackup(JSON.stringify(raw)),/校验失败/);raw.files[0].name='../outside';assert.throws(()=>validateBackup(JSON.stringify(raw)),/文件名/);
});
test('backup rejects future versions, duplicate files and oversized content',()=>{
 const b=prepareBackup({version:1,selectedId:'',conversations:[]},{profiles:[],selectedId:''},emptyExtensions(),[]);
 assert.throws(()=>validateBackup(JSON.stringify({...b,version:2})),/版本/);b.files=[{name:'a',base64:'',crc32:0},{name:'a',base64:'',crc32:0}];assert.throws(()=>validateBackup(JSON.stringify(b)),/重复/);
});
test('skill resources cannot traverse and import conflicts never overwrite installed version',async()=>{
 for(const p of ['../x','/x','a/../../b','a\\b','a//b'])assert.throws(()=>resourcePath(p));
 let raw='';const repo=new ExtensionRepository({read:async()=>raw,write:async(v)=>{raw=v;}});await repo.load();
 const skill={id:'work',version:'1',name:'Work',instructions:'summarize',resources:[{path:'refs/a.md',text:'data'}],enabled:true};
 await repo.installSkill(JSON.stringify(skill));assert.equal(repo.document.skills[0].enabled,false);
 await assert.rejects(()=>repo.installSkill(JSON.stringify({...skill,version:'2'})),/已存在/);assert.equal(repo.document.skills[0].version,'1');await repo.installSkill(JSON.stringify({...skill,version:'2'}),true);assert.equal(repo.document.skills[0].version,'2');
});
test('role imports support character card v2 and worldbook trigger isolation',async()=>{
 const role=parseRole({spec:'chara_card_v2',data:{name:'Library',description:'archivist',character_book:{entries:[{keys:['books'],content:'secret-lore'}]}}});
 let raw=JSON.stringify({...emptyExtensions(),roles:[role],roleSelections:{a:role.id}});const repo=new ExtensionRepository({read:async()=>raw,write:async(v)=>{raw=v;}});await repo.load();
 assert.ok(repo.context('a','books').includes('secret-lore'));assert.ok(!repo.context('b','books').includes('secret-lore'));assert.notEqual(repo.memoryFile('a'),repo.memoryFile('b'));
});
test('draft reload preserves attachments and viewport, never provider key',async()=>{
 let raw='';const storage={read:async()=>raw,write:async(v:string)=>{raw=v;}};const d=new DraftRepository(storage);await d.load();d.setMessage('c','hello');d.setViewport('c',155,false);d.setAttachments('c',['a.txt']);d.setMessage('c','edited');await d.flush();const reloaded=new DraftRepository(storage);await reloaded.load();assert.deepEqual(reloaded.message('c'),{id:'c',text:'edited',scroll:155,followBottom:false,attachments:['a.txt']});
});
const config={id:'test',name:'Fixture',url:'https://mcp.example/rpc',enabled:true,enabledTools:['read']};
function rpcFixture(failTool=false){let calls:any[]=[];const io:RpcTransport={cancel(){},async send(url,headers,body){const r=JSON.parse(body);calls.push({url,headers,r});let result:any={};if(r.method==='initialize')result={protocolVersion:'2025-03-26',capabilities:{tools:{}}};if(r.method==='tools/list')result={tools:[{name:'read',inputSchema:{type:'object',properties:{}}},{name:'disabled',inputSchema:{type:'object'}}]};if(r.method==='tools/call'){if(failTool)throw new Error('connection lost');result={content:[{type:'text',text:'result'}]};}return {status:r.id?200:202,headers:'{"Mcp-Session-Id":"s1"}',body:r.id?JSON.stringify({jsonrpc:'2.0',id:r.id,result}):''};}};return {io,calls};}
test('MCP initializes once, binds session/key to same URL and exposes only checked tools',async()=>{
 const {io,calls}=rpcFixture();const client=new McpClient(config,'key',io);await client.connect(control());const defs=client.definitions();assert.equal(defs.length,1);const alias=(defs[0].function as {name:string}).name;
 assert.equal((await client.execute(alias,{},control())).ok,true);assert.equal(calls[2].headers['Mcp-Session-Id'],'s1');assert.ok(calls.every(c=>c.url===config.url&&c.headers.Authorization==='Bearer key'));await assert.rejects(()=>client.execute('disabled',{},control()),/未启用/);
});
test('MCP unknown tool result stops the parent loop and never automatically replays',async()=>{
 const {io,calls}=rpcFixture(true),client=new McpClient(config,'key',io);await client.connect(control());const alias=(client.definitions()[0].function as {name:string}).name;let modelRequests=0;
 const loop=new AgentLoop({cancel(){},async complete(){modelRequests++;return {finishReason:'tool_calls',message:{role:'assistant',content:null,tool_calls:[{id:'t',type:'function',function:{name:alias,arguments:'{}'}}]}};}},client,()=>{},{allowDevice:false,allowedTools:[alias]});
 await assert.rejects(()=>loop.run('read'),UnknownToolOutcomeError);assert.equal(modelRequests,1);assert.equal(calls.filter(c=>c.r.method==='tools/call').length,1);
});
test('MCP rejects duplicate response IDs and redirect responses',async()=>{
 for(const bad of ['duplicate','redirect']){const io:RpcTransport={cancel(){},async send(_u,_h,b){const id=JSON.parse(b).id;const e={jsonrpc:'2.0',id,result:{protocolVersion:'2025-03-26'}};return {status:bad==='redirect'?307:200,headers:'{}',body:'data: '+JSON.stringify(e)+'\n\ndata: '+JSON.stringify(e)+'\n\n'};}};await assert.rejects(()=>new McpClient(config,'k',io).connect(control()),/ID|HTTP/);}
});
test('subtask cannot receive tools or leak parent context and is capped at four calls',async()=>{
 let calls=0;const tool=new SubtaskTools({cancel(){},async complete(messages){calls++;assert.equal(messages.length,2);assert.equal(messages[1].content,'isolated');return {finishReason:'stop',message:{role:'assistant',content:'analysis'}};}});
 for(let i=0;i<4;i++)await tool.execute('delegate_analysis',{task:'isolated'},control());await assert.rejects(()=>tool.execute('delegate_analysis',{task:'isolated'},control()),/上限/);assert.equal(calls,4);
});
test('browser takeover invalidates actions and unknown script result is fatal',async()=>{
 let code='';const port:BrowserPort={ready:()=>true,invalidate(){},async navigate(){},async script(c){code=c;if(c.includes('document.querySelectorAll'))return JSON.stringify({url:'https://example.com',nodes:[]});throw new Error('gone');}};
 const t=new BrowserTools(port),page=await t.execute('browser_read',{},control()),id=page.data!.observation_id;t.invalidate();await assert.rejects(()=>t.execute('browser_click',{observation_id:id,node_id:0},control()),/过期/);const p=await t.execute('browser_read',{},control());await assert.rejects(()=>t.execute('browser_click',{observation_id:p.data!.observation_id,node_id:0},control()),UnknownToolOutcomeError);assert.ok(code.includes('isConnected'));
});
test('WAV parser accepts only declared PCM16/16k mono and bounded RIFF chunks',()=>{
 const b=new Uint8Array(48),v=new DataView(b.buffer);for(const [p,s] of [[0,'RIFF'],[8,'WAVE'],[12,'fmt '],[36,'data']] as [number,string][])for(let i=0;i<s.length;i++)b[p+i]=s.charCodeAt(i);v.setUint32(16,16,true);v.setUint16(20,1,true);v.setUint16(22,1,true);v.setUint32(24,16000,true);v.setUint16(34,16,true);v.setUint32(40,4,true);assert.equal(wavPcm(b).length,4);v.setUint16(22,2,true);assert.throws(()=>wavPcm(b),/单声道/);
});
test('provider diagnostic records independent failures without executing returned tools',async()=>{
 let calls=0;const io={cancel(){},async post(){calls++;if(calls===1)throw new Error('network');return JSON.stringify({choices:[{finish_reason:'tool_calls',message:{role:'assistant',content:null,tool_calls:[{id:'t',type:'function',function:{name:'connection_check',arguments:'{"ok":true}'}}]}}]});}};
 const result=await diagnoseModel({baseUrl:'https://a.example/v1',model:'m',apiKey:'hidden',screenshots:false},io,control(),()=>{});
 assert.equal(result[0].state,'failed');assert.equal(result.find(r=>r.id==='stream')?.state,'unverified');assert.equal(result.find(r=>r.id==='tools')?.state,'passed');assert.equal(calls,2);
});

import { rolePng } from '../entry/src/main/ets/core/characterPng';
import { usageOf } from '../entry/src/main/ets/core/usage';
import { safeHeaders, metadataFor } from '../entry/src/main/ets/core/modelMetadata';
import { SystemTools, SystemPort } from '../entry/src/main/ets/core/systemTools';
import { messageBlocks } from '../entry/src/main/ets/core/markdown';
import { ConversationRepository } from '../entry/src/main/ets/core/conversations';
import { ConversationRun } from '../entry/src/main/ets/core/conversationRun';

test('exported character PNG round-trips its role and bounds corrupt chunks',()=>{
 const role={id:'librarian',name:'图书管理员',description:'整理书目',world:[]};const encoded=Buffer.from(JSON.stringify(role)).toString('base64');const png=rolePng(encoded);assert.equal(characterPngPayload(png),encoded);const bad=png.slice();bad.set([255,255,255,255],8);assert.throws(()=>characterPngPayload(bad),/长度/);
});
test('provider metadata is advisory, custom headers cannot override credentials or inject lines',()=>{
 assert.deepEqual(safeHeaders({'OpenAI-Project':'project'}),{'OpenAI-Project':'project'});
 for(const h of [{Authorization:'Bearer x'},{Cookie:'x'},{'x-api-key':'x'},{'OpenAI-Project':'x\r\nHost: bad'}])assert.throws(()=>safeHeaders(h),/只允许/);
 assert.equal(metadataFor({context_length:128000,architecture:{input_modalities:['text','image']}},'m').vision,true);assert.equal(metadataFor({},'m').vision,undefined);
});
test('real provider usage is accepted only with finite non-negative integer counts',()=>{
 assert.deepEqual(usageOf({prompt_tokens:10,completion_tokens:4}),{input:10,output:4,total:14});assert.equal(usageOf({input_tokens:-1,output_tokens:5}),undefined);assert.equal(usageOf({input_tokens:'10',output_tokens:5}),undefined);
});
test('system selectors reject missing timezone and do not execute while backgrounded',async()=>{
 let visible=false,calls=0;const p:SystemPort={foreground:()=>visible,status:async()=>'ok',contact:async()=>'',location:async()=>'',calendar:async()=>{calls++;return 1;}};const t=new SystemTools(p,[]);
 await assert.rejects(()=>t.execute('calendar_draft',{title:'Meeting',start_time:'2026-10-30T12:00:00Z',end_time:'2026-10-30T13:00:00Z'},control()),/前台/);
 visible=true;await assert.rejects(()=>t.execute('calendar_draft',{title:'Meeting',start_time:'2026-10-30T12:00:00',end_time:'2026-10-30T13:00:00'},control()),/时区/);assert.equal(calls,0);
 const result=await t.execute('calendar_draft',{title:'Meeting',start_time:'2026-10-30T12:00:00Z',end_time:'2026-10-30T13:00:00Z'},control());assert.equal(result.ok,true);assert.equal(calls,1);
});
test('Markdown tables remain data and unfinished fences keep their text',()=>{
 const blocks=messageBlocks('| Name | Value |\n| --- | --- |\n| A | 1 |');assert.equal(blocks[0].kind,'table');assert.deepEqual(blocks[0].rows,[['Name','Value'],['A','1']]);assert.equal(messageBlocks('```js\n<script>')[0].text,'<script>');
});
test('revision retains old turn and persists usage/context/attachment metadata without replay',async()=>{
 let raw='',i=0;const repo=new ConversationRepository({read:async()=>raw,write:async(v)=>{raw=v;}},()=>String(++i));await repo.load();const id=await repo.create();
 const first=await repo.begin(id,'old','m','','chat');await repo.finish(id,first.turnId,'completed','old reply');
 const run=new ConversationRun(repo,{conversationId:id,text:'edited',modelLabel:'m',profileId:'',mode:'chat',secrets:[],revisionOf:first.turnId,attachments:['a.txt']},(_context,journal)=>({stop(){},async run(){await journal({kind:'usage',text:'',usage:{input:10,output:4,total:14}});await journal({kind:'context',text:'older excerpt'});return 'new reply';}}));await run.run();
 const c=repo.get(id)!;assert.equal(c.turns.length,2);assert.equal(c.turns[0].reply,'old reply');assert.equal(c.turns[1].revisionOf,first.turnId);assert.deepEqual(c.turns[1].usage,{input:10,output:4,total:14});assert.equal(c.turns[1].contextNote,'older excerpt');
});
test('backup preserves editor state but drops wireless address and continuous-background authorization',()=>{
 const b=prepareBackup({version:1,selectedId:'',conversations:[]},{selectedId:'',profiles:[]},emptyExtensions(),[],{target:'192.168.1.1:1234',darkTheme:true,continuousBackground:true},[{id:'c',text:'draft',scroll:50,attachments:['a.txt'],followBottom:false}]);assert.equal(b.settings?.target,undefined);assert.equal(b.settings?.continuousBackground,false);assert.equal(b.editors?.[0].text,'draft');
});

import { ProfileRepository } from '../entry/src/main/ets/core/profiles';
test('session-only provider key is usable by notification continuation but never written or restored cold',async()=>{
 let raw='',i=0;const metadata={read:async()=>raw,write:async(v:string)=>{raw=v;}},secrets={put:async()=>{},get:async()=>'',remove:async()=>{}};const repo=new ProfileRepository(metadata,secrets,()=>String(++i));
 const saved=await repo.save({id:'',name:'Local',baseUrl:'https://a.example/v1',model:'m',screenshots:false,allowInsecureHttp:false},'private-session-key',false);assert.equal(await repo.key(saved),'private-session-key');assert.ok(!raw.includes('private-session-key'));
 const cold=new ProfileRepository(metadata,secrets,()=>String(++i));await cold.load();assert.equal(await cold.key(cold.document.profiles[0]),'');repo.clearSessionKeys();assert.equal(await repo.key(saved),'');
});
test('PNG role export has valid checksums for every image and metadata chunk',()=>{
 const png=rolePng(Buffer.from('{"name":"fixture"}').toString('base64')),view=new DataView(png.buffer);let count=0;
 for(let p=8;p+12<=png.length;){const n=view.getUint32(p);assert.equal(crc32(png.slice(p+4,p+8+n)),view.getUint32(p+8+n));p+=n+12;count++;}assert.equal(count,4);
});
