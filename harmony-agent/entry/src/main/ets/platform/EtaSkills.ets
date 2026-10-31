// Harmony port for app/src/main/kotlin/io/github/mangi/eta/agent/skill/SkillRuntime.kt, SkillPackageInstaller.kt, SkillMutationLock.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11). No script execution, no arbitrary external paths, no new permissions.
import {common} from '@kit.AbilityKit';
import {fileIo,picker} from '@kit.CoreFileKit';
import {util} from '@kit.ArkTS';
import {cryptoFramework} from '@kit.CryptoArchitectureKit';
import {BusinessError,zlib} from '@kit.BasicServicesKit';
import {SkillFileSystem,SkillFileKind} from '../core/eta/skills/skillFiles';
import {SkillZipCodec} from '../core/eta/skills/skillZip';
import {SkillInflateSession,SkillInflateCounts,inflateSkillBytes} from '../core/eta/skills/skillInflater';
import {SkillInstallError,SkillRegistry,SkillRegistryEntry} from '../core/eta/skills/skillTypes';
import {SkillIndexService} from '../core/eta/skills/skillIndex';
import {SkillPackageInstaller,SkillArchiveReader} from '../core/eta/skills/skillInstaller';
import {BUILTIN_SKILLS} from '../core/eta/skills/builtinSkills';
import {SkillsPorts} from '../core/eta/skills/agentSkillsStore';
import {toSkillZipOutcome} from '../core/eta/skills/skillZipGateway';
import {decodeSkillRegistry} from '../core/eta/skills/skillRegistryCodec';
import {RuntimeDatabase,DatabaseDocumentStorage} from './RuntimeStorage';

class HarmonySkillFiles implements SkillFileSystem {
 private root:string;
 constructor(context:common.UIAbilityContext){this.root=context.filesDir+'/eta-skills-v1';}
 private async rawKind(path:string):Promise<SkillFileKind>{try{const stat=await fileIo.lstat(path);return stat.isSymbolicLink()?'link':stat.isDirectory()?'directory':stat.isFile()?'file':'other';}catch(error){if((error as BusinessError).code===13900002)return null;throw new Error('SKILL_FILE_STAT_FAILED');}}
 private async guard(path:string):Promise<void>{const parts=path.split('/');if(!path||parts.some(p=>!p||p==='.'||p==='..'||p.includes('\\')||p.includes('\0')))throw new Error('SKILL_PATH_INVALID');let rootKind=await this.rawKind(this.root);if(rootKind===null){try{await fileIo.mkdir(this.root);}catch(_){}rootKind=await this.rawKind(this.root);}if(rootKind!=='directory')throw new Error('SKILL_ROOT_INVALID');for(let i=1;i<parts.length;i++){const kind=await this.rawKind(this.root+'/'+parts.slice(0,i).join('/'));if(kind!==null&&kind!=='directory')throw new Error('SKILL_ANCESTOR_INVALID');}}
 async kind(path:string):Promise<SkillFileKind>{await this.guard(path);return this.rawKind(this.root+'/'+path);}
 async list(path:string):Promise<string[]>{if(await this.kind(path)!=='directory')throw new Error('SKILL_DIRECTORY_INVALID');return fileIo.listFile(this.root+'/'+path,{recursion:false});}
 async mkdir(path:string):Promise<void>{const kind=await this.kind(path);if(kind===null){try{await fileIo.mkdir(this.root+'/'+path,true);}catch(error){if(await this.kind(path)!=='directory')throw new Error('SKILL_MKDIR_FAILED');}}else if(kind!=='directory')throw new Error('SKILL_DIRECTORY_INVALID');}
 async read(path:string,maxBytes:number):Promise<Uint8Array>{if(await this.kind(path)!=='file')throw new Error('SKILL_FILE_INVALID');return readSkillFile(this.root+'/'+path,maxBytes,false);}
 async write(path:string,bytes:Uint8Array):Promise<void>{const kind=await this.kind(path);if(kind!==null&&kind!=='file')throw new Error('SKILL_FILE_INVALID');const file=await fileIo.open(this.root+'/'+path,fileIo.OpenMode.CREATE|fileIo.OpenMode.TRUNC|fileIo.OpenMode.READ_WRITE);try{let offset=0;while(offset<bytes.length){const remaining=bytes.slice(offset),n=await fileIo.write(file.fd,remaining.buffer,{offset});if(n<=0)throw new Error('SKILL_SHORT_WRITE');offset+=n;}await fileIo.fsync(file.fd);}finally{await fileIo.close(file);}}
 async atomicWrite(path:string,bytes:Uint8Array):Promise<void>{const temporary=path+'.tmp-'+this.uuid();try{await this.write(temporary,bytes);await this.move(temporary,path);}finally{if(await this.kind(temporary)!==null)await this.unlink(temporary);}}
 async move(from:string,to:string):Promise<void>{await this.guard(from);await this.guard(to);await fileIo.rename(this.root+'/'+from,this.root+'/'+to);}
 async unlink(path:string):Promise<void>{await this.guard(path);await fileIo.unlink(this.root+'/'+path);}
 async rmdir(path:string):Promise<void>{if(await this.kind(path)!=='directory')throw new Error('SKILL_DIRECTORY_INVALID');if((await this.list(path)).length)throw new Error('SKILL_DIRECTORY_NOT_EMPTY');await fileIo.rmdir(this.root+'/'+path);}
 async lock():Promise<()=>Promise<void>>{const path='.eta-skill-installer/install.lock',kind=await this.kind(path);if(kind!==null&&kind!=='file')throw new Error('SKILL_LOCK_INVALID');const file=await fileIo.open(this.root+'/'+path,fileIo.OpenMode.CREATE|fileIo.OpenMode.READ_WRITE);try{await file.lock(true);}catch(error){await fileIo.close(file);throw new Error('SKILL_LOCK_FAILED');}return async()=>{try{file.unlock();}finally{await fileIo.close(file);}};}
 encode(text:string):Uint8Array{return new util.TextEncoder().encodeInto(text);}
 decode(bytes:Uint8Array):string{return util.TextDecoder.create('utf-8',{fatal:true,ignoreBOM:true}).decodeToString(bytes);}
 uuid():string{return util.generateRandomUUID(true);}
}
async function readSkillFile(path:string,maxBytes:number,archive:boolean):Promise<Uint8Array>{const file=await fileIo.open(path,fileIo.OpenMode.READ_ONLY);try{const stat=await fileIo.stat(file.fd),size=stat.size;if(!stat.isFile()||size<0||size>maxBytes){if(archive&&size>maxBytes)throw new SkillInstallError('ARCHIVE_TOO_LARGE');throw new Error('SKILL_FILE_SIZE_INVALID');}const output=new Uint8Array(size);let offset=0;while(offset<size){const buffer=new ArrayBuffer(Math.min(65536,size-offset)),n=await fileIo.read(file.fd,buffer,{offset,length:buffer.byteLength});if(n<=0)throw new Error('SKILL_SHORT_READ');output.set(new Uint8Array(buffer,0,n),offset);offset+=n;}const extra=new ArrayBuffer(1);if(await fileIo.read(file.fd,extra,{offset:size,length:1})!==0)throw new Error('SKILL_FILE_CHANGED');return output;}finally{await fileIo.close(file);}}
class HarmonySkillInflateSession implements SkillInflateSession {
 constructor(private zip:zlib.Zip){}
 async initialize(input:ArrayBuffer,output:ArrayBuffer):Promise<void>{const status=await this.zip.inflateInit2({nextIn:input,availableIn:input.byteLength,nextOut:output,availableOut:output.byteLength},-15);if(status!==zlib.ReturnStatus.OK)throw new Error('SKILL_INFLATE_INIT');}
 async inflate():Promise<boolean>{return await this.zip.inflate({},zlib.CompressFlushMode.FINISH)===zlib.ReturnStatus.STREAM_END;}
 async counts():Promise<SkillInflateCounts>{const state=await this.zip.getZStream();return {input:state.totalIn??-1,output:state.totalOut??-1};}
 async end():Promise<void>{await this.zip.inflateEnd({});}
}
class HarmonySkillCodec implements SkillZipCodec {
 async sha256(bytes:Uint8Array):Promise<string>{const digest=cryptoFramework.createMd('SHA256');await digest.update({data:bytes});const data=(await digest.digest()).data;let out='';for(let i=0;i<data.length;i++)out+=data[i].toString(16).padStart(2,'0');return out;}
 async inflateRaw(bytes:Uint8Array,expectedBytes:number):Promise<Uint8Array>{return inflateSkillBytes(new HarmonySkillInflateSession(await zlib.createZip()),bytes,expectedBytes);}
}
class HarmonySkillRegistry implements SkillRegistry {
 private storage:DatabaseDocumentStorage;
 constructor(db:RuntimeDatabase){this.storage=new DatabaseDocumentStorage(db,'eta_skills_registry_v1');}
 async read():Promise<Record<string,SkillRegistryEntry>>{return decodeSkillRegistry(await this.storage.read());}
 async write(entries:Record<string,SkillRegistryEntry>):Promise<void>{await this.storage.write(JSON.stringify(entries));}
}
export function createEtaSkillsPorts(context:common.UIAbilityContext,db:RuntimeDatabase):SkillsPorts{
 const files=new HarmonySkillFiles(context),registry=new HarmonySkillRegistry(db),index=new SkillIndexService(files,registry,BUILTIN_SKILLS),installer=new SkillPackageInstaller(index,new HarmonySkillCodec());
 return {list:()=>index.listSkillsForManagement(true),toggle:(id,value)=>index.setSkillEnabled(id,value),delete:(id)=>index.deleteSkill(id),reinstall:(id)=>index.installBuiltinSkill(id),
  pickZip:async():Promise<SkillArchiveReader|null>=>{const selected=await new picker.DocumentViewPicker(context).select({maxSelectNumber:1,fileSuffixFilters:['ZIP|.zip']});if(selected.length!==1)return null;const uri=selected[0];if(!uri.startsWith('file://'))throw new Error('SKILL_PICKER_URI_INVALID');return async(maxBytes:number)=>readSkillFile(uri,maxBytes,true);},
  install:async(reader,options)=>toSkillZipOutcome(await installer.installLocalZip(reader,options))};
}
