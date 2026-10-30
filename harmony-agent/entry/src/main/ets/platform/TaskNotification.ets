import { common, bundleManager, wantAgent, WantAgent } from '@kit.AbilityKit';
import { notificationManager } from '@kit.NotificationKit';
import { TaskNotice, TaskNoticeQueue } from '../core/taskNotice';

export class TaskNotification {
  private context:common.UIAbilityContext;
  private open:WantAgent|undefined;
  private stop:WantAgent|undefined;
  private queue:TaskNoticeQueue;
  private prepared:boolean=false;
  constructor(context:common.UIAbilityContext,warning:(message:string)=>void){
    this.context=context;this.queue=new TaskNoticeQueue((notice:TaskNotice):Promise<void>=>this.publish(notice),warning);
  }
  async start(token:string):Promise<void>{
    if(!await notificationManager.isNotificationEnabled())await notificationManager.requestEnableNotification(this.context);
    if(!await notificationManager.isNotificationEnabled())throw new Error('请允许助手通知，以便在操作手机时查看和停止任务');
    const own=bundleManager.getBundleInfoForSelfSync(bundleManager.BundleFlag.GET_BUNDLE_INFO_WITH_APPLICATION);
    this.open=await wantAgent.getWantAgent({wants:[{bundleName:own.name,abilityName:'EntryAbility',uri:'harmonyagent://assistant'}],operationType:wantAgent.OperationType.START_ABILITY,requestCode:40,wantAgentFlags:[wantAgent.WantAgentFlags.UPDATE_PRESENT_FLAG]});
    this.stop=await wantAgent.getWantAgent({wants:[{bundleName:own.name,abilityName:'EntryAbility',uri:'harmonyagent://task-stop',parameters:{token:token}}],operationType:wantAgent.OperationType.START_ABILITY,requestCode:41,wantAgentFlags:[wantAgent.WantAgentFlags.UPDATE_PRESENT_FLAG]});
    this.prepared=true;await this.queue.start();
  }
  private async publish(notice:TaskNotice):Promise<void>{
    if(!this.prepared||!this.open||!this.stop)return;
    await notificationManager.publish({id:4001,notificationSlotType:notificationManager.SlotType.SERVICE_INFORMATION,
      content:{notificationContentType:notificationManager.ContentType.NOTIFICATION_CONTENT_BASIC_TEXT,normal:{title:notice.title,text:notice.text,additionalText:'手机任务'}},
      isOngoing:notice.running,tapDismissed:!notice.running,wantAgent:this.open,
      actionButtons:notice.running?[{title:'返回助手',wantAgent:this.open},{title:'停止任务',wantAgent:this.stop}]:[{title:'查看结果',wantAgent:this.open}]});
  }
  update(text:string):void{if(this.prepared)this.queue.update(text);}
  async finish(success:boolean,stopped:boolean):Promise<void>{if(this.prepared)await this.queue.finish(success,stopped);}
}
