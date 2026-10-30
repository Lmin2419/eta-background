import { common, bundleManager, wantAgent, WantAgent } from '@kit.AbilityKit';
import { notificationManager } from '@kit.NotificationKit';
import { screenLock } from '@kit.BasicServicesKit';
import { NotificationDriver, NoticeView, NoticeAction } from '../core/notifications';

export class HarmonyNotifications implements NotificationDriver {
  screenOn=true;
  private context:()=>common.UIAbilityContext;
  constructor(context:()=>common.UIAbilityContext){this.context=context;}
  enabled():Promise<boolean>{return notificationManager.isNotificationEnabled();}
  async request():Promise<boolean>{if(!await this.enabled())await notificationManager.requestEnableNotification(this.context());return this.enabled();}
  async activeIds():Promise<number[]>{return (await notificationManager.getActiveNotifications()).map((n:notificationManager.NotificationRequest):number=>n.id||0);}
  async canAlert():Promise<boolean>{try{return this.screenOn&&!await screenLock.isScreenLocked();}catch(_){return false;}}
  private async agent(view:NoticeView,action:string,offset:number):Promise<WantAgent>{
    const own=bundleManager.getBundleInfoForSelfSync(bundleManager.BundleFlag.GET_BUNDLE_INFO_WITH_APPLICATION);
    return wantAgent.getWantAgent({wants:[{bundleName:own.name,abilityName:'EntryAbility',uri:'harmonyagent://assistant',parameters:{runId:view.runId,conversationId:view.conversationId,taskAction:action,taskToken:view.token}}],operationType:wantAgent.OperationType.START_ABILITY,requestCode:view.id*8+offset,wantAgentFlags:[wantAgent.WantAgentFlags.UPDATE_PRESENT_FLAG]});
  }
  async publish(view:NoticeView):Promise<void>{
    const actions:notificationManager.NotificationActionButton[]=[];
    for(let i=0;i<view.actions.length;i++){
      const action:NoticeAction=view.actions[i];
      const button:notificationManager.NotificationActionButton={title:action.label,wantAgent:await this.agent(view,action.name,i+1)};
      if(action.input)button.userInput={inputKey:'harmony.reply'};
      actions.push(button);
    }
    const removed=await wantAgent.getWantAgent({wants:[{action:'com.example.harmonyagent.TASK_NOTICE_REMOVED',parameters:{notificationId:view.id,taskToken:view.token}}],operationType:wantAgent.OperationType.SEND_COMMON_EVENT,requestCode:view.id*8+7,wantAgentFlags:[wantAgent.WantAgentFlags.UPDATE_PRESENT_FLAG]});
    await notificationManager.publish({id:view.id,notificationSlotType:view.alert?notificationManager.SlotType.SERVICE_INFORMATION:notificationManager.SlotType.CONTENT_INFORMATION,
      content:{notificationContentType:notificationManager.ContentType.NOTIFICATION_CONTENT_BASIC_TEXT,normal:{title:view.title,text:view.text,additionalText:'随身助手'}},
      updateOnly:view.updateOnly,tapDismissed:false,isAlertOnce:!view.alert,
      wantAgent:await this.agent(view,'open',0),actionButtons:actions,removalWantAgent:removed});
  }
  cancel(id:number):Promise<void>{return notificationManager.cancel(id);}
}
