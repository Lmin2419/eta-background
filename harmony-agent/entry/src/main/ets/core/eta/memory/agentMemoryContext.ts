// Ported from app/src/main/kotlin/io/github/mangi/eta/agent/memory/AgentMemoryContext.kt,
// ConversationMemoryPins.kt @ 3b1794d. Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import {MemorySnapshot} from './agentMemoryRepository';
export interface AgentMemoryContext {enabled:boolean;revision:string;byteSize:number;coreContent:string;coreTruncated:boolean;headingIndex:string;coreBudgetChars:number;}
export function memoryCoreBudget(window:number|null):number{return Math.max(4000,Math.min(32000,Math.trunc((window!==null&&window>0?window:128000)/16)));}
export function buildMemoryContext(snapshot:MemorySnapshot,window:number|null):AgentMemoryContext{const budget=memoryCoreBudget(window),lines=snapshot.content.split('\n'),start=lines.findIndex(s=>s.trim()==='# 核心记忆');let core='';if(start>=0){let end=start+1;while(end<lines.length&&!lines[end].startsWith('# '))end++;core=lines.slice(start,end).join('\n');}return {enabled:true,revision:snapshot.revision,byteSize:snapshot.byteSize,coreContent:core.slice(0,budget),coreTruncated:core.length>budget,headingIndex:lines.filter(s=>/^#{1,2}\s+.+$/.test(s.trimEnd())).join('\n').slice(0,4000),coreBudgetChars:budget};}
export class ConversationMemoryPins {
 private pins=new Map<string,{context:AgentMemoryContext;window:number|null;revision:string}>();
 contextFor(id:string|null,snapshot:MemorySnapshot,window:number|null):AgentMemoryContext{const pin=id!==null?this.pins.get(id):undefined;if(pin&&pin.revision===snapshot.revision&&pin.window===window){this.pins.delete(id!);this.pins.set(id!,pin);return pin.context;}const context=buildMemoryContext(snapshot,window);if(id!==null){this.pins.delete(id);this.pins.set(id,{context,window,revision:snapshot.revision});if(this.pins.size>32)this.pins.delete(this.pins.keys().next().value!);}return context;}
 recordOwnWrite(id:string|null,revision:string):void{const pin=id!==null?this.pins.get(id):undefined;if(pin&&revision.trim()){this.pins.delete(id!);pin.revision=revision;this.pins.set(id!,pin);}}
 forget(id:string|null):void{if(id!==null)this.pins.delete(id);}
}
