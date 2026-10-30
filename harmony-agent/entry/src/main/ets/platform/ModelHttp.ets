import { http } from '@kit.NetworkKit';
import { deviceInfo } from '@kit.BasicServicesKit';
import { util } from '@kit.ArkTS';
import { HttpTransport } from '../core/model';

export class HarmonyHttpTransport implements HttpTransport {
  private request: http.HttpRequest | undefined;
  private abortStream: (()=>void) | undefined;
  cancel(): void { this.abortStream?.(); this.request?.destroy(); this.request = undefined; }
  async stream(url:string, headers:Record<string,string>, body:string, onChunk:(chunk:string)=>void):Promise<void> {
    if(deviceInfo.sdkApiVersion<23)throw new Error('模型网络连接需要 API 23 及以上系统');
    const request=http.createHttp();this.request=request;
    const decoder=util.TextDecoder.create('utf-8',{fatal:true});
    try {
      await new Promise<void>((resolve,reject)=>{
        let ended=false,accepted=false,settled=false,bytes=0;
        let timer:number=-1;
        const fail=(error:Error):void=>{if(settled)return;settled=true;clearTimeout(timer);reject(error);};
        const finish=():void=>{if(!settled&&ended&&accepted){settled=true;clearTimeout(timer);resolve();}};
        const idle=():void=>{clearTimeout(timer);timer=setTimeout(()=>fail(new Error('模型流等待新数据超时')),300000);};
        this.abortStream=():void=>fail(new Error('CANCELLED'));
        request.on('dataReceive',(data:ArrayBuffer):void=>{
          if(settled)return;
          try {
            bytes+=data.byteLength;if(bytes>2_000_000)throw new Error('模型响应超过大小限制');
            onChunk(decoder.decodeToString(new Uint8Array(data),{stream:true}));idle();
          }catch(e){fail(e as Error);}
        });
        request.on('dataEnd',():void=>{
          if(settled)return;
          try {onChunk(decoder.decodeToString(new Uint8Array(0),{stream:false}));ended=true;finish();}
          catch(e){fail(e as Error);}
        });
        idle();
        request.requestInStream(url,{method:http.RequestMethod.POST,header:headers,extraData:body,
          connectTimeout:15000,readTimeout:300000,usingCache:false,maxRedirects:0,maxLimit:2_000_000})
          .then((code:number):void=>{
            if(code<200||code>=300)fail(new Error('模型服务返回 HTTP '+code+'，请检查地址、密钥、额度或流式支持'));
            else {accepted=true;finish();}
          }).catch(()=>fail(new Error('模型流连接中断，请检查网络和接口；不会自动重复请求')));
      });
    } finally {this.abortStream=undefined;request.destroy();if(this.request===request)this.request=undefined;}
  }
  async post(url: string, headers: Record<string,string>, body: string): Promise<string> {
    return this.send(url,headers,http.RequestMethod.POST,body);
  }
  async get(url: string, headers: Record<string,string>): Promise<string> {
    return this.send(url,headers,http.RequestMethod.GET);
  }
  private async send(url: string, headers: Record<string,string>, method: http.RequestMethod, body?: string): Promise<string> {
    if (deviceInfo.sdkApiVersion < 23) throw new Error('模型网络连接需要 API 23 及以上系统，以禁止携带密钥的自动重定向');
    const request = http.createHttp(); this.request = request;
    try {
      const response = await request.request(url, {
        method: method, header: headers, extraData: body,
        expectDataType: http.HttpDataType.STRING, connectTimeout: 15000, readTimeout: 60000,
        usingCache: false, maxRedirects: 0, maxLimit: 2_000_000
      });
      if (response.responseCode < 200 || response.responseCode >= 300) {
        throw new Error('模型服务返回 HTTP ' + response.responseCode + '，请检查 API 地址、密钥和额度');
      }
      return response.result as string;
    } catch (e) {
      const message = (e as Error).message;
      if (message.startsWith('模型服务返回 HTTP ')) throw new Error(message);
      throw new Error('模型请求失败：请检查网络、证书和接口地址；不自动跟随重定向');
    } finally { request.destroy(); if (this.request === request) this.request = undefined; }
  }
}

