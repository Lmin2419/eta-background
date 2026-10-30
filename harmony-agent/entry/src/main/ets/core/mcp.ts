import { McpConfig, mcpUrl } from './extensions';
import { DeviceTools, RunControl, ToolResult, UnknownToolOutcomeError } from './types';
export interface RpcResponse {status:number;headers:string;body:string}
export interface RpcTransport {send(url:string,headers:Record<string,string>,body:string):Promise<RpcResponse>;cancel():void}
export interface RemoteTool {name:string;description:string;inputSchema:Record<string,unknown>}
function parse(raw:string):any{try{return JSON.parse(raw);}catch(_){throw new Error('MCP 返回无效 JSON');}}
function rpcResult(raw:string,id:number):any{
 if(raw.length>2_000_000)throw new Error('MCP 响应过大');
 const events:any[]=[];
 if(raw.trimStart().startsWith('{'))events.push(parse(raw));else{
  for(const frame of raw.replace(/\r\n/g,'\n').split('\n\n')){const lines=frame.split('\n').filter(l=>l.startsWith('data:')).map(l=>l.slice(5).trimStart());if(lines.length)events.push(parse(lines.join('\n')));}
 }
 const matches=events.filter(e=>e.jsonrpc==='2.0'&&e.id===id);if(matches.length!==1)throw new Error('MCP 响应 ID 缺失或重复；未自动重放请求');
 const e=matches[0];if(e.error)throw new Error('MCP 请求返回错误 '+String(e.error.code||''));if(e.result===undefined)throw new Error('MCP 响应缺少结果');return e.result;
}
/** Streamable HTTP only. A lost session never causes an automatic tool retry. */
export class McpClient implements DeviceTools{
 private session='';private protocol='2025-03-26';private nextId=1;private closed=false;private ready=false;private aliases:Map<string,string>=new Map();
 tools:RemoteTool[]=[];
 constructor(private config:McpConfig,private key:string,private io:RpcTransport) {mcpUrl(config.url);if(/[\r\n\0]/.test(key))throw new Error('MCP 凭据格式无效');}
 invalidate():void{}
 cancel():void{this.closed=true;this.key='';this.io.cancel();}
 private async send(method:string,params:object,control:RunControl,notification=false):Promise<any>{
  control.check();if(this.closed)throw new Error('MCP 连接已关闭');
  const id=this.nextId++,headers:Record<string,string>={'Content-Type':'application/json','Accept':'application/json, text/event-stream','MCP-Protocol-Version':this.protocol};
  if(this.key)headers.Authorization='Bearer '+this.key;if(this.session)headers['Mcp-Session-Id']=this.session;
  const response=await this.io.send(this.config.url,headers,JSON.stringify({jsonrpc:'2.0',...(notification?{}:{id}),method,params}));control.check();if(this.closed)throw new Error('CANCELLED');
  if(response.status===404&&this.session){this.ready=false;throw new Error('MCP 会话已失效，请重新连接；未知操作没有重试');}
  if(response.status<200||response.status>=300)throw new Error('MCP HTTP '+response.status+'；没有自动重复请求');
  const parsedHeaders=parse(response.headers||'{}');for(const k of Object.keys(parsedHeaders))if(k.toLowerCase()==='mcp-session-id'){const value=parsedHeaders[k];if(typeof value!=='string'||!value||value.length>512||/[\x00-\x20\x7f]/.test(value))throw new Error('MCP 会话标识无效');if(this.session&&this.session!==value)throw new Error('MCP 会话意外变化');this.session=value;}
  if(notification)return undefined;return rpcResult(response.body,id);
 }
 async connect(control:RunControl):Promise<RemoteTool[]>{
  if(this.ready)return this.tools;
  const init=await this.send('initialize',{protocolVersion:this.protocol,capabilities:{},clientInfo:{name:'harmony-agent',version:'0.8.0'}},control);
  if(!['2025-03-26','2025-06-18','2024-11-05'].includes(init.protocolVersion))throw new Error('服务器 MCP 协议版本暂不支持');this.protocol=init.protocolVersion;
  await this.send('notifications/initialized',{},control,true);const all:RemoteTool[]=[],seen=new Set<string>();let cursor='';
  for(let page=0;page<20;page++){
   const result=await this.send('tools/list',cursor?{cursor}:{},control);if(!Array.isArray(result.tools))throw new Error('MCP 工具目录无效');
   for(const t of result.tools){if(typeof t.name!=='string'||!t.name||t.name.length>128||!t.inputSchema||t.inputSchema.type!=='object')throw new Error('MCP 工具定义无效');if(all.some(x=>x.name===t.name))throw new Error('MCP 工具目录存在重复名称');all.push({name:t.name,description:typeof t.description==='string'?t.description.slice(0,3000):t.name,inputSchema:t.inputSchema});if(all.length>100)throw new Error('MCP 工具超过 100 个，请缩小服务范围');}
   if(!result.nextCursor){this.tools=all;this.ready=true;return all;}
   if(typeof result.nextCursor!=='string'||result.nextCursor.length>1024||seen.has(result.nextCursor))throw new Error('MCP 分页游标无效');seen.add(result.nextCursor);cursor=result.nextCursor;
  }
  throw new Error('MCP 目录分页超过上限');
 }
 definitions():Record<string,object>[] {
  if(!this.ready)throw new Error('MCP 尚未连接');this.aliases.clear();
  let hash=0;for(const c of this.config.id)hash=(Math.imul(hash,31)+c.charCodeAt(0))>>>0;
  return this.tools.filter(t=>this.config.enabledTools.includes(t.name)).map((t,i)=>{const name='mcp_'+hash.toString(16)+'_'+i;this.aliases.set(name,t.name);return {type:'function',function:{name,description:'远程服务 '+this.config.name+'：'+t.description,parameters:t.inputSchema}};}) as unknown as Record<string,object>[];
 }
 async execute(name:string,args:Record<string,unknown>,control:RunControl):Promise<ToolResult>{
  const original=this.aliases.get(name);if(!this.ready||!original||!this.config.enabledTools.includes(original))throw new Error('MCP 工具未启用');
  let result:any;try{result=await this.send('tools/call',{name:original,arguments:args},control);}catch(e){control.check();throw new UnknownToolOutcomeError('远程工具结果未知，任务已停止且不会自动重放：'+(e instanceof Error?e.message:'连接失败'));}
  const content=Array.isArray(result.content)?result.content:[];const text=content.filter((p:any)=>p.type==='text'&&typeof p.text==='string').map((p:any)=>p.text).join('\n').slice(0,24000);
  return {ok:result.isError!==true,message:result.isError?'远程工具返回错误':'远程工具已返回；需核对业务结果',data:{text,structuredContent:JSON.stringify(result.structuredContent||{}).slice(0,24000),omittedNonText:content.some((p:any)=>p.type!=='text')}};
 }
}
