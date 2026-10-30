import { abilityAccessCtrl, bundleManager } from '@kit.AbilityKit';
import { display } from '@kit.ArkUI';
import { deviceInfo } from '@kit.BasicServicesKit';

export function hasVirtualScreenPermission():boolean {
  const own=bundleManager.getBundleInfoForSelfSync(bundleManager.BundleFlag.GET_BUNDLE_INFO_WITH_APPLICATION);
  return abilityAccessCtrl.createAtManager().checkAccessTokenSync(own.appInfo.accessTokenId,'ohos.permission.ACCESS_VIRTUAL_SCREEN')===abilityAccessCtrl.GrantStatus.PERMISSION_GRANTED;
}

/** The only screen destroyed here is the ID returned by this experiment. No app is moved or launched. */
export class VirtualScreenExperiment {
  private screenId:number|undefined;
  private busy=false;
  async createAndRelease(surfaceId:string,report:(message:string)=>void):Promise<void> {
    if(this.busy||this.screenId!==undefined)throw new Error('实验仍在运行或屏幕未释放，请先释放资源');
    if(!hasVirtualScreenPermission())throw new Error('尚未取得 ACCESS_VIRTUAL_SCREEN 的调试签名 ACL 授权');
    if(!surfaceId)throw new Error('测试画布尚未就绪');
    this.busy=true;
    try {
      const id=await display.createVirtualScreen({name:'HarmonyAgentPermissionProbe',width:320,height:180,density:1,surfaceId:surfaceId,supportsFocus:false});
      this.screenId=id;report('创建成功：虚拟屏 ID '+id+'，320 × 180。不抢占焦点，正在释放…');
      await this.release();report('创建与释放均成功。下一步仍需验证应用启动、画面读取和定向输入；当前尚不支持后台屏执行。');
    } finally {this.busy=false;if(this.screenId!==undefined)await this.release();}
  }
  async release():Promise<void> {
    const id=this.screenId;if(id===undefined)return;
    await display.destroyVirtualScreen(id);this.screenId=undefined;
  }
}

export async function virtualScreenPermissionReport():Promise<string> {
  const own=bundleManager.getBundleInfoForSelfSync(bundleManager.BundleFlag.GET_BUNDLE_INFO_WITH_APPLICATION);
  const manager=abilityAccessCtrl.createAtManager();
  const granted=manager.checkAccessTokenSync(own.appInfo.accessTokenId,'ohos.permission.ACCESS_VIRTUAL_SCREEN')===abilityAccessCtrl.GrantStatus.PERMISSION_GRANTED;
  const displays=await display.getAllDisplays();
  return '系统 API：'+deviceInfo.sdkApiVersion+'\n应用虚拟屏权限：'+(granted?'已取得，可点击下方按钮实测创建与释放':'未取得。需要在工程声明 ACCESS_VIRTUAL_SCREEN，并由调试 Profile 的 ACL 授权；普通弹窗不能授予。详见升级包中的“虚拟屏实验说明”，不要只改权限后沿用旧签名')+
    '\n当前可见显示：'+displays.map((item:display.Display):string=>item.id+' ('+item.width+'×'+item.height+')').join('、')+
    '\n本次只查询权限、显示列表及调试命令帮助，不移动 App、不注入输入。';
}
