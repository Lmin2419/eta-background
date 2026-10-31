// Ported from app/src/main/kotlin/io/github/mangi/eta/data/model/AppearanceSettings.kt,
// data/repository/AppearanceSettingsRepository.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
export type AppearanceThemeMode='system'|'light'|'dark';
export type AppearanceTopBarBlurStyle='gaussian'|'progressive';
export interface AppearanceSettings {
  themeMode:AppearanceThemeMode;monetEnabled:boolean;paletteStyle:string;accentColor:string;pureBlackEnabled:boolean;
  blurEnabled:boolean;runtimeGlowEnabled:boolean;taskCompletionAlertsEnabled:boolean;topBarBlurStyle:AppearanceTopBarBlurStyle;
  swipeDismissEnabled:boolean;predictiveBackEnabled:boolean;interfaceScale:number;
}
export function normalizeInterfaceScale(value:number):number{return Number.isFinite(value)?Math.min(1.1,Math.max(.8,value)):1;}
export function defaultAppearance():AppearanceSettings{return {themeMode:'system',monetEnabled:false,paletteStyle:'tonal_spot',accentColor:'system',pureBlackEnabled:false,blurEnabled:true,runtimeGlowEnabled:true,taskCompletionAlertsEnabled:true,topBarBlurStyle:'gaussian',swipeDismissEnabled:true,predictiveBackEnabled:true,interfaceScale:1};}
export function normalizeAppearance(value:Partial<AppearanceSettings>):AppearanceSettings{
  const next={...defaultAppearance(),...value};next.themeMode=['system','light','dark'].includes(next.themeMode)?next.themeMode:'system';
  next.topBarBlurStyle=next.topBarBlurStyle==='progressive'?'progressive':'gaussian';next.interfaceScale=normalizeInterfaceScale(next.interfaceScale);
  if(typeof next.blurEnabled!=='boolean')next.blurEnabled=true;return next;
}
export interface AppearanceStorage {read():Promise<string>;write(value:string):Promise<void>;}
export interface AppearancePlatform {apply(settings:AppearanceSettings):Promise<void>;}
/** Serializes platform apply + durable preferences. Failed writes restore the previous applied state. */
export class AppearanceSettingsRepository {
  private value=defaultAppearance();private tail:Promise<void>=Promise.resolve();private loading:Promise<void>|undefined;
  constructor(private storage:AppearanceStorage,private platform:AppearancePlatform,private changed:()=>void=()=>{},private recovered:()=>void=()=>{}){}
  snapshot():AppearanceSettings{return {...this.value};}
  load():Promise<void>{
    if(!this.loading)this.loading=(async()=>{
      const raw=await this.storage.read();let parsed:Partial<AppearanceSettings>={};
      try{const candidate:unknown=raw?JSON.parse(raw):{};
        if(!candidate||typeof candidate!=='object'||Array.isArray(candidate))throw Error('Invalid appearance');
        parsed=candidate as Partial<AppearanceSettings>;
      }catch(_){this.recovered();}
      const value=normalizeAppearance(parsed);await this.platform.apply(value);this.value=value;this.changed();
    })().catch(error=>{this.loading=undefined;throw error;});
    return this.loading;
  }
  update(patch:Partial<AppearanceSettings>):Promise<void>{const run=this.tail.then(async()=>{await this.load();const old=this.value,next=normalizeAppearance({...old,...patch});
    try{await this.platform.apply(next);await this.storage.write(JSON.stringify(next));}catch(error){try{await this.platform.apply(old);}catch(_){}throw error;}
    this.value=next;this.changed();});this.tail=run.catch(()=>{});return run;
  }
}
