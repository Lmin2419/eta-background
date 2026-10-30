// Ported behavior boundaries from ConversationPanePanel.kt / AgentConversationStoreTest.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
// Store cases below translate the 0.9 adapter seam, not Android Room execution.
import test from 'node:test';
import assert from 'node:assert/strict';
import { FoundationController } from '../../entry/src/main/ets/core/foundation/controller';
import { ConversationRepository } from '../../entry/src/main/ets/core/conversations';
import { DraftRepository } from '../../entry/src/main/ets/core/drafts';
import { ProfileRepository } from '../../entry/src/main/ets/core/profiles';
import { groupForDrawer, conversationCaseFold, messageMatchesQuery, messagePage, boundedConversationPreview, PaneDateContext, ConversationSummaryUi } from '../../entry/src/main/ets/core/eta/conversationDrawer';
import { emptyChatState, agentMessage, userMessage, noticeMessage } from '../../entry/src/main/ets/core/eta/model/conversation';
import { ExportLabels } from '../../entry/src/main/ets/core/eta/conversationMarkdownExporter';
class Storage {raw='';fail=false;async read(){return this.raw;}async write(raw:string){if(this.fail)throw Error('disk full');this.raw=raw;}}
const labels:ExportLabels={user:'User',assistant:'Assistant',thinking:'Thinking',toolLineFormat:'%s %s',toolsLineFormat:'%s',argumentsFormat:'%s',resultFormat:'%s',imagesFormat:'%d',toolStatusRunning:'Running',toolStatusSuccess:'Success',toolStatusFailed:'Failed',toolStatusUnknown:'Unknown',noticeStopped:'Stopped',noticeEmptyResult:'Empty',noticeModelRetry:'Retry',noticeRuntimeFailed:'Failed',noticeInterrupted:'Interrupted'};
function dateContext():PaneDateContext{return {now:Date.UTC(2026,9,31),locale:'en-US',timeZone:'UTC',use24HourClock:true,yesterdayLabel:'Yesterday',recentLabel:'Recent',formatter:{calendarDate:t=>{const d=new Date(t);return {year:d.getUTCFullYear(),month:d.getUTCMonth()+1,day:d.getUTCDate()};},format:()=> 'label',mediumDate:()=> 'date'}};}
async function fixture(){const storage=new Storage(),drafts=new DraftRepository(new Storage()),profiles=new ProfileRepository(new Storage(),{put:async()=>{},get:async()=>'',remove:async()=>{}},()=> 'profile');await profiles.load();let n=0;let c:FoundationController;
 const chats=new ConversationRepository(storage,()=>String(++n),Date.now,()=>c?.changed(),2);const exports:{name:string;text:string}[]=[];
 c=new FoundationController({chats,profiles,drafts,uuid:()=>String(++n),load:async()=>{await chats.load();await drafts.load();},transport:()=>({post:async()=>{throw Error('no network');},cancel:()=>{}}),paneDate:dateContext,exportLabels:()=>labels,exportMarkdown:async(name,text)=>{exports.push({name,text});return true;}});await c.initialize();return {c,chats,storage,exports};}
async function turn(chats:ConversationRepository,id:string,request:string,reply:string){const t=await chats.begin(id,request,'model','profile','chat');await chats.finish(id,t.turnId,'completed',reply);}
test('drawer groups adjacent dates in source order; active runs count as Today without merging nonadjacent groups',()=>{
 const make=(id:string,time:number,active=false,pinned=false):ConversationSummaryUi=>({id,title:id,preview:'',updatedAtMillis:time,timeLabel:'Yesterday',isActiveRun:active,runStatus:null,isPinned:pinned});
 const context=dateContext(),now=context.now;
 const result=groupForDrawer([make('pin',now-86400000,false,true),make('today',now),make('old',now-86400000),make('running',now-172800000,true),make('yesterday',now-86400000)],context);
 assert.deepEqual(result.map(g=>g.section),['Pinned','Today','Dated','Today','Dated']);assert.deepEqual(result.flatMap(g=>g.items.map(i=>i.id)),['pin','today','old','running','yesterday']);
});
test('logic: loadKeepsDatabaseEmptyUntilFirstMessageIsSent',async()=>{const f=await fixture();assert.deepEqual(JSON.parse(f.storage.raw).conversations,[]);assert.deepEqual(f.c.snapshot().conversations,[]);await f.c.flush();});
test('logic: creatingConversationKeepsEmptyStateOutOfHistoryAndDatabase',async()=>{const f=await fixture();f.c.draft('not submitted');await f.c.newConversation();assert.equal(f.c.snapshot().conversations.length,0);assert.equal(JSON.parse(f.storage.raw).conversations.length,0);await f.c.flush();});
test('logic: conversationModelBindingRoundTripsAndChangeAloneIsPersisted',async()=>{const f=await fixture(),id=f.c.snapshot().chat.id;await turn(f.chats,id,'hello','world');await f.chats.preferences(id,'p','chat','model-x');await f.chats.preferences(id,'p','chat','model-y');const restored=new ConversationRepository(f.storage,()=> 'new');await restored.load();assert.equal(restored.get(id)?.preferredModel,'model-y');await f.c.flush();});
test('logic: searchFindsUnloadedChunkBoundariesAndTreatsSqlWildcardsLiterally',()=>{
 const message=agentMessage('literal','x'.repeat(16381)+'跨越边界 100%_done');for(const query of ['跨越边界','%_','DONE'])assert.equal(messageMatchesQuery(message,query),true);for(const query of ['eta:chunks','[]'])assert.equal(messageMatchesQuery(message,query),false);
});
test('logic: searchRetainsUnicodeCaseFoldingAndQueriesSpanningManyChunks',()=>{
 assert.equal(messageMatchesQuery(agentMessage('unicode','äpfel'),'ÄPFEL'),true);const query='开始'+'a'.repeat(60000)+'结束';assert.equal(messageMatchesQuery(agentMessage('large','前缀'+query+' 后缀'),query),true);assert.equal(messageMatchesQuery(agentMessage('fold','İKſ'),'iks'),true);assert.notEqual(conversationCaseFold('ß'),conversationCaseFold('ss'));
});
test('logic: searchIncludesMatchingLocalizedNoticeCodesFromEarlierMessages',()=>{assert.equal(messageMatchesQuery(noticeMessage('old','Interrupted'),'已中断',['Interrupted']),true);assert.equal(messageMatchesQuery(agentMessage('new','新的回答'),'已中断',['Interrupted']),false);});
test('logic: initialLoadKeepsOnlyTheSelectedMessageWindowAndEarlierPagesRemainComplete',()=>{
 const messages=[userMessage('long','x'.repeat(20000)),...Array.from({length:130},(_,i)=>agentMessage(String(i),'assistant-'+i))];const recent=messagePage(messages);assert.equal(recent.messages.length,128);assert.equal(recent.startIndex,3);assert.equal(recent.hasEarlierMessages,true);const earlier=messagePage(messages,recent.startIndex);assert.equal(earlier.startIndex,0);assert.deepEqual([...earlier.messages,...recent.messages],messages);
});
test('logic: unopenedConversationOnlyReadsItsBoundedPreview',()=>{
 const state=emptyChatState();state.messages=[agentMessage('last','内容'.repeat(20000))];const preview=boundedConversationPreview(state);assert.equal(preview.isConversationLoaded,false);assert.equal(preview.history.length,0);assert.equal(preview.journal.length,0);assert.equal((preview.messages[0] as {content:string}).content.length,256);
});
test('logic: savingEmptySnapshotClearsPreviouslyPersistedConversations',async()=>{const f=await fixture(),id=f.c.snapshot().chat.id;await turn(f.chats,id,'hello','world');f.c.requestPaneMutation(id,'delete');await f.c.confirmPaneMutation();assert.equal(JSON.parse(f.storage.raw).conversations.length,0);assert.equal(f.c.snapshot().conversations.length,0);await f.c.flush();});
test('export includes all history independent of the 128-message visible window; no request is sent',async()=>{
 const f=await fixture(),id=f.c.snapshot().chat.id;for(let i=0;i<70;i++)await turn(f.chats,id,'question-'+i,'answer-'+i);await f.c.exportConversation(id);assert.equal(f.exports.length,1);assert.match(f.exports[0].text,/question-0/);assert.match(f.exports[0].text,/answer-69/);assert.match(f.exports[0].name,/\.md$/);await f.c.flush();
});
test('rename dialog owns its draft and a failed save retains original title',async()=>{
 const f=await fixture(),id=f.c.snapshot().chat.id;await turn(f.chats,id,'hello','world');f.c.requestPaneMutation(id,'rename');f.c.paneRenameInput('renamed');f.storage.fail=true;await f.c.confirmPaneMutation();assert.notEqual(f.chats.get(id)?.title,'renamed');assert.equal(f.c.snapshot().paneMutation?.input,'renamed');f.storage.fail=false;await f.c.confirmPaneMutation();assert.equal(f.chats.get(id)?.title,'renamed');assert.equal(f.c.snapshot().paneMutation,null);await f.c.flush();
});
