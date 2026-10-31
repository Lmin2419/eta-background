// Ported from app/src/main/kotlin/io/github/mangi/eta/data/repository/McpServerRepository.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11). RDB document and ASSET are explicit atomic-storage ports.
import {ProfileStorage,SecretStorage} from '../../profiles';import {copy} from '../protocol/providerTypes';import {utf8Size} from '../searchSuggestionsBudget';
import {McpServer,decodeMcpServers,mcpToEntity} from './mcpTypes';
export class McpServerRepository {
 private tail:Promise<void>=Promise.resolve();
 constructor(private storage:ProfileStorage,private secrets:SecretStorage,private uuid:()=>string){}
 private serial<T>(action:()=>Promise<T>):Promise<T>{const p=this.tail.then(action);this.tail=p.then(()=>{},()=>{});return p;}
 private read():Promise<McpServer[]>{return this.storage.read().then(decodeMcpServers);}
 private write(values:McpServer[]):Promise<void>{return this.storage.write(JSON.stringify(values.map(mcpToEntity)));}
 private alias(id:string):string{return 'eta_mcp_'+id;}
 list():Promise<McpServer[]>{return this.serial(async()=>(await this.read()).sort((a,b)=>a.sortOrder-b.sortOrder));}
 token(id:string):Promise<string>{return this.serial(async()=>{const server=(await this.read()).find(s=>s.id===id);if(!server)throw Error('MCP_SERVER_MISSING');return server.authorizationType==='bearer'?this.secrets.get(this.alias(id)):'';});}
 private secret(s:McpServer,token:string):Promise<void>{if(/[\r\n\0]/.test(token)||utf8Size(token)>1024)throw Error('MCP_TOKEN_INVALID');return s.authorizationType==='bearer'?this.secrets.put(this.alias(s.id),token):this.secrets.remove(this.alias(s.id));}
 add(server:McpServer,token:string):Promise<McpServer>{return this.serial(async()=>{const values=await this.read(),added={...copy(server),id:server.id||this.uuid(),sortOrder:values.reduce((n,s)=>Math.max(n,s.sortOrder),-1)+1};if(values.some(s=>s.id===added.id))throw Error('MCP_DUPLICATE_ID');await this.secret(added,token);try{await this.write([...values,added]);}catch(_){try{await this.secrets.remove(this.alias(added.id));}catch(_){}throw Error('MCP_SAVE_FAILED');}return copy(added);});}
 update(server:McpServer,token:string|null=null):Promise<void>{return this.serial(()=>this.updateUnlocked(server,token));}
 private async updateUnlocked(server:McpServer,token:string|null):Promise<void>{const values=await this.read(),at=values.findIndex(s=>s.id===server.id);if(at<0)throw Error('MCP_SERVER_MISSING');const previous=values[at],oldToken=previous.authorizationType==='bearer'?await this.secrets.get(this.alias(server.id)):'',changes=token!==null||server.authorizationType==='none',updated=token!==null?{...copy(server),lastProtocolVersion:null,toolsExpireAt:null}:copy(server);if(changes)await this.secret(updated,token||'');values[at]=updated;try{await this.write(values);}catch(_){if(changes)try{await this.secret(previous,oldToken);}catch(_){}throw Error('MCP_SAVE_FAILED');}}
 setEnabled(id:string,enabled:boolean):Promise<void>{return this.serial(async()=>{const s=(await this.read()).find(s=>s.id===id);if(!s)throw Error('MCP_SERVER_MISSING');await this.updateUnlocked({...s,enabled},null);});}
 setToolEnabled(id:string,name:string,enabled:boolean):Promise<void>{return this.serial(async()=>{const s=(await this.read()).find(s=>s.id===id);if(!s||!s.tools.some(t=>t.name===name))throw Error('MCP_TOOL_MISSING');const names=new Set(s.enabledToolNames);if(enabled)names.add(name);else names.delete(name);await this.updateUnlocked({...s,enabledToolNames:[...names]},null);});}
 delete(id:string):Promise<void>{return this.serial(async()=>{const values=await this.read(),previous=values.find(s=>s.id===id);if(!previous)throw Error('MCP_SERVER_MISSING');const oldToken=previous.authorizationType==='bearer'?await this.secrets.get(this.alias(id)):'';await this.secrets.remove(this.alias(id));try{await this.write(values.filter(s=>s.id!==id));}catch(_){try{await this.secret(previous,oldToken);}catch(_){}throw Error('MCP_DELETE_FAILED');}});}
}
