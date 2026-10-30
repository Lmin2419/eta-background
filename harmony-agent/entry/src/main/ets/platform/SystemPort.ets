import { common, abilityAccessCtrl } from '@kit.AbilityKit';
import { batteryInfo } from '@kit.BasicServicesKit';
import { connection } from '@kit.NetworkKit';
import { contact } from '@kit.ContactsKit';
import { calendarManager } from '@kit.CalendarKit';
import { geoLocationManager } from '@kit.LocationKit';
import { SystemPort } from '../core/systemTools';
interface PickedContact {name:string;phones:string[]}
export class HarmonySystemPort implements SystemPort {
 private context:()=>common.UIAbilityContext;private visible:()=>boolean;
 constructor(context:()=>common.UIAbilityContext,visible:()=>boolean){this.context=context;this.visible=visible;}
 foreground():boolean{return this.visible();}
 async status():Promise<string>{let network='不可用';try{const handle=await connection.getDefaultNet(),caps=await connection.getNetCapabilities(handle);network=JSON.stringify(caps.bearerTypes);}catch(_){}return JSON.stringify({batteryPercent:batteryInfo.batterySOC,charging:batteryInfo.chargingStatus,time:new Date().toISOString(),networkBearerTypes:network});}
 async contact():Promise<string>{const result=await contact.selectContacts({isMultiSelect:false});return JSON.stringify(result.slice(0,1).map((c:contact.Contact):PickedContact=>({name:c.name?.fullName||'',phones:(c.phoneNumbers||[]).map((p:contact.PhoneNumber):string=>p.phoneNumber).slice(0,5)})));}
 calendar(title:string,start:number,end:number):Promise<number>{return calendarManager.getCalendarManager(this.context()).editEvent({type:calendarManager.EventType.NORMAL,title,startTime:start,endTime:end});}
 async location():Promise<string>{
  const granted=await abilityAccessCtrl.createAtManager().requestPermissionsFromUser(this.context(),['ohos.permission.APPROXIMATELY_LOCATION']);if(granted.authResults.some(v=>v!==0))throw new Error('未授予大致位置权限');if(!this.visible())throw new Error('已离开助手，位置请求没有继续');
  const location=await geoLocationManager.getCurrentLocation({priority:geoLocationManager.LocationRequestPriority.FIRST_FIX,timeoutMs:10000,maxAccuracy:0});return JSON.stringify({latitude:location.latitude,longitude:location.longitude,accuracy:location.accuracy,timeStamp:location.timeStamp});
 }
}
