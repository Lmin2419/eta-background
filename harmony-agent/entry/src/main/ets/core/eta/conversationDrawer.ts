// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/components/ConversationPanePanel.kt,
// ui/app/AgentConversationStore.kt, ConversationTimeLabels.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { ConversationDateFormat, ConversationTimeLabels } from './conversationTimeLabels';
import { AgentChatMessageUi, AgentChatUiState, SystemNoticeCode } from './model/conversation';
import { EtaMessageDefaults } from './tokens';
export interface ConversationSummaryUi {id:string;title:string;preview:string;updatedAtMillis:number;timeLabel:string;isActiveRun:boolean;runStatus:string|null;isPinned:boolean;}
export interface ConversationDrawerGroup {id:string;section:'Pinned'|'Today'|'Dated';label:string;items:ConversationSummaryUi[];}
export interface PaneDateContext {now:number;locale:string;timeZone:string;use24HourClock:boolean;yesterdayLabel:string;recentLabel:string;formatter:ConversationDateFormat;}
export function groupForDrawer(conversations:ConversationSummaryUi[],context:PaneDateContext):ConversationDrawerGroup[]{
  const groups:ConversationDrawerGroup[]=[];const now=context.formatter.calendarDate(context.now,context.timeZone);
  for(const c of conversations){
    const target=context.formatter.calendarDate(c.updatedAtMillis,context.timeZone);
    const today=c.updatedAtMillis<=0||(target.year===now.year&&target.month===now.month&&target.day===now.day);
    const section=c.isPinned?'Pinned':c.isActiveRun||today?'Today':'Dated';
    const label=section==='Dated'?c.timeLabel:'';const last=groups[groups.length-1];
    if(last&&last.section===section&&last.label===label)last.items.push(c);
    else groups.push({id:section+'-'+c.id,section,label,items:[c]});
  }return groups;
}
export function conversationTimeLabel(timestamp:number,context:PaneDateContext):string{return ConversationTimeLabels.label(timestamp,context.now,context.locale,context.timeZone,context.formatter,context.use24HourClock,context.yesterdayLabel,context.recentLabel);}
/** Java's case-insensitive matching folds dotted/dotless I, Kelvin and long S without expanding ß. */
export function conversationCaseFold(value:string):string{return Array.from(value).map(c=>{const upper=c.toUpperCase();const one=Array.from(upper).length===1?upper:c;return one==='İ'?'i':one.toLowerCase();}).join('');}
export function messageMatchesQuery(message:AgentChatMessageUi,query:string,notices:SystemNoticeCode[]=[]):boolean{
  if(message.kind==='notice'&&notices.includes(message.code))return true;
  const value='content' in message?message.content:message.kind==='notice'?message.detail||'':message.kind==='tool'?[message.toolName,message.argumentsSummary,message.command,message.resultSummary].join('\n'):'';
  return conversationCaseFold(value).includes(conversationCaseFold(query));
}
export interface MessagePage {messages:AgentChatMessageUi[];startIndex:number;hasEarlierMessages:boolean;}
export function messagePage(messages:AgentChatMessageUi[],before=messages.length,pageSize=EtaMessageDefaults.PageSize):MessagePage{
  const end=Math.max(0,Math.min(before,messages.length)),start=Math.max(0,end-Math.max(1,pageSize));
  return {messages:messages.slice(start,end),startIndex:start,hasEarlierMessages:start>0};
}
export function boundedConversationPreview(state:AgentChatUiState):AgentChatUiState{
  const last=state.messages[state.messages.length-1];let preview=last;
  if(last&&'content' in last)preview={...last,content:last.content.slice(0,256)};
  if(preview?.kind==='user')preview={...preview,images:[]};
  if(preview?.kind==='assistant')preview={...preview,searchSuggestionsHtml:[]};
  if(preview?.kind==='tool')preview={...preview,argumentsSummary:preview.argumentsSummary.slice(0,256),command:preview.command?.slice(0,256)||null,resultSummary:preview.resultSummary?.slice(0,256)||null};
  return {...state,messages:preview?[preview]:[],input:'',history:[],journal:[],isStreaming:false,roleplay:null,roleplayOriginalContents:new Map(),appliedRuntimeRunIds:[],isConversationLoaded:false,messageStartIndex:0,hasEarlierMessages:false};
}
