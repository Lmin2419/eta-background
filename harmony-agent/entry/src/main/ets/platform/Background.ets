import { abilityAccessCtrl, bundleManager, common, wantAgent } from '@kit.AbilityKit';
import { backgroundTaskManager } from '@kit.BackgroundTasksKit';
import { geoLocationManager } from '@kit.LocationKit';
import { notificationManager } from '@kit.NotificationKit';
import { BusinessError } from '@kit.BasicServicesKit';
import { BackgroundDriver } from '../core/session';

// Optional development mode: actual low-power approximate location subscription.
// Coordinates are deliberately never read, retained, logged, or sent to the model.
export class HarmonyBackground implements BackgroundDriver {
  private context: common.UIAbilityContext;
  private useLocation: boolean;
  private transientId: number = -1;
  private continuousId: number = -1;
  private continuous: boolean = false;
  private continuousCancelled: boolean = false;
  private cancelListening: boolean = false;
  private suspendListening: boolean = false;
  private locationListening: boolean = false;
  private switchListening: boolean = false;
  private permissionTimer: number = -1;
  private closing: boolean = false;
  private stage: string = '准备后台任务';
  private report: (message: string) => void;
  private revoke: (reason: string) => void = (_reason: string): void => {};
  private discardLocation = (_location: geoLocationManager.Location): void => {};
  private locationSwitch = (enabled: boolean): void => {
    if (!enabled && !this.closing) this.revoke('系统定位已关闭，助手任务已停止');
  };
  private cancelled = (info: backgroundTaskManager.ContinuousTaskCancelInfo): void => {
    if (!this.closing && (this.continuousId < 0 || info.id === this.continuousId)) {
      this.continuousCancelled = true; this.continuous = false;
      this.revoke('后台通知已取消或系统已收回任务（原因 '+info.reason+'），助手已停止');
    }
  };
  private suspended = (info: backgroundTaskManager.ContinuousTaskSuspendInfo): void => {
    if (!this.closing && (this.continuousId < 0 || info.continuousTaskId === this.continuousId)) {
      this.revoke('系统已暂停持续后台任务，助手已停止；不会自动重新申请');
    }
  };
  constructor(context: common.UIAbilityContext, useLocation: boolean, report: (message:string)=>void = (_message:string):void=>{}) {
    this.context = context; this.useLocation = useLocation; this.report=report;
  }

  async start(onRevoked: (reason: string) => void, isCancelled: () => boolean): Promise<void> {
    this.revoke = onRevoked;
    if (isCancelled()) throw new Error('CANCELLED');
    if (!this.useLocation) {
      const delay = backgroundTaskManager.requestSuspendDelay('执行用户发起的手机助手任务', (): void => {
        if (!this.closing) this.revoke('短时后台额度即将耗尽，助手任务已停止');
      });
      this.transientId = delay.requestId;
      return;
    }
    try {
    this.stage='通知授权';this.report('正在检查通知权限…');
    if (!await notificationManager.isNotificationEnabled()) await notificationManager.requestEnableNotification(this.context);
    if (isCancelled()) throw new Error('CANCELLED');
    if (!await notificationManager.isNotificationEnabled()) throw new Error('请在通知设置中允许随身助手发送通知');
    this.stage='定位授权';this.report('正在检查定位权限…');
    const access = abilityAccessCtrl.createAtManager();
    const permission = await access.requestPermissionsFromUser(this.context, ['ohos.permission.APPROXIMATELY_LOCATION']);
    if (isCancelled()) throw new Error('CANCELLED');
    if (permission.authResults.length !== 1 || permission.authResults[0] !== abilityAccessCtrl.GrantStatus.PERMISSION_GRANTED) {
      throw new Error('持续后台实验模式需要大致位置权限；可关闭此模式使用短时任务');
    }
    if (!geoLocationManager.isLocationEnabled()) throw new Error('请先开启系统定位，或关闭持续后台实验模式');
    const own = bundleManager.getBundleInfoForSelfSync(bundleManager.BundleFlag.GET_BUNDLE_INFO_WITH_APPLICATION);
    const agent = await wantAgent.getWantAgent({
      wants: [{bundleName: own.name, abilityName: 'EntryAbility', uri:'harmonyagent://assistant'}],
      operationType: wantAgent.OperationType.START_ABILITY,
      requestCode: 1, wantAgentFlags: [wantAgent.WantAgentFlags.UPDATE_PRESENT_FLAG]
    });
    if (isCancelled()) throw new Error('CANCELLED');
    backgroundTaskManager.on('continuousTaskCancel', this.cancelled); this.cancelListening = true;
    backgroundTaskManager.on('continuousTaskSuspend', this.suspended); this.suspendListening = true;
    this.stage='申请系统持续任务';this.report('正在申请系统定位任务通知…');
    const notification = await backgroundTaskManager.startBackgroundRunning(this.context, ['location'], agent);
    this.continuous = !this.continuousCancelled; this.continuousId = notification.continuousTaskId ?? -1;
    if (isCancelled()) throw new Error('CANCELLED');
    this.stage='订阅低功耗定位';
    const request: geoLocationManager.LocationRequest = {
      priority: geoLocationManager.LocationRequestPriority.LOW_POWER,
      timeInterval: 60, distanceInterval: 0, maxAccuracy: 0
    };
    geoLocationManager.on('locationChange', request, this.discardLocation); this.locationListening = true;
    geoLocationManager.on('locationEnabledChange', this.locationSwitch); this.switchListening = true;
    this.stage='确认系统任务';
    const tasks=await backgroundTaskManager.getAllContinuousTasks(this.context);
    if (isCancelled()) throw new Error('CANCELLED');
    const active=tasks.find((task:backgroundTaskManager.ContinuousTaskInfo):boolean=>task.continuousTaskId===this.continuousId);
    if (!active || active.suspendState) throw new Error('系统未确认持续任务处于运行状态');
    this.report('系统任务已确认 · 任务 '+this.continuousId+' · 通知 '+notification.notificationId+'。请下拉通知栏核对。');
    // Permission withdrawal may terminate the process. If it stays alive, stop within this check interval.
    this.permissionTimer = setInterval((): void => {
      try {
        if (access.checkAccessTokenSync(own.appInfo.accessTokenId, 'ohos.permission.APPROXIMATELY_LOCATION') !== abilityAccessCtrl.GrantStatus.PERMISSION_GRANTED) {
          this.revoke('大致位置权限已撤销，助手任务已停止');
        }
      } catch (_) { this.revoke('无法确认定位权限，助手任务已停止'); }
    }, 3000);
    } catch (e) {
      if (isCancelled() || (e as Error).message==='CANCELLED') throw new Error('CANCELLED');
      const code=(e as BusinessError).code;
      const message=(e as Error).message || '系统未提供详细原因';
      throw new Error(this.stage+'失败'+(code?'（'+code+'）':'')+'：'+message);
    }
  }

  async stop(): Promise<void> {
    this.closing = true;
    const failures: string[] = [];
    if (this.permissionTimer >= 0) { clearInterval(this.permissionTimer); this.permissionTimer = -1; }
    if (this.locationListening) {
      try { geoLocationManager.off('locationChange', this.discardLocation); } catch (_) { failures.push('location'); }
      this.locationListening = false;
    }
    if (this.switchListening) {
      try { geoLocationManager.off('locationEnabledChange', this.locationSwitch); } catch (_) { failures.push('switch'); }
      this.switchListening = false;
    }
    if (this.cancelListening) {
      try { backgroundTaskManager.off('continuousTaskCancel', this.cancelled); } catch (_) { failures.push('cancel'); }
      this.cancelListening = false;
    }
    if (this.suspendListening) {
      try { backgroundTaskManager.off('continuousTaskSuspend', this.suspended); } catch (_) { failures.push('suspend'); }
      this.suspendListening = false;
    }
    if (this.continuous) {
      try { await backgroundTaskManager.stopBackgroundRunning(this.context); } catch (_) { failures.push('background'); }
      this.continuous = false; this.continuousId = -1;
    }
    if (this.transientId >= 0) {
      try { backgroundTaskManager.cancelSuspendDelay(this.transientId); } catch (_) { /* May have expired. */ }
      this.transientId = -1;
    }
    if (failures.length > 0) throw new Error('后台资源清理未完成');
  }
}

/** Read back the OS state; never treat a visible switch as a running background task. */
export async function backgroundDiagnostics(context: common.UIAbilityContext): Promise<string> {
  const notifications=await notificationManager.isNotificationEnabled();
  const location=geoLocationManager.isLocationEnabled();
  const tasks=await backgroundTaskManager.getAllContinuousTasks(context);
  const active=tasks.filter((task:backgroundTaskManager.ContinuousTaskInfo):boolean=>!task.suspendState);
  return '通知权限：'+(notifications?'已允许':'未允许')+' · 系统定位：'+(location?'已开启':'已关闭')+'\n'+
    '运行中的持续任务：'+active.length+(active.length?'\n'+active.map((task:backgroundTaskManager.ContinuousTaskInfo):string=>'任务 '+task.continuousTaskId+' · 通知 '+task.notificationId+' · '+task.backgroundModes.join(', ')).join('\n'):'；当前没有系统持续任务');
}
export async function openTaskNotificationSettings(context: common.UIAbilityContext): Promise<void> {
  await notificationManager.openNotificationSettings(context);
}
