// Ported from app/src/main/kotlin/io/github/mangi/eta/agent/skill/SkillPackageInstaller.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11). ZipInputStream is replaced by a bounded ZIP32 reader + raw Deflate port.
import {SkillFileSystem} from './skillFiles';
import {SkillErrorCode,SkillPackageLimits,checkSkillCancelled,skillFail} from './skillTypes';
export interface SkillZipCodec {inflateRaw(bytes:Uint8Array,expectedBytes:number):Promise<Uint8Array>;sha256(bytes:Uint8Array):Promise<string>;}
export function validateSkillRelativePath(raw:string,code:SkillErrorCode='UNSAFE_ENTRY_PATH'):string[]{if(!raw.trim()||raw.startsWith('/')||raw.startsWith('\\')||/^[A-Za-z]:/.test(raw)||/[\\\x00-\x1f\x7f-\x9f]/.test(raw))skillFail(code);const parts=raw.split('/');if(parts.some(p=>!p.trim()||p==='.'||p==='..'||p.length>255))skillFail(code);return parts;}
export function skillCrc32(bytes:Uint8Array):number{let crc=0xffffffff;for(const byte of bytes){crc^=byte;for(let b=0;b<8;b++)crc=(crc>>>1)^((crc&1)?0xedb88320:0);}return (crc^0xffffffff)>>>0;}
interface ZipEntry {path:string;directory:boolean;method:number;crc:number;size:number;compressed:number;offset:number;dataStart:number;end:number;}
function invalid():never{return skillFail('INVALID_ARCHIVE');}
export async function extractSkillZip(bytes:Uint8Array,root:string,fs:SkillFileSystem,codec:SkillZipCodec,limits:SkillPackageLimits,isCancelled:()=>boolean):Promise<void>{
 if(!bytes.length)invalid();if(bytes.length>limits.maxArchiveBytes)skillFail('ARCHIVE_TOO_LARGE');const view=new DataView(bytes.buffer,bytes.byteOffset,bytes.byteLength);
 const u16=(at:number)=>at<0||at+2>bytes.length?invalid():view.getUint16(at,true),u32=(at:number)=>at<0||at+4>bytes.length?invalid():view.getUint32(at,true);
 let end=-1;for(let p=bytes.length-22;p>=Math.max(0,bytes.length-65557);p--)if(u32(p)===0x06054b50&&p+22+u16(p+20)===bytes.length){end=p;break;}if(end<0)invalid();
 const count=u16(end+10),centralSize=u32(end+12),start=u32(end+16);if(u16(end+4)!==0||u16(end+6)!==0||u16(end+8)!==count||count===65535||start+centralSize!==end||!count)invalid();if(count>limits.maxEntries)skillFail('TOO_MANY_ENTRIES');
 const entries:ZipEntry[]=[],paths=new Map<string,boolean>();let position=start,total=0;
 for(let n=0;n<count;n++){
  checkSkillCancelled(isCancelled);if(u32(position)!==0x02014b50)invalid();const flags=u16(position+8),method=u16(position+10),crc=u32(position+16),compressed=u32(position+20),size=u32(position+24),nameLength=u16(position+28),extra=u16(position+30),comment=u16(position+32),offset=u32(position+42),attrs=u32(position+38);
  // ZIP64, encryption, split archives and non-file Unix types are not accepted by this port.
  if((flags&~0x080e)!==0||(flags&1)!==0||![0,8].includes(method)||u16(position+34)!==0||[compressed,size,offset].includes(0xffffffff))invalid();
  const type=(attrs>>>16)&0xf000;if(type!==0&&type!==0x8000&&type!==0x4000)skillFail('UNSAFE_ENTRY_PATH');
  const next=position+46+nameLength+extra+comment;if(next>end||next>start+centralSize||!nameLength)invalid();let raw:string;try{raw=fs.decode(bytes.slice(position+46,position+46+nameLength));}catch(_){invalid();}
  const directory=raw.endsWith('/'),parts=validateSkillRelativePath(directory?raw.slice(0,-1):raw);if(parts.length>limits.maxPathDepth)skillFail('ENTRY_PATH_TOO_DEEP');const path=parts.join('/'),key=path.normalize('NFC').toLowerCase();
  if(paths.has(key)||parts.slice(0,-1).some((_,i)=>paths.get(parts.slice(0,i+1).join('/').normalize('NFC').toLowerCase())===false)||(!directory&&[...paths.keys()].some(k=>k.startsWith(key+'/'))))skillFail('DUPLICATE_ENTRY');paths.set(key,directory);
  if(size>limits.maxSingleFileBytes)skillFail('ENTRY_TOO_LARGE');total+=size;if(total>limits.maxExtractedBytes)skillFail('EXTRACTED_CONTENT_TOO_LARGE');if(directory&&size!==0)invalid();
  if(u32(offset)!==0x04034b50||u16(offset+6)!==flags||u16(offset+8)!==method||u16(offset+26)!==nameLength)invalid();const localName=bytes.slice(offset+30,offset+30+nameLength);if(localName.length!==nameLength||localName.some((b,i)=>b!==bytes[position+46+i]))invalid();
  const dataStart=offset+30+nameLength+u16(offset+28);if(dataStart+compressed>start||dataStart>start)invalid();if(!(flags&8)&&(u32(offset+14)!==crc||u32(offset+18)!==compressed||u32(offset+22)!==size))invalid();
  let localEnd=dataStart+compressed;if(flags&8){let descriptor=localEnd;if(u32(descriptor)===0x08074b50)descriptor+=4;if(descriptor+12>start||u32(descriptor)!==crc||u32(descriptor+4)!==compressed||u32(descriptor+8)!==size)invalid();localEnd=descriptor+12;}
  if(entries.some(e=>offset<e.end&&localEnd>e.offset))invalid();
  entries.push({path,directory,method,crc,size,compressed,offset,dataStart,end:localEnd});position=next;
 }
 if(position!==end)invalid();await fs.mkdir(root);
 for(const entry of entries){checkSkillCancelled(isCancelled);const compressed=bytes.slice(entry.dataStart,entry.dataStart+entry.compressed);let data:Uint8Array;try{data=entry.method===0?compressed:await codec.inflateRaw(compressed,entry.size);}catch(_){invalid();}if(data.length!==entry.size||skillCrc32(data)!==entry.crc)invalid();checkSkillCancelled(isCancelled);const path=root+'/'+entry.path;if(entry.directory)await fs.mkdir(path);else{const parent=path.slice(0,path.lastIndexOf('/'));await fs.mkdir(parent);await fs.write(path,data);}}
}
