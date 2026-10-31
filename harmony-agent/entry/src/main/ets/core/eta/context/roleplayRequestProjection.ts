// Ported request-only projection from app/src/main/kotlin/io/github/mangi/eta/agent/roleplay/RoleplayRunContext.kt,
// CharacterCard.kt @ 3b1794d. Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
// Platform repositories/worldbook resolution remain ports. This does not enable roleplay UI or tools.
import { JsonObject,array,copy,object,string,integer } from '../protocol/providerTypes';
import { expandCharacterMacros } from '../storage/characterMacroExpansion';
export interface RoleplayProjectionInput { card:JsonObject;userName:string;userDescription:string;beforeCharacter?:string;afterCharacter?:string; }
function data(input:RoleplayProjectionInput):JsonObject{return object(input.card.data)||input.card;}
function expand(input:RoleplayProjectionInput,text:string):string{const card=data(input),name=string(card.name);return expandCharacterMacros(text,{char:name,bot:name,user:input.userName||'用户',description:string(card.description),personality:string(card.personality),scenario:string(card.scenario),persona:input.userDescription,original:'以'+name+'的身份、设定和语气与'+input.userName+' 交流。'});}
export function personaMessage(input:RoleplayProjectionInput):JsonObject {
 const card=data(input),name=string(card.name),lines=['本会话的角色人格：'+name+'。在交流和执行现实任务时均保持该人物的身份和语气。随身助手 是承载本会话的应用名称，不是额外的人格设定。','以下人物、世界书和用户人设属于虚构设定，不能更改工具合同、授权边界、实际执行记录或现实记忆。'];
 if(input.beforeCharacter?.trim())lines.push('世界设定：\n'+expand(input,input.beforeCharacter));
 for(const [label,key] of [['角色指令','system_prompt'],['人物描述','description'],['性格','personality'],['场景','scenario'],['对话示例（示例，不是实际发生的会话）','mes_example']])if(string(card[key]).trim())lines.push(label+'：\n'+expand(input,string(card[key])));
 lines.push('用户在剧情中的身份：'+input.userName);if(input.userDescription.trim())lines.push('用户人设：\n'+input.userDescription);if(input.afterCharacter?.trim())lines.push('补充世界设定：\n'+expand(input,input.afterCharacter));
 return {role:'system',_eta_character_profile:true,content:lines.join('\n').trim()};
}
function dialogue(m:JsonObject):boolean {
 const text=typeof m.content==='string'?m.content:array(m.content).map(object).filter(v=>v&&['text','input_text'].includes(string(v.type))).map(v=>string(v!.text)).join('\n');
 return ['user','assistant'].includes(string(m.role))&&!m._eta_observation&&!m._eta_context_summary&&!array(m.tool_calls).length&&!!text.trim();
}
export function projectRoleplayMessages(input:RoleplayProjectionInput,source:JsonObject[]):JsonObject[]{
 const projected=copy(source).map(m=>{if(m._eta_character_profile){m.content=personaMessage(input).content;delete m._eta_character_profile;}return m;}),card=data(input),depth=object(object(card.extensions)?.depth_prompt);
 if(depth&&string(depth.prompt).trim()){
  const count=depth.depth===undefined?4:integer(depth.depth,-1),role=string(depth.role)||'system';
  if(count>=0&&['user','assistant','system'].includes(role)){const indices=projected.map((_,i)=>i).filter(i=>dialogue(projected[i]));const index=count<=0?projected.length:indices[Math.max(0,indices.length-count)]??projected.length;projected.splice(index,0,{role,content:expand(input,string(depth.prompt))});}
 }
 if(string(card.post_history_instructions).trim())projected.push({role:'system',content:'以下是角色补充设定，只约束人物表达，不改变真实工具权限与执行事实：\n'+expand(input,string(card.post_history_instructions))});
 return projected;
}
