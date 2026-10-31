// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/screens/mcp/McpServersScreen.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11). Compose state/coroutines become controller state with cancellation generations.
import {copy} from '../protocol/providerTypes';
import {utf8Size} from '../searchSuggestionsBudget';
import {McpServer,emptyMcpServer} from './mcpTypes';
import {McpServerRepository} from './mcpRepository';
import {McpHttpClient,McpTransport,applyMcpDiscovery,validateMcpEndpoint} from './mcpHttpClient';

export type McpModal='add'|'token'|'risk'|'delete'|'';
export type McpNotice='added'|'refreshed'|'connection_failed'|'refresh_failed'|'save_failed'|'name_required'|'invalid_url'|'token_invalid'|'';
export interface McpDraft {name:string;url:string;token:string;}
export interface McpUiState {servers:McpServer[];selectedId:string;loading:boolean;working:boolean;modal:McpModal;draft:McpDraft;riskyToolName:string;notice:McpNotice;noticeCount:number;}
export function emptyMcpState():McpUiState{return {servers:[],selectedId:'',loading:false,working:false,modal:'',draft:{name:'',url:'',token:''},riskyToolName:'',notice:'',noticeCount:0};}
export class McpUiStore {
  private state=emptyMcpState();
  private generation=0;
  private client:McpHttpClient|null=null;
  constructor(private repository:McpServerRepository,private transport:()=>McpTransport,private changed:()=>void,private now:()=>number=Date.now){}
  snapshot():McpUiState{return copy(this.state);}
  selected():McpServer|null{return this.state.servers.find(s=>s.id===this.state.selectedId)||null;}
  private publish():void{this.changed();}
  private invalidate():void{this.generation++;this.client?.close();this.client=null;}
  close():void{this.invalidate();this.state={...this.state,working:false,loading:false,modal:'',draft:{name:'',url:'',token:''},notice:''};this.publish();}
  async open(id=''):Promise<void>{this.close();const gen=this.generation;this.state={...this.state,selectedId:id,loading:true};this.publish();try{const servers=await this.repository.list();if(gen!==this.generation)return;this.state={...this.state,servers,loading:false};}catch(_){if(gen!==this.generation)return;this.state={...this.state,loading:false,notice:'save_failed'};}this.publish();}
  show(modal:'add'|'token'|'delete'):void{if(this.state.working||this.state.loading)return;this.state={...this.state,modal,draft:{name:'',url:'',token:''},notice:''};this.publish();}
  dismiss():void{this.invalidate();this.state={...this.state,working:false,modal:'',draft:{name:'',url:'',token:''},riskyToolName:'',notice:''};this.publish();}
  dismissNotice():void{this.state={...this.state,notice:''};this.publish();}
  draft(field:keyof McpDraft,value:string):void{
    if(this.state.modal!=='add'&&this.state.modal!=='token')return;
    // Programmatic edits invalidate an in-flight probe as well as disabled UI edits.
    this.invalidate();this.state={...this.state,working:false,draft:{...this.state.draft,[field]:value},notice:''};this.publish();
  }
  private validToken(value:string):boolean{return !/[\r\n\0]/.test(value)&&utf8Size(value)<=1024;}
  private fail(notice:McpNotice):void{this.state={...this.state,notice};this.publish();}
  private async discover(server:McpServer,token:string):Promise<McpServer>{const client=new McpHttpClient(server,token,this.transport());this.client=client;try{return applyMcpDiscovery(server,await client.discoverTools(),this.now());}finally{client.close();if(this.client===client)this.client=null;}}
  async add():Promise<void>{
    if(this.state.working||this.state.modal!=='add')return;
    const draft=copy(this.state.draft),name=draft.name.trim(),token=draft.token.trim();
    if(!name){this.fail('name_required');return;}
    let url:string;try{url=validateMcpEndpoint(draft.url);}catch(_){this.fail('invalid_url');return;}
    if(!this.validToken(token)){this.fail('token_invalid');return;}
    const gen=this.generation;this.state={...this.state,working:true,notice:''};this.publish();
    try{
      const server={...emptyMcpServer(name,url,this.now()),authorizationType:token?'bearer':'none'};
      const discovered=await this.discover(server,token);if(gen!==this.generation)return;
      await this.repository.add(discovered,token);const servers=await this.repository.list();if(gen!==this.generation)return;
      this.state={...this.state,servers,working:false,modal:'',draft:{name:'',url:'',token:''},notice:'added',noticeCount:discovered.tools.length};
    }catch(_){if(gen!==this.generation)return;this.state={...this.state,working:false,notice:'connection_failed'};}
    this.publish();
  }
  async refreshTools():Promise<void>{
    const server=this.selected();if(!server||this.state.working||this.state.loading)return;
    const gen=this.generation;this.state={...this.state,working:true,notice:''};this.publish();
    try{const token=await this.repository.token(server.id);if(gen!==this.generation)return;const updated=await this.discover(server,token);if(gen!==this.generation)return;await this.repository.update(updated);const servers=await this.repository.list();if(gen!==this.generation)return;this.state={...this.state,servers,working:false,notice:'refreshed',noticeCount:updated.tools.length};}
    catch(_){if(gen!==this.generation)return;this.state={...this.state,working:false,notice:'refresh_failed'};}this.publish();
  }
  private async mutate(action:()=>Promise<void>,after?:()=>void):Promise<boolean>{
    if(this.state.working||this.state.loading)return false;const gen=this.generation;this.state={...this.state,working:true,notice:''};this.publish();
    try{await action();const servers=await this.repository.list();if(gen!==this.generation)return false;this.state={...this.state,servers,working:false};after?.();this.publish();return true;}
    catch(_){if(gen===this.generation){this.state={...this.state,working:false,notice:'save_failed'};this.publish();}return false;}
  }
  async setEnabled(value:boolean):Promise<void>{const server=this.selected();if(server)await this.mutate(()=>this.repository.setEnabled(server.id,value));}
  async setTool(name:string,value:boolean):Promise<void>{const server=this.selected(),tool=server?.tools.find(t=>t.name===name);if(!server||!tool||this.state.working)return;if(value&&tool.readOnlyHint!==true){this.state={...this.state,modal:'risk',riskyToolName:name};this.publish();return;}await this.mutate(()=>this.repository.setToolEnabled(server.id,name,value));}
  async confirmRisk():Promise<void>{const server=this.selected(),name=this.state.riskyToolName;if(!server||this.state.modal!=='risk')return;await this.mutate(()=>this.repository.setToolEnabled(server.id,name,true),()=>{this.state={...this.state,modal:'',riskyToolName:''};});}
  async saveToken():Promise<void>{const server=this.selected(),token=this.state.draft.token.trim();if(!server||this.state.modal!=='token')return;if(!this.validToken(token)){this.fail('token_invalid');return;}await this.mutate(()=>this.repository.update({...server,authorizationType:token?'bearer':'none'},token),()=>{this.state={...this.state,modal:'',draft:{name:'',url:'',token:''}};});}
  async delete():Promise<boolean>{const server=this.selected();if(!server||this.state.modal!=='delete')return false;return this.mutate(()=>this.repository.delete(server.id),()=>{this.state={...this.state,modal:'',selectedId:''};});}
}
