export type CapabilityState='available'|'permission_required'|'disconnected'|'unsupported'|'unverified';
export interface Capability {id:string;name:string;state:CapabilityState;reason:string;identity:string;checkedAt:number}
export class CapabilityRegistry {
  private values:Map<string,Capability>=new Map();
  set(id:string,name:string,state:CapabilityState,reason:string,identity='application'):void{this.values.set(id,{id,name,state,reason,identity,checkedAt:Date.now()});}
  get(id:string):Capability|undefined{return this.values.get(id);}
  available(id:string):boolean{return this.values.get(id)?.state==='available';}
  list():Capability[]{return Array.from(this.values.values()).map(c=>({...c}));}
  require(id:string):void{const c=this.get(id);if(c?.state!=='available')throw new Error(c?.reason||'能力尚未验证');}
  report():string{return this.list().map(c=>c.name+'：'+c.state+'\n'+c.reason+'\n身份：'+c.identity+'；检查：'+new Date(c.checkedAt).toISOString()).join('\n\n');}
}
