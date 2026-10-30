import { fileIo as fs, picker } from '@kit.CoreFileKit';
import { common } from '@kit.AbilityKit';
import { util } from '@kit.ArkTS';
import { WorkspaceBackend, WorkspaceFile, safeWorkspaceName } from '../core/workspace';
import { BackupFile, crc32, decodeBase64 } from '../core/backup';
import { ContentPart } from '../core/types';

export class WorkspaceStore implements WorkspaceBackend {
  private root:string;
  constructor(filesDir:string){this.root=filesDir+'/workspace';if(!fs.accessSync(this.root))fs.mkdirSync(this.root,true);}
  useRoot(path:string):void{this.root=path;if(!fs.accessSync(path))fs.mkdirSync(path,true);}
  async backupFiles():Promise<BackupFile[]>{const result:BackupFile[]=[];for(const f of await this.list()){const bytes=this.bytes(this.path(f.name),4_000_000);result.push({name:f.name,base64:new util.Base64Helper().encodeToStringSync(bytes),crc32:crc32(bytes)});}return result;}
  static stage(filesDir:string,files:BackupFile[]):string{
    const root=filesDir+'/workspace-restore-'+util.generateRandomUUID();fs.mkdirSync(root,true);
    try{for(const f of files){const bytes=decodeBase64(f.base64),file=fs.openSync(root+'/'+safeWorkspaceName(f.name),fs.OpenMode.CREATE|fs.OpenMode.WRITE_ONLY|fs.OpenMode.TRUNC);try{let at=0;while(at<bytes.length){const n=fs.writeSync(file.fd,bytes.buffer.slice(at));if(n<=0)throw new Error('恢复文件写入失败');at+=n;}fs.fsyncSync(file.fd);}finally{fs.closeSync(file);}}return root;}
    catch(e){try{fs.rmdirSync(root);}catch(_){}throw e as Error;}
  }
  clearRecoveryDirectories(filesDir:string):number{let count=0;for(const name of fs.listFileSync(filesDir)){const path=filesDir+'/'+name;if(name.startsWith('workspace-restore-')&&path!==this.root&&fs.statSync(path).isDirectory()){fs.rmdirSync(path);count++;}}return count;}
  path(name:string):string{return this.root+'/'+safeWorkspaceName(name);}
  async list():Promise<WorkspaceFile[]>{
    const files:WorkspaceFile[]=[];
    for(const name of fs.listFileSync(this.root)){
      if(name.startsWith('.'))continue;
      const stat=fs.statSync(this.path(name));if(stat.isFile())files.push({name,size:stat.size,modifiedAt:stat.mtime*1000});
    }
    return files.sort((a:WorkspaceFile,b:WorkspaceFile):number=>b.modifiedAt-a.modifiedAt);
  }
  async read(name:string):Promise<string>{const path=this.path(name);if(fs.statSync(path).size>400000)throw new Error('文件超过文本读取上限，请缩小内容');if(/\.(png|jpg|jpeg|webp|gif|pdf|zip)$/i.test(name))throw new Error('这是二进制附件，请通过附件预览或对应工具读取');return fs.readTextSync(path);}
  async write(name:string,text:string):Promise<void>{
    const path=this.path(name),files=await this.list();
    if(files.length>=100&&!fs.accessSync(path))throw new Error('工作区最多 100 个文件');
    const bytes=new util.TextEncoder().encodeInto(text);if(bytes.byteLength>400000)throw new Error('文本文件超过 400KB');
    if(files.reduce((sum:number,f:WorkspaceFile):number=>sum+(f.name===name?0:f.size),0)+bytes.byteLength>32_000_000)throw new Error('工作区超过 32MB，请先导出并清理');
    const temp=this.root+'/.write-'+util.generateRandomUUID();const fd=fs.openSync(temp,fs.OpenMode.CREATE|fs.OpenMode.WRITE_ONLY|fs.OpenMode.TRUNC);
    try{let offset=0;while(offset<bytes.length){const n=fs.writeSync(fd.fd,bytes.buffer.slice(offset));if(n<=0)throw new Error('文件写入未完成');offset+=n;}fs.fsyncSync(fd.fd);}finally{fs.closeSync(fd);}
    fs.renameSync(temp,path);
  }
  async remove(name:string):Promise<void>{fs.unlinkSync(this.path(name));}
  bytes(path:string,limit:number):Uint8Array{
    const size=fs.statSync(path).size;if(size>limit)throw new Error('附件超过大小限制');
    const buffer=new ArrayBuffer(size),file=fs.openSync(path,fs.OpenMode.READ_ONLY);
    try{let offset=0;while(offset<size){const chunk=new ArrayBuffer(size-offset);const n=fs.readSync(file.fd,chunk,{offset:offset});if(n<=0)throw new Error('附件读取未完成');new Uint8Array(buffer).set(new Uint8Array(chunk,0,n),offset);offset+=n;}}finally{fs.closeSync(file);}
    return new Uint8Array(buffer);
  }
  async image(name:string):Promise<ContentPart>{
    const ext=name.toLocaleLowerCase();const mime=ext.endsWith('.png')?'image/png':/\.jpe?g$/.test(ext)?'image/jpeg':ext.endsWith('.webp')?'image/webp':'';
    if(!mime)throw new Error('直接图片输入支持 PNG、JPEG、WebP');
    return {type:'image_url',image_url:{url:'data:'+mime+';base64,'+new util.Base64Helper().encodeToStringSync(this.bytes(this.path(name),4_000_000))}};
  }
  async importFiles(context:common.UIAbilityContext):Promise<string[]>{
    const document=new picker.DocumentViewPicker(context),uris=await document.select({maxSelectNumber:5});const names:string[]=[];
    for(const uri of uris){
      const current=await this.list();if(current.length>=100)throw new Error('工作区文件数量已达上限');
      const rawName=decodeURIComponent(uri.split('/').pop()||'导入文件').split('?')[0].replace(/[\\/\x00-\x1f]/g,'_').replace(/^\.+/,'').slice(-80)||'导入文件.txt';
      const name=util.generateRandomUUID().slice(0,8)+'-'+rawName;
      const bytes=this.bytes(uri,4_000_000);
      if(current.reduce((sum:number,f:WorkspaceFile):number=>sum+f.size,0)+bytes.length>32_000_000)throw new Error('工作区超过 32MB');
      const temporary=this.root+'/.import-'+util.generateRandomUUID();const file=fs.openSync(temporary,fs.OpenMode.CREATE|fs.OpenMode.WRITE_ONLY|fs.OpenMode.TRUNC);
      try{let offset=0;while(offset<bytes.length){const n=fs.writeSync(file.fd,bytes.buffer.slice(offset));if(n<=0)throw new Error('附件导入未完成');offset+=n;}fs.fsyncSync(file.fd);}finally{fs.closeSync(file);}
      fs.renameSync(temporary,this.path(name));names.push(name);
    }
    return names;
  }
  async exportFile(context:common.UIAbilityContext,name:string):Promise<boolean>{
    const document=new picker.DocumentViewPicker(context),uris=await document.save({newFileNames:[safeWorkspaceName(name)]});if(!uris.length)return false;
    const source=fs.openSync(this.path(name),fs.OpenMode.READ_ONLY),target=fs.openSync(uris[0],fs.OpenMode.CREATE|fs.OpenMode.WRITE_ONLY|fs.OpenMode.TRUNC);
    try{fs.copyFileSync(source.fd,target.fd);fs.fsyncSync(target.fd);}finally{fs.closeSync(source);fs.closeSync(target);}return true;
  }
}
