import { relationalStore } from '@kit.ArkData';
import { common } from '@kit.AbilityKit';
import { fileIo as fs } from '@kit.CoreFileKit';
import { ConversationStorage, parseConversationDocument, ConversationDocument, Conversation } from '../core/conversations';
import { parseProfileDocument, ProfileStorage } from '../core/profiles';
import { BackupDocument } from '../core/backup';
import { TaskDocument } from '../core/runtime';

/** One connection and serial transaction owner for all durable documents. */
export class RuntimeDatabase {
  private connection:Promise<relationalStore.RdbStore>|undefined;
  private tail:Promise<void>=Promise.resolve();
  private context:common.UIAbilityContext;
  constructor(context:common.UIAbilityContext){this.context=context;}
  private async database():Promise<relationalStore.RdbStore>{
    if(!this.connection)this.connection=this.open().catch((e:Error):never=>{this.connection=undefined;throw e;});return this.connection;
  }
  private async open():Promise<relationalStore.RdbStore>{
    const db=await relationalStore.getRdbStore(this.context,{name:'assistant.db',securityLevel:relationalStore.SecurityLevel.S1});
    await db.executeSql('CREATE TABLE IF NOT EXISTS documents (id TEXT PRIMARY KEY, payload TEXT NOT NULL)');
    await db.executeSql('CREATE TABLE IF NOT EXISTS conversations (id TEXT PRIMARY KEY, payload TEXT NOT NULL)');
    await db.executeSql('CREATE TABLE IF NOT EXISTS task_events (run_id TEXT, seq INTEGER, state TEXT, phase TEXT, reason TEXT, at INTEGER, PRIMARY KEY(run_id,seq))');
    return db;
  }
  serial<T>(fn:()=>Promise<T>):Promise<T>{const p=this.tail.then(fn);this.tail=p.then(()=>{},()=>{});return p;}
  read(key:string):Promise<string>{return this.serial(()=>this.readUnlocked(key));}
  private async readUnlocked(key:string):Promise<string>{
    const db=await this.database(),rows=await db.querySql('SELECT payload FROM documents WHERE id=?',[key]);
    try{return rows.goToFirstRow()?rows.getString(0):'';}finally{rows.close();}
  }
  async write(key:string,raw:string):Promise<void>{return this.serial(async()=>{
    const db=await this.database();
    db.beginTransaction();
    try{
      await db.executeSql('INSERT OR REPLACE INTO documents(id,payload) VALUES(?,?)',[key,raw]);
      if(key==='tasks'){
        const tasks=(JSON.parse(raw) as TaskDocument).tasks;
        for(const t of tasks)await db.executeSql('INSERT OR IGNORE INTO task_events(run_id,seq,state,phase,reason,at) VALUES(?,?,?,?,?,?)',[t.id,t.seq,t.state,t.phase,t.reason,t.updatedAt]);
        await db.executeSql('DELETE FROM task_events WHERE rowid NOT IN (SELECT rowid FROM task_events ORDER BY at DESC LIMIT 5000)');
      }
      db.commit();
    }catch(e){db.rollBack();throw e as Error;}
  });}
  async restore(backup:BackupDocument,workspaceRoot:string,nextNotificationId:number):Promise<void>{return this.serial(async()=>{
    const db=await this.database();db.beginTransaction();
    try{
      await db.executeSql('DELETE FROM conversations');
      for(const c of backup.conversations.conversations)await db.executeSql('INSERT INTO conversations(id,payload) VALUES(?,?)',[c.id,JSON.stringify(c)]);
      const docs:Map<string,string>=new Map();
      docs.set('conversation_meta',JSON.stringify({version:1,selectedId:backup.conversations.selectedId}));docs.set('profiles',JSON.stringify(backup.profiles));docs.set('extensions',JSON.stringify(backup.extensions));docs.set('workspace_root',workspaceRoot);
      docs.set('settings',JSON.stringify(backup.settings||{}));docs.set('drafts',JSON.stringify({version:1,messages:backup.editors||[],providers:[]}));docs.set('tasks',JSON.stringify({version:1,nextNotificationId,tasks:[]}));
      for(const key of docs.keys())await db.executeSql('INSERT OR REPLACE INTO documents(id,payload) VALUES(?,?)',[key,docs.get(key)||'']);
      await db.executeSql('DELETE FROM task_events');db.commit();
    }catch(e){db.rollBack();throw e as Error;}
  });}
  readConversations():Promise<string>{return this.serial(()=>this.readConversationsUnlocked());}
  private async readConversationsUnlocked():Promise<string>{
    const meta=await this.readUnlocked('conversation_meta');
    if(!meta)return '';
    const document=JSON.parse(meta) as ConversationDocument;
    document.conversations=[];
    const db=await this.database(),rows=await db.querySql('SELECT payload FROM conversations ORDER BY rowid');
    try{while(rows.goToNextRow())document.conversations.push(JSON.parse(rows.getString(0)) as Conversation);}finally{rows.close();}
    return JSON.stringify(document);
  }
  async writeConversations(raw:string):Promise<void>{return this.serial(async()=>{
    const document=parseConversationDocument(raw),db=await this.database();
    const rows=await db.querySql('SELECT id,payload FROM conversations');const existing:Map<string,string>=new Map();
    try{while(rows.goToNextRow())existing.set(rows.getString(0),rows.getString(1));}finally{rows.close();}
    db.beginTransaction();
    try{
      for(const c of document.conversations){const payload=JSON.stringify(c);if(existing.get(c.id)!==payload)await db.executeSql('INSERT OR REPLACE INTO conversations(id,payload) VALUES(?,?)',[c.id,payload]);existing.delete(c.id);}
      for(const id of existing.keys())await db.executeSql('DELETE FROM conversations WHERE id=?',[id]);
      await db.executeSql('INSERT OR REPLACE INTO documents(id,payload) VALUES(?,?)',['conversation_meta',JSON.stringify({version:1,selectedId:document.selectedId})]);
      db.commit();
    }catch(e){db.rollBack();throw e as Error;}
  });}
}

export class DatabaseDocumentStorage implements ConversationStorage {
  private db:RuntimeDatabase;private key:string;
  constructor(db:RuntimeDatabase,key:string){this.db=db;this.key=key;}
  read():Promise<string>{return this.db.read(this.key);}
  write(raw:string):Promise<void>{return this.db.write(this.key,raw);}
}
export class DatabaseConversationStorage implements ConversationStorage {
  private db:RuntimeDatabase;private filesDir:string;
  constructor(db:RuntimeDatabase,filesDir:string){this.db=db;this.filesDir=filesDir;}
  async read():Promise<string>{
    const existing=await this.db.readConversations();if(existing)return existing;
    const legacy=this.filesDir+'/conversations.json';if(!fs.accessSync(legacy))return '';
    if(fs.statSync(legacy).size>8_000_000)throw new Error('旧会话文件过大，未迁移');
    const raw=fs.readTextSync(legacy);parseConversationDocument(raw);
    // Keep the legacy file intact. The database marker and all rows commit together.
    await this.db.writeConversations(raw);
    const verified=await this.db.readConversations();
    if(JSON.stringify(parseConversationDocument(raw))!==JSON.stringify(parseConversationDocument(verified)))throw new Error('旧会话迁移核验失败，原 JSON 仍保留');
    return verified;
  }
  write(raw:string):Promise<void>{return this.db.writeConversations(raw);}
}

export class DatabaseProfileStorage implements ProfileStorage {
  private db:RuntimeDatabase;private filesDir:string;
  constructor(db:RuntimeDatabase,filesDir:string){this.db=db;this.filesDir=filesDir;}
  async read():Promise<string>{const current=await this.db.read('profiles');if(current)return current;const path=this.filesDir+'/model-profiles.json';if(!fs.accessSync(path))return '';if(fs.statSync(path).size>2_000_000)throw new Error('旧模型配置过大');const raw=fs.readTextSync(path);parseProfileDocument(raw);await this.db.write('profiles',raw);return raw;}
  write(raw:string):Promise<void>{parseProfileDocument(raw);return this.db.write('profiles',raw);}
}
