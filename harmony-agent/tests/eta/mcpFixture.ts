// Ported fixture for app/src/test/kotlin/io/github/mangi/eta/agent/mcp/McpProtocolValidationTest.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11). Real loopback HTTP keeps SSE/cancellation assertions meaningful.
import {createServer,IncomingHttpHeaders,ServerResponse} from 'node:http';
import {AddressInfo} from 'node:net';
import {McpTransport,McpWireRequest,McpWireResponse} from '../../entry/src/main/ets/core/eta/mcp/mcpHttpClient';
import {JsonObject} from '../../entry/src/main/ets/core/eta/protocol/providerTypes';
export interface Received {method:string;headers:IncomingHttpHeaders;body:JsonObject;}
export function respond(response:ServerResponse,status:number,body:object|string,headers:Record<string,string>={}):void{response.writeHead(status,{'Content-Type':'application/json',...headers});response.end(typeof body==='string'?body:JSON.stringify(body));}
export async function fixture(handler:(request:Received,response:ServerResponse)=>void){
 const received:Received[]=[];const server=createServer(async(req,res)=>{let raw='';for await(const chunk of req)raw+=chunk;const request={method:req.method||'',headers:req.headers,body:raw?JSON.parse(raw) as JsonObject:{}};received.push(request);handler(request,res);});
 await new Promise<void>(resolve=>server.listen(0,'127.0.0.1',resolve));
 return {url:'http://127.0.0.1:'+(server.address() as AddressInfo).port+'/mcp',received,close:async()=>{server.closeAllConnections();await new Promise<void>(resolve=>server.close(()=>resolve()));}};
}
export class FetchMcpTransport implements McpTransport {
 private abort:AbortController|null=null;releaseDone:Promise<void>=Promise.resolve();
 async exchange(request:McpWireRequest,onChunk:(chunk:string,headers:Record<string,string>)=>boolean):Promise<McpWireResponse>{
  const abort=new AbortController();this.abort=abort;const response=await fetch(request.url,{method:request.method,headers:request.headers,body:request.body,signal:abort.signal,redirect:'manual'}),headers:Record<string,string>={},reader=response.body?.getReader(),decoder=new TextDecoder('utf-8',{fatal:true});response.headers.forEach((value,key)=>{headers[key]=value;});
  try{if(reader)while(true){const next=await reader.read();if(next.done){onChunk(decoder.decode(),headers);break;}if(!onChunk(decoder.decode(next.value,{stream:true}),headers)){await reader.cancel();break;}}return {status:response.status,headers};}
  finally{if(this.abort===abort)this.abort=null;}
 }
 cancel():void{this.abort?.abort();this.abort=null;}
 release(request:McpWireRequest):void{this.releaseDone=fetch(request.url,{method:'DELETE',headers:request.headers,redirect:'manual'}).then(async r=>{await r.arrayBuffer();}).catch(()=>{});}
}
export const modernResult=(id:unknown,tools:object[]=[],ttl=1000):object=>({jsonrpc:'2.0',id,result:{resultType:'complete',ttlMs:ttl,tools}});
export const legacyResult=(id:unknown,tools:object[]=[]):object=>({jsonrpc:'2.0',id,result:{tools}});
export function initialize(response:ServerResponse,id:unknown,version='2025-11-25',session='session-1'):void{respond(response,200,{jsonrpc:'2.0',id,result:{protocolVersion:version,capabilities:{},serverInfo:{name:'test',version:'1'}}},session?{'Mcp-Session-Id':session}:{});}
