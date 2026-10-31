// Ported from app/src/main/kotlin/io/github/mangi/eta/data/repository/CommunityCatalogRepository.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { common } from '@kit.AbilityKit';
import { http } from '@kit.NetworkKit';
import { util } from '@kit.ArkTS';
import { RuntimeDatabase } from './RuntimeStorage';
import { CommunityCatalogPort, CatalogCache } from '../core/eta/communityCatalog';
export class EtaCommunityCatalog implements CommunityCatalogPort {
  private context:common.UIAbilityContext;private database:RuntimeDatabase;private request:http.HttpRequest|undefined;
  constructor(context:common.UIAbilityContext,database:RuntimeDatabase){this.context=context;this.database=database;}
  async readCache():Promise<CatalogCache|null>{const raw=await this.database.read('eta_community_catalog');return raw?JSON.parse(raw) as CatalogCache:null;}
  async writeCache(cache:CatalogCache):Promise<void>{await this.database.write('eta_community_catalog',JSON.stringify(cache));}
  async readSnapshot():Promise<string>{return util.TextDecoder.create('utf-8',{fatal:true}).decodeToString(await this.context.resourceManager.getRawFileContent('catalog/models-dev-api.json'));}
  cancel():void{this.request?.destroy();this.request=undefined;}
  async fetch():Promise<string>{
    const request=http.createHttp();this.request=request;
    try{
      const response=await request.request('https://models.dev/api.json',{method:http.RequestMethod.GET,header:{'Accept':'application/json','User-Agent':'Harmony-Agent-Model-Catalog (+https://github.com/Lmin2419/eta-background)'},expectDataType:http.HttpDataType.STRING,connectTimeout:10000,readTimeout:30000,usingCache:false,maxRedirects:0,maxLimit:8*1024*1024});
      if(response.responseCode<200||response.responseCode>=300)throw Error('目录更新失败：HTTP '+response.responseCode);
      const headers=response.header as Record<string,Object>;let json=false;
      for(const key of Object.keys(headers))if(key.toLowerCase()==='content-type')json=String(headers[key]).toLowerCase().startsWith('application/json');
      if(!json)throw Error('目录更新失败：响应格式不是 JSON');return response.result as string;
    }finally{request.destroy();if(this.request===request)this.request=undefined;}
  }
}
