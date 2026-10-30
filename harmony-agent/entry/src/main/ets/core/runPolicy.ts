import { ExtensionDocument } from './extensions';
import { SettingValue } from './settings';

export interface RuntimeOptions {
  useWorkspace:boolean;useWeb:boolean;useSystem:boolean;memoryEnabled:boolean;
  searchUrl:string;contextTokens:number;continuousBackground:boolean;completionAlerts:boolean;inlineReply:boolean;
}
export function runtimeOptions(values:Record<string,SettingValue>):RuntimeOptions {
  return {useWorkspace:values.useWorkspace!==false,useWeb:values.useWeb===true,useSystem:values.useSystem===true,
    memoryEnabled:values.memoryEnabled===true,searchUrl:typeof values.searchUrl==='string'?values.searchUrl:'',
    contextTokens:typeof values.contextTokens==='number'?values.contextTokens:32000,continuousBackground:values.continuousBackground===true,
    completionAlerts:values.completionAlerts!==false,inlineReply:values.inlineReply===true};
}
/** Only execution-affecting settings enter the key. Presentation changes do not cancel a run. */
export function policyKey(document:ExtensionDocument,conversationId:string,options:RuntimeOptions):string {
  const role=document.roles.find(r=>r.id===document.roleSelections[conversationId]);
  return JSON.stringify({role:role||null,skills:document.skills.filter(s=>s.enabled),mcp:document.mcp.filter(m=>m.enabled&&m.enabledTools.length),
    secondary:document.subtasks?document.secondaryProfileId:'',workspace:options.useWorkspace,web:options.useWeb,system:options.useSystem,
    memory:options.memoryEnabled,search:options.useWeb?options.searchUrl:'',background:options.continuousBackground});
}
/** Revoke synchronously before awaiting persistence or another model round. */
export class PolicyWatch {
  private runs:Map<string,{conversationId:string;key:string;revoke:()=>void}>=new Map();
  watch(id:string,conversationId:string,key:string,revoke:()=>void):void{this.runs.set(id,{conversationId,key,revoke});}
  release(id:string):void{this.runs.delete(id);}
  refresh(key:(conversationId:string)=>string):void{
    for(const [id,run] of this.runs)if(run.key!==key(run.conversationId)){this.runs.delete(id);run.revoke();}
  }
}
