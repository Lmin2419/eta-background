// Ported from app/src/main/kotlin/io/github/mangi/eta/data/repository/AgentMemoryRepository.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11). AtomicFile and SHA-256 are explicit platform ports.
import {utf8Size} from '../searchSuggestionsBudget';
export interface MemoryStorage {read():Promise<string>;write(content:string):Promise<void>;}
export interface MemorySnapshot {content:string;revision:string;byteSize:number;lineCount:number;}
export interface MemoryReadResult {snapshot:MemorySnapshot;content:string;startLine:number|null;endLine:number|null;hasMore:boolean;matchedLines:number;}
export type MemoryMutation={kind:'replace';revision:string;startLine:number;endLine:number;content:string}|{kind:'append';revision:string;content:string}|{kind:'clear';revision:string};
export interface MemoryWriteResult {kind:'success'|'conflict';snapshot:MemorySnapshot;}
export class AgentMemoryError extends Error{constructor(readonly code:string,message:string){super(message);}}
export const MAX_MEMORY_BYTES=1024*1024;
const lines=(s:string)=>s.length?s.split('\n'):[];
export class AgentMemoryRepository {
 private tail:Promise<void>=Promise.resolve();
 constructor(private storage:MemoryStorage,private hash:(content:string)=>Promise<string>,private enabledStorage:MemoryStorage){}
 private serial<T>(action:()=>Promise<T>):Promise<T>{const p=this.tail.then(action);this.tail=p.then(()=>{},()=>{});return p;}
 private async snapshotOf(content:string):Promise<MemorySnapshot>{return {content,revision:await this.hash(content),byteSize:utf8Size(content),lineCount:lines(content).length};}
 private async readUnlocked():Promise<MemorySnapshot>{let raw:string;try{raw=await this.storage.read();}catch(_){throw new AgentMemoryError('MEMORY_READ_FAILED','无法读取记忆文件');}if(utf8Size(raw)>MAX_MEMORY_BYTES)throw new AgentMemoryError('MEMORY_TOO_LARGE','记忆文件超过 1 MiB 安全上限');return this.snapshotOf(raw);}
 private async writeUnlocked(content:string):Promise<MemorySnapshot>{if(utf8Size(content)>MAX_MEMORY_BYTES)throw new AgentMemoryError('MEMORY_TOO_LARGE','记忆文件不能超过 1 MiB UTF-8 字节');const next=await this.snapshotOf(content);try{await this.storage.write(content);}catch(_){throw new AgentMemoryError('MEMORY_WRITE_FAILED','无法保存记忆文件');}return next;}
 snapshot():Promise<MemorySnapshot>{return this.serial(()=>this.readUnlocked());}
 replaceAll(content:string):Promise<MemorySnapshot>{return this.serial(()=>this.writeUnlocked(content));}
 replaceAllIfRevision(content:string,revision:string):Promise<MemoryWriteResult>{return this.serial(async()=>{const current=await this.readUnlocked();if(current.revision!==revision)return {kind:'conflict',snapshot:current};return {kind:'success',snapshot:await this.writeUnlocked(content)};});}
 mutate(m:MemoryMutation):Promise<MemoryWriteResult>{return this.serial(async()=>{const current=await this.readUnlocked();if(m.revision!==current.revision)return {kind:'conflict',snapshot:current};let value='';if(m.kind==='append')value=!m.content?current.content:!current.content?m.content:current.content.replace(/\n+$/,'')+'\n'+m.content;else if(m.kind==='replace'){const values=lines(current.content);if(m.startLine<1||m.endLine<m.startLine||m.endLine>values.length||!Number.isInteger(m.startLine)||!Number.isInteger(m.endLine))throw new AgentMemoryError('MEMORY_RANGE_INVALID','替换行范围无效；请重新读取记忆后再试');values.splice(m.startLine-1,m.endLine-m.startLine+1,...lines(m.content));value=values.join('\n');}return {kind:'success',snapshot:await this.writeUnlocked(value)};});}
 read(query:string|null=null,startLine=1,maxChars=12000):Promise<MemoryReadResult>{return this.serial(async()=>{const snapshot=await this.readUnlocked(),budget=Math.max(1,Math.min(32000,Math.trunc(maxChars))),values=lines(snapshot.content);if(!query?.trim())return this.page(snapshot,values,startLine,budget);return this.search(snapshot,values,query.trim(),budget);});}
 private page(snapshot:MemorySnapshot,values:string[],startLine:number,maxChars:number):MemoryReadResult{
  const start=Math.max(0,Math.min(values.length,Math.trunc(startLine)-1));if(start>=values.length)return {snapshot,content:'',startLine:null,endLine:null,hasMore:false,matchedLines:0};let output='',end=start;
  while(end<values.length){const rendered=(end+1)+': '+values[end],separator=output?'\n':'';if(output&&output.length+separator.length+rendered.length>maxChars)break;output+=separator;output+=rendered.slice(0,maxChars-output.length);end++;if(output.length>=maxChars)break;}return {snapshot,content:output,startLine:start+1,endLine:end,hasMore:end<values.length,matchedLines:end-start};
 }
 private search(snapshot:MemorySnapshot,values:string[],query:string,maxChars:number):MemoryReadResult{
  const matches=values.map((value,index)=>value.toLowerCase().includes(query.toLowerCase())?index:-1).filter(i=>i>=0),included=new Set<number>();for(const index of matches)for(let i=index-1;i<=index+1;i++)if(i>=0&&i<values.length)included.add(i);let output='',last:number|null=null,count=0;
  for(const index of included){const rendered=(index+1)+': '+values[index],gap=!output?'':last!==null&&index>last+1?'\n…\n':'\n';if(output&&output.length+gap.length+rendered.length>maxChars)break;const take=rendered.slice(0,maxChars-output.length-gap.length);output+=gap+take;last=index;count++;if(output.length>=maxChars)break;}return {snapshot,content:output,startLine:included.size?[...included][0]+1:null,endLine:last===null?null:last+1,hasMore:count<included.size,matchedLines:matches.length};
 }
 isEnabled():Promise<boolean>{return this.serial(async()=>{const raw=await this.enabledStorage.read();if(!raw)return true;if(raw!=='true'&&raw!=='false')throw new AgentMemoryError('MEMORY_READ_FAILED','记忆开关数据无效');return raw==='true';});}
 setEnabled(value:boolean):Promise<void>{return this.serial(()=>this.enabledStorage.write(String(value)));}
}
