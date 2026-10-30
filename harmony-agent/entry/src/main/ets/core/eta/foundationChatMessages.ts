// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/components/AgentChatBody.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { MessageRow } from '../foundation/controller';
import { AgentChatMessageUi, agentMessage, thinkingMessage, userMessage, noticeMessage } from './model/conversation';
/** Durable 0.9 turn schema to Eta's message identities. Never infer or execute tools. */
export function foundationChatMessages(rows:MessageRow[],previous:AgentChatMessageUi[]=[]):AgentChatMessageUi[] {
  const old=new Map(previous.map(m=>[m.id,m]));
  const result:AgentChatMessageUi[]=[];
  function add(value:AgentChatMessageUi):void {const before=old.get(value.id);result.push(before&&JSON.stringify(before)===JSON.stringify(value)?before:value);}
  for(const row of rows){
    add(userMessage(row.id+'-user',row.request));
    if(row.reasoning||(row.active&&!row.reply))add(thinkingMessage(row.id+'-thinking',row.reasoning,row.active&&!row.reply));
    if(row.reply)add(agentMessage(row.id+'-assistant',row.reply,row.active));
    if(row.notice)add(noticeMessage(row.id+'-notice',row.resultStatus==='stopped'?'Stopped':row.resultStatus==='interrupted'?'Interrupted':'RuntimeFailed',row.notice));
    else if(!row.active&&!row.reply)add(noticeMessage(row.id+'-notice','EmptyResult'));
  }
  return result;
}
