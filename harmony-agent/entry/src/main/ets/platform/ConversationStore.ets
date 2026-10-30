import { fileIo as fs, picker } from '@kit.CoreFileKit';
import { common } from '@kit.AbilityKit';
import { util } from '@kit.ArkTS';
import { ConversationStorage } from '../core/conversations';

function writeComplete(fd: number, text: string): void {
  const bytes = new util.TextEncoder().encodeInto(text);
  let offset = 0;
  while (offset < bytes.byteLength) {
    const count = fs.writeSync(fd,bytes.buffer.slice(offset));
    if (count <= 0) throw new Error('会话写入不完整');
    offset += count;
  }
}

export class FileConversationStorage implements ConversationStorage {
  private path: string;
  constructor(filesDir: string) { this.path=filesDir+'/conversations.json'; }
  async read(): Promise<string> {
    if (!fs.accessSync(this.path)) return '';
    if (fs.statSync(this.path).size>8_000_000) throw new Error('会话文件过大，已保留原文件');
    return fs.readTextSync(this.path);
  }
  async write(text: string): Promise<void> {
    const temp=this.path+'.tmp';
    const file=fs.openSync(temp,fs.OpenMode.CREATE|fs.OpenMode.WRITE_ONLY|fs.OpenMode.TRUNC);
    try { writeComplete(file.fd,text);fs.fsyncSync(file.fd); }
    finally { fs.closeSync(file); }
    fs.renameSync(temp,this.path);
  }
}

export async function exportConversation(context: common.UIAbilityContext, title: string, markdown: string): Promise<boolean> {
  const safeName=title.replace(/[\\/:*?"<>|\r\n]/g,'_').slice(0,48)||'会话';
  const documentPicker=new picker.DocumentViewPicker(context);
  const uris=await documentPicker.save({newFileNames:[safeName+'.md']});
  if (!uris.length) return false;
  const file=fs.openSync(uris[0],fs.OpenMode.WRITE_ONLY|fs.OpenMode.CREATE|fs.OpenMode.TRUNC);
  try { writeComplete(file.fd,markdown);fs.fsyncSync(file.fd); }
  finally { fs.closeSync(file); }
  return true;
}
