// Harmony bounded Deflate port for app/src/main/kotlin/io/github/mangi/eta/agent/skill/SkillPackageInstaller.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
export interface SkillInflateCounts {input:number;output:number;}
export interface SkillInflateSession {initialize(input:ArrayBuffer,output:ArrayBuffer):Promise<void>;inflate():Promise<boolean>;counts():Promise<SkillInflateCounts>;end():Promise<void>;}
/** OHOS getZStream returns counters only. Keep our output buffer alive and never expect nextOut in its result. */
export async function inflateSkillBytes(session:SkillInflateSession,bytes:Uint8Array,expected:number):Promise<Uint8Array>{if(!Number.isInteger(expected)||expected<0||expected>32*1024*1024)throw new Error('SKILL_INFLATE_SIZE');const input=new Uint8Array(bytes).buffer,output=new ArrayBuffer(expected+1);await session.initialize(input,output);try{const ended=await session.inflate(),counts=await session.counts();if(!ended||counts.output!==expected||counts.input!==bytes.length)throw new Error('SKILL_INFLATE_INVALID');return new Uint8Array(output).slice(0,expected);}finally{await session.end();}}
