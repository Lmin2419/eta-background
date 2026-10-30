import { picker, fileIo as fs } from '@kit.CoreFileKit';
import { common } from '@kit.AbilityKit';
import { util } from '@kit.ArkTS';
import { characterPngPayload } from '../core/extensions';
export async function chooseData(context:common.UIAbilityContext,limit:number,rolePng=false):Promise<string>{
 const uris=await new picker.DocumentViewPicker(context).select({maxSelectNumber:1});if(!uris.length)return '';
 const file=fs.openSync(uris[0],fs.OpenMode.READ_ONLY);
 try{const size=fs.statSync(file.fd).size;if(size>limit)throw new Error('所选文件超过导入限制');const bytes=new Uint8Array(size);let at=0;while(at<size){const buffer=new ArrayBuffer(size-at);const n=fs.readSync(file.fd,buffer,{offset:at});if(n<=0)throw new Error('所选文件读取不完整');bytes.set(new Uint8Array(buffer,0,n),at);at+=n;}
  if(rolePng&&bytes[0]===137){const encoded=characterPngPayload(bytes);return util.TextDecoder.create('utf-8',{fatal:true}).decodeToString(new util.Base64Helper().decodeSync(encoded));}
  return util.TextDecoder.create('utf-8',{fatal:true}).decodeToString(bytes);
 }finally{fs.closeSync(file);}
}
export async function saveData(context:common.UIAbilityContext,name:string,text:string):Promise<boolean>{
 const uris=await new picker.DocumentViewPicker(context).save({newFileNames:[name]});if(!uris.length)return false;
 const file=fs.openSync(uris[0],fs.OpenMode.CREATE|fs.OpenMode.WRITE_ONLY|fs.OpenMode.TRUNC);
 try{const bytes=new util.TextEncoder().encodeInto(text);let at=0;while(at<bytes.length){const n=fs.writeSync(file.fd,bytes.buffer.slice(at));if(n<=0)throw new Error('导出未完整写入');at+=n;}fs.fsyncSync(file.fd);}finally{fs.closeSync(file);}return true;
}

export async function saveBinary(context:common.UIAbilityContext,name:string,bytes:Uint8Array):Promise<boolean>{const uris=await new picker.DocumentViewPicker(context).save({newFileNames:[name]});if(!uris.length)return false;const f=fs.openSync(uris[0],fs.OpenMode.WRITE_ONLY|fs.OpenMode.CREATE|fs.OpenMode.TRUNC);try{let at=0;while(at<bytes.length){const n=fs.writeSync(f.fd,bytes.buffer.slice(at));if(n<=0)throw new Error('导出不完整');at+=n;}fs.fsyncSync(f.fd);}finally{fs.closeSync(f);}return true;}
