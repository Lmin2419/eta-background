import { SettingValue, SavedEditor, parseSettings, parseEditors } from './settings';
import { ConversationDocument, parseConversationDocument } from './conversations';
import { ProfileDocument, parseProfileDocument } from './profiles';
import { ExtensionDocument, parseExtensions } from './extensions';
import { safeWorkspaceName } from './workspace';
export interface BackupFile {name:string;base64:string;crc32:number}
export interface BackupDocument {format:'harmony-agent-backup';version:1;createdAt:number;conversations:ConversationDocument;profiles:ProfileDocument;extensions:ExtensionDocument;files:BackupFile[];settings?:Record<string,SettingValue>;editors?:SavedEditor[]}
export function decodeBase64(text:string):Uint8Array{
 if(text.length>6_000_000||! /^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$/.test(text))throw new Error('备份文件编码无效');
 const chars='ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/',out=new Uint8Array(text.length/4*3-(text.endsWith('==')?2:text.endsWith('=')?1:0));let at=0;
 for(let i=0;i<text.length;i+=4){const n=(chars.indexOf(text[i])<<18)|(chars.indexOf(text[i+1])<<12)|((chars.indexOf(text[i+2])&63)<<6)|(chars.indexOf(text[i+3])&63);if(at<out.length)out[at++]=n>>16;if(at<out.length)out[at++]=n>>8;if(at<out.length)out[at++]=n;}
 return out;
}
export function crc32(bytes:Uint8Array):number{let c=0xffffffff;for(const b of bytes){c^=b;for(let i=0;i<8;i++)c=(c>>>1)^((c&1)?0xedb88320:0);}return (c^0xffffffff)>>>0;}
export function prepareBackup(conversations:ConversationDocument,profiles:ProfileDocument,extensions:ExtensionDocument,files:BackupFile[],settings:Record<string,SettingValue>={},editors:SavedEditor[]=[]):BackupDocument{
 const cleanProfiles=parseProfileDocument(JSON.stringify(profiles));for(const p of cleanProfiles.profiles){delete p.secretRef;delete p.headersRef;delete p.extraHeaders;}
 const cleanExtensions=parseExtensions(JSON.stringify(extensions));for(const m of cleanExtensions.mcp){delete m.secretRef;m.enabled=false;}
 return validateBackup(JSON.stringify({format:'harmony-agent-backup',version:1,createdAt:Date.now(),conversations,profiles:cleanProfiles,extensions:cleanExtensions,files,settings,editors}));
}
export function validateBackup(raw:string):BackupDocument{
 if(raw.length>48_000_000)throw new Error('备份超过 48MB');let v:any;try{v=JSON.parse(raw);}catch(_){throw new Error('备份不是有效 JSON，原数据未修改');}
 if(v?.format!=='harmony-agent-backup'||v.version!==1||!Number.isFinite(v.createdAt)||!Array.isArray(v.files)||v.files.length>100)throw new Error('备份版本或格式无效');
 const conversations=parseConversationDocument(JSON.stringify(v.conversations)),profiles=parseProfileDocument(JSON.stringify(v.profiles)),extensions=parseExtensions(JSON.stringify(v.extensions));
 for(const p of profiles.profiles){delete p.secretRef;delete p.headersRef;delete p.extraHeaders;}for(const m of extensions.mcp){delete m.secretRef;m.enabled=false;}
 // External files cannot turn an interrupted task into a live run.
 for(const c of conversations.conversations){if(!profiles.profiles.some(p=>p.id===c.profileId))c.profileId='';for(const t of c.turns)if(t.status==='running'){t.status='interrupted';t.notice='从备份恢复；没有恢复设备操作';for(const s of t.steps)if(s.status==='running')s.status='interrupted';}}
 const settings=parseSettings(JSON.stringify(v.settings||{}));delete settings.target;settings.continuousBackground=false;const editors=parseEditors(v.editors||[]);
 const files:BackupFile[]=[];let total=0;const names=new Set<string>();
 for(const f of v.files){const name=safeWorkspaceName(f.name);if(names.has(name)||typeof f.base64!=='string')throw new Error('备份文件名重复或内容无效');const bytes=decodeBase64(f.base64);if(bytes.length>4_000_000||crc32(bytes)!==f.crc32)throw new Error('备份文件校验失败：'+name);total+=bytes.length;if(total>32_000_000)throw new Error('备份工作区超过 32MB');files.push({name,base64:f.base64,crc32:f.crc32});names.add(name);}
 return {format:'harmony-agent-backup',version:1,createdAt:v.createdAt,conversations,profiles,extensions,files,settings,editors};
}
