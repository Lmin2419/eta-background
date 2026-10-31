// Harmony port for app/src/main/kotlin/io/github/mangi/eta/agent/mcp/McpHttpClient.kt and data/repository/McpSecretStore.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11). No model tool registration; only user-requested catalog discovery.
import {http} from '@kit.NetworkKit';
import {asset} from '@kit.AssetStoreKit';
import {util} from '@kit.ArkTS';
import {BusinessError,deviceInfo} from '@kit.BasicServicesKit';
import {SecretStorage} from '../core/profiles';
import {McpServerRepository} from '../core/eta/mcp/mcpRepository';
import {McpTransport,McpWireRequest,McpWireResponse,mcpHeaders} from '../core/eta/mcp/mcpHttpClient';
import {RuntimeDatabase,DatabaseDocumentStorage} from './RuntimeStorage';

class McpAssetSecrets implements SecretStorage {
  private encoder:util.TextEncoder=new util.TextEncoder();
  private query(alias:string):asset.AssetMap{const query:asset.AssetMap=new Map();query.set(asset.Tag.ALIAS,this.encoder.encodeInto(alias));return query;}
  async put(alias:string,value:string):Promise<void>{
    const bytes=this.encoder.encodeInto(value);try{
      if(bytes.length>1024)throw new Error('MCP_TOKEN_LIMIT');const attributes=this.query(alias);
      attributes.set(asset.Tag.SECRET,bytes);attributes.set(asset.Tag.ACCESSIBILITY,asset.Accessibility.DEVICE_FIRST_UNLOCKED);
      attributes.set(asset.Tag.SYNC_TYPE,asset.SyncType.NEVER);attributes.set(asset.Tag.CONFLICT_RESOLUTION,asset.ConflictResolution.OVERWRITE);
      await asset.add(attributes);
    }catch(_){throw new Error('MCP_SECRET_WRITE_FAILED');}finally{bytes.fill(0);}
  }
  async get(alias:string):Promise<string>{
    const query=this.query(alias);query.set(asset.Tag.RETURN_TYPE,asset.ReturnType.ALL);
    try{const records=await asset.query(query);const bytes=records[0]?.get(asset.Tag.SECRET) as Uint8Array;if(!bytes)throw new Error('MCP_SECRET_MISSING');try{return util.TextDecoder.create('utf-8').decodeToString(bytes);}finally{bytes.fill(0);}}
    catch(_){throw new Error('MCP_SECRET_READ_FAILED');}
  }
  async remove(alias:string):Promise<void>{try{await asset.remove(this.query(alias));}catch(error){if((error as BusinessError).code!==24000002)throw new Error('MCP_SECRET_REMOVE_FAILED');}}
}
export function createEtaMcpRepository(db:RuntimeDatabase):McpServerRepository{return new McpServerRepository(new DatabaseDocumentStorage(db,'eta_mcp_servers'),new McpAssetSecrets(),()=>util.generateRandomUUID());}

export class HarmonyMcpTransport implements McpTransport {
  private request:http.HttpRequest|undefined;
  private abort:(()=>void)|undefined;
  cancel():void{this.abort?.();this.request?.destroy();this.request=undefined;}
  async exchange(wire:McpWireRequest,onChunk:(chunk:string,headers:Record<string,string>)=>boolean):Promise<McpWireResponse>{
    if(deviceInfo.sdkApiVersion<23)throw new Error('MCP_API_UNSUPPORTED');
    const request=http.createHttp();this.request=request;const decoder=util.TextDecoder.create('utf-8',{fatal:true});
    try{return await new Promise<McpWireResponse>((resolve,reject)=>{
      let done=false,ended=false,headersReady=false,code:number|undefined,bytes=0;
      let headers:Record<string,string>={};let pending:string[]=[];let timer:number=-1;
      const fail=(error:Error):void=>{if(done)return;done=true;clearTimeout(timer);reject(error);};
      const finish=():void=>{if(!done&&ended&&code!==undefined){done=true;clearTimeout(timer);resolve({status:code,headers});}};
      const feed=(chunk:string):void=>{if(ended||done)return;if(!headersReady){pending.push(chunk);return;}if(!onChunk(chunk,headers)){ended=true;finish();}};
      this.abort=():void=>fail(new Error('MCP_CANCELLED'));
      timer=setTimeout(()=>fail(new Error('MCP_TIMEOUT')),60000);
      request.on('headersReceive',(raw:Object):void=>{if(done)return;try{headers=mcpHeaders(raw);headersReady=true;const queued=pending;pending=[];queued.forEach(feed);}catch(_){fail(new Error('MCP_RESPONSE_INVALID'));}});
      request.on('dataReceive',(data:ArrayBuffer):void=>{if(done||ended)return;try{bytes+=data.byteLength;if(bytes>1048576)throw new Error('MCP_RESPONSE_TOO_LARGE');feed(decoder.decodeToString(new Uint8Array(data),{stream:true}));}catch(_){fail(new Error('MCP_RESPONSE_INVALID'));}});
      request.on('dataEnd',():void=>{if(done||ended)return;try{if(!headersReady)throw new Error('MCP_HEADERS_MISSING');feed(decoder.decodeToString(new Uint8Array(0),{stream:false}));ended=true;finish();}catch(_){fail(new Error('MCP_RESPONSE_INVALID'));}});
      request.requestInStream(wire.url,{method:http.RequestMethod.POST,header:wire.headers,extraData:wire.body,usingCache:false,maxRedirects:0,maxLimit:1048576,connectTimeout:15000,readTimeout:60000})
        .then((status:number):void=>{code=status;finish();}).catch(()=>fail(new Error('MCP_NETWORK_FAILED')));
    });}finally{this.abort=undefined;request.destroy();if(this.request===request)this.request=undefined;}
  }
  release(wire:McpWireRequest):void{
    if(deviceInfo.sdkApiVersion<23)return;const request=http.createHttp();
    void request.request(wire.url,{method:http.RequestMethod.DELETE,header:wire.headers,usingCache:false,maxRedirects:0,maxLimit:1048576,connectTimeout:15000,readTimeout:15000})
      .catch(()=>{}).finally(()=>request.destroy());
  }
}
