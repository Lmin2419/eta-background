import { http } from '@kit.NetworkKit';
import { RpcTransport, RpcResponse } from '../core/mcp';
export class HarmonyRpcTransport implements RpcTransport {
 private request:http.HttpRequest|undefined;
 cancel():void{this.request?.destroy();this.request=undefined;}
 async send(url:string,headers:Record<string,string>,body:string):Promise<RpcResponse>{
  const request=http.createHttp();this.request=request;
  try{const response=await request.request(url,{method:http.RequestMethod.POST,header:headers,extraData:body,expectDataType:http.HttpDataType.STRING,connectTimeout:15000,readTimeout:60000,usingCache:false,maxRedirects:0,maxLimit:2_000_000});return {status:response.responseCode,headers:JSON.stringify(response.header),body:typeof response.result==='string'?response.result:''};}
  catch(_){throw new Error('MCP 连接失败或超时；未知工具结果不会自动重试');}
  finally{request.destroy();if(this.request===request)this.request=undefined;}
 }
}
