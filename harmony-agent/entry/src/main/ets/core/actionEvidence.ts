import { Observation, UiNode } from './types';
export type ActionOutcome='REJECTED'|'CHANGED'|'NO_OBSERVED_CHANGE'|'UNKNOWN';
export interface ActionEvidence {accepted:boolean;outcome:ActionOutcome;operationMayHaveRun:boolean;beforeObservationId:string;afterObservationId:string;goalVerified:false}
function comparable(a:Observation,b:Observation):boolean{return !a.truncated&&!b.truncated&&a.width===b.width&&a.height===b.height&&(a.rotation||0)===(b.rotation||0)&&(a.displayId||0)===(b.displayId||0);}
export function evidence(before:Observation|undefined,after:Observation|undefined,accepted:boolean):ActionEvidence{
  const matched=Boolean(before&&after&&comparable(before,after));
  return {accepted,outcome:!accepted?'REJECTED':!matched?'UNKNOWN':before!.fingerprint!==after!.fingerprint?'CHANGED':'NO_OBSERVED_CHANGE',operationMayHaveRun:accepted,beforeObservationId:before?.id||'',afterObservationId:after?.id||'',goalVerified:false};
}
export class NoProgressTracker {
  private entries:Map<string,{fingerprint:string;count:number;at:number}>=new Map();
  constructor(private now:()=>number=Date.now){}
  blocked(key:string,before:Observation):boolean{
    const entry=this.entries.get(key);if(!entry)return false;
    if(before.truncated||entry.fingerprint!==before.fingerprint||this.now()-entry.at>120000){this.entries.delete(key);return false;}
    return entry.count>=3;
  }
  record(key:string,before:Observation|undefined,after:Observation|undefined):void{
    if(!before||!after||evidence(before,after,true).outcome!=='NO_OBSERVED_CHANGE'){this.entries.delete(key);return;}
    const old=this.entries.get(key);this.entries.delete(key);
    this.entries.set(key,{fingerprint:after.fingerprint,count:old&&old.fingerprint===before.fingerprint&&this.now()-old.at<=120000?old.count+1:1,at:this.now()});
    while(this.entries.size>64)this.entries.delete(this.entries.keys().next().value!);
  }
  clear():void{this.entries.clear();}
}
export function scrollTarget(nodes:UiNode[],id:unknown):UiNode {
  const candidates=nodes.filter(n=>n.scrollable);
  if(typeof id==='number'){const found=candidates.find(n=>n.id===id);if(!found)throw new Error('目标不是已识别的滚动容器');return found;}
  if(candidates.length!==1)throw new Error(candidates.length?'存在多个滚动容器，请指定 node_id':'未识别滚动容器，请使用明确的坐标滑动');
  return candidates[0];
}
export function targetObservation(observation:Observation,target:UiNode):Observation{
  const b=target.bounds;
  const nodes=observation.nodes.filter(n=>n.id!==target.id&&n.bounds.left>=b.left&&n.bounds.right<=b.right&&n.bounds.top>=b.top&&n.bounds.bottom<=b.bottom);
  return {...observation,fingerprint:JSON.stringify(nodes.map(n=>[n.text,n.type,n.bounds.left-b.left,n.bounds.top-b.top,n.bounds.right-b.left,n.bounds.bottom-b.top])),truncated:observation.truncated||nodes.length===0};
}
