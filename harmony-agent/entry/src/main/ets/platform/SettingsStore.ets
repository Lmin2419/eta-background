import { preferences } from '@kit.ArkData';
import { common } from '@kit.AbilityKit';
import { RuntimeDatabase } from './RuntimeStorage';
import { SettingValue, SETTING_KEYS, parseSettings } from '../core/settings';
export class SettingsStore {
 private values:Record<string,SettingValue>={};private db:RuntimeDatabase;
 constructor(db:RuntimeDatabase){this.db=db;}
 static async open(context:common.UIAbilityContext,db:RuntimeDatabase):Promise<SettingsStore>{
  const store=new SettingsStore(db),raw=await db.read('settings');if(raw){store.values=parseSettings(raw);return store;}
  const old=await preferences.getPreferences(context,{name:'agent_settings'});
  for(const key of SETTING_KEYS){if(await old.has(key)){const value=await old.get(key,'');if(typeof value==='string'||typeof value==='number'||typeof value==='boolean')store.values[key]=value;}}
  store.values=parseSettings(JSON.stringify(store.values));await store.flush();return store;
 }
 snapshot():Record<string,SettingValue>{return parseSettings(JSON.stringify(this.values));}
 async get(key:string,fallback:SettingValue):Promise<SettingValue>{return this.values[key]===undefined?fallback:this.values[key];}
 async put(key:string,value:SettingValue):Promise<void>{if(!SETTING_KEYS.includes(key))throw new Error('未注册偏好项');this.values[key]=value;}
 flush():Promise<void>{return this.db.write('settings',JSON.stringify(parseSettings(JSON.stringify(this.values))));}
}
