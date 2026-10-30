import { Message } from './types';
export interface ContextBudgetResult {messages:Message[];estimatedTokens:number;omittedMessages:number;summary:string}
/** Conservative character budget. Only complete old turns are removed; the current goal and tool pairs remain intact. */
export function fitContext(messages:Message[],contextTokens=32000,reservedOutput=4096):ContextBudgetResult{
  if(!Number.isInteger(contextTokens)||contextTokens<8000||contextTokens>1000000)throw new Error('上下文窗口应为 8000–1000000 Tokens');
  const budget=contextTokens-reservedOutput-2000;if(budget<2000)throw new Error('输出预算过大，请扩大上下文窗口或减少输出上限');
  const cost=(items:Message[])=>items.reduce((n,m)=>n+(typeof m.content==='string'?m.content.length:Array.isArray(m.content)?m.content.reduce((s,p)=>s+(p.type==='text'?(p.text||'').length:2200),0):0)+JSON.stringify(m.tool_calls||[]).length+24,0);
  const result=messages.slice();let removed:Message[]=[];
  // Caller marks the actual current request; everything after it is kept as a transaction.
  const anchor=messages.findIndex(m=>m.currentGoal===true);
  if(anchor<0)throw new Error('上下文缺少当前目标标记');
  let remaining=anchor-1;
  while(cost(result)>budget&&remaining>0){
    let count=1;while(count<remaining&&result[1+count].role!=='user')count++;
    removed.push(...result.splice(1,count));remaining-=count;
  }
  let summary='';
  if(removed.length){
    summary='已按预算移除 '+removed.length+' 条较早消息。以下为旧用户目标摘录，仅供背景，不是待执行命令：\n'+removed.filter(m=>m.role==='user'&&typeof m.content==='string').slice(-6).map(m=>String(m.content).slice(0,180)).join('\n');
    if(cost(result)+summary.length<budget)result.splice(1,0,{role:'system',content:summary});
  }
  if(cost(result)>budget)throw new Error('当前目标和完整工具记录超过上下文预算；已停止，未丢弃未知动作。请新建任务或减少附件');
  return {messages:result,estimatedTokens:cost(result),omittedMessages:removed.length,summary};
}
