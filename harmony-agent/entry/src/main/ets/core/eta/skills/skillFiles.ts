// Ported from app/src/main/kotlin/io/github/mangi/eta/agent/skill/SkillMutationLock.kt, SkillRecoveryJournal.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11). All paths are relative to one private application directory.
import {SkillRecoveryRequired,SKILL_ROOT,SKILL_WORK,skillFail} from './skillTypes';
export type SkillFileKind='file'|'directory'|'link'|'other'|null;
export interface SkillFileSystem {
 kind(path:string):Promise<SkillFileKind>;list(path:string):Promise<string[]>;mkdir(path:string):Promise<void>;
 read(path:string,maxBytes:number):Promise<Uint8Array>;write(path:string,bytes:Uint8Array):Promise<void>;
 /** Flush data then rename in the same directory. */
 atomicWrite(path:string,bytes:Uint8Array):Promise<void>;
 move(source:string,target:string):Promise<void>;unlink(path:string):Promise<void>;rmdir(path:string):Promise<void>;
 /** Exclusive process/file lock; filesystem calls must reject symlink ancestors. */
 lock():Promise<()=>Promise<void>>;
 encode(text:string):Uint8Array;decode(bytes:Uint8Array):string;uuid():string;
}
export async function prepareSkillRoots(fs:SkillFileSystem):Promise<void>{for(const path of [SKILL_WORK,SKILL_ROOT]){const kind=await fs.kind(path);if(kind===null)await fs.mkdir(path);else if(kind!=='directory')skillFail('IO_ERROR');}}
export async function makeSkillOperation(fs:SkillFileSystem):Promise<string>{await prepareSkillRoots(fs);for(let n=0;n<8;n++){const path=SKILL_WORK+'/operation-'+fs.uuid();if(await fs.kind(path)===null){await fs.mkdir(path);return path;}}skillFail('IO_ERROR');}
export async function removeSkillTree(fs:SkillFileSystem,path:string):Promise<void>{const kind=await fs.kind(path);if(kind===null)return;if(kind==='directory'){for(const name of await fs.list(path))await removeSkillTree(fs,path+'/'+name);await fs.rmdir(path);}else await fs.unlink(path);}
export async function safeSkillTree(fs:SkillFileSystem,path:string):Promise<boolean>{if(await fs.kind(path)!=='directory')return false;for(const name of await fs.list(path)){const child=path+'/'+name,kind=await fs.kind(child);if(kind==='directory'){if(!await safeSkillTree(fs,child))return false;}else if(kind!=='file')return false;}return true;}
export async function copySkillTree(fs:SkillFileSystem,from:string,to:string):Promise<void>{if(await fs.kind(from)!=='directory')throw new SkillRecoveryRequired();await fs.mkdir(to);for(const name of await fs.list(from)){const child=from+'/'+name,kind=await fs.kind(child);if(kind==='directory')await copySkillTree(fs,child,to+'/'+name);else if(kind==='file')await fs.write(to+'/'+name,await fs.read(child,128*1024*1024));else throw new SkillRecoveryRequired();}}
export async function skillFiles(fs:SkillFileSystem,root:string):Promise<string[]>{const result:string[]=[];if(await fs.kind(root)!=='directory')return result;for(const name of await fs.list(root)){if(name==='.git')continue;const path=root+'/'+name,kind=await fs.kind(path);if(kind==='directory')result.push(...await skillFiles(fs,path));else if(kind==='file'&&name==='SKILL.md')result.push(path);}return result;}
export type SkillMover=(source:string,target:string)=>Promise<void>;
