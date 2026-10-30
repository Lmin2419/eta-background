import { KnownAppTools, KNOWN_APP_TOOLS } from '../core/workflows';
import { DataGate } from '../core/dataGate';
import { SettingsStore } from './SettingsStore';
import { PolicyWatch, policyKey, runtimeOptions, RuntimeOptions } from '../core/runPolicy';
import { parseSettings } from '../core/settings';
import { SystemTools, SYSTEM_TOOLS, APP_TOOLS } from '../core/systemTools';
import { HarmonySystemPort } from './SystemPort';
import { common, Want } from '@kit.AbilityKit';
import { util } from '@kit.ArkTS';
import { commonEventManager, deviceInfo } from '@kit.BasicServicesKit';
import { ConversationRepository, ConversationContext, redactSecrets, TOOL_LABELS } from '../core/conversations';
import { ConversationRun, RunJournal } from '../core/conversationRun';
import { TaskLedger, TaskScheduler, TaskRecord, ManagedTask, terminal } from '../core/runtime';
import { TaskNotifications } from '../core/notifications';
import { CapabilityRegistry } from '../core/capabilities';
import { DraftRepository } from '../core/drafts';
import { ProfileRepository, ModelProfile } from '../core/profiles';
import { ModelSettings, OpenAIClient, TOOLS } from '../core/model';
import { AgentLoop } from '../core/loop';
import { BackgroundService } from '../core/backgroundService';
import { TaskSession, AgentTask } from '../core/session';
import { RunEvent, DeviceTools, RunControl, ToolResult, ChatDevice, ContentPart } from '../core/types';
import { WorkspaceTools, ToolRouter } from '../core/workspace';
import { WorkspaceStore } from './WorkspaceStore';
import { WebTools } from '../core/webTools';
import { availableDeviceTools, auxiliaryTools, toolNames } from '../core/toolCatalog';
import { HarmonyDevice } from '../core/device';
import { NativeHdcTransport, HarmonyHttpTransport, screenSize } from './Services';
import { RuntimeDatabase, DatabaseDocumentStorage, DatabaseConversationStorage, DatabaseProfileStorage } from './RuntimeStorage';
import { FileProfileStorage, AssetSecretStorage } from './ProfileStore';
import { HarmonyNotifications } from './HarmonyNotifications';
import { HarmonyBackground } from './Background';

import { ExtensionRepository, SkillTools, SKILL_TOOLS } from '../core/extensions';
import { McpClient } from '../core/mcp';
import { HarmonyRpcTransport } from './McpTransport';
import { SubtaskTools, SUBTASK_TOOLS } from '../core/subtasks';
import { BrowserHost } from './BrowserHost';
import { BROWSER_TOOLS } from '../core/browser';
import { BackupDocument, prepareBackup, validateBackup } from '../core/backup';

export interface LiveTask {id:string;reply:string;reasoning:string;phase:string}
/** Application-owned runtime. Pages subscribe; disposing a page never disposes a task. */
export class AppRuntime {
  private static instance:AppRuntime|undefined;
  static get(context:common.UIAbilityContext):AppRuntime{if(!AppRuntime.instance)AppRuntime.instance=new AppRuntime(context);else AppRuntime.instance.context=context;return AppRuntime.instance;}
  readonly dataGate:DataGate=new DataGate();
  readonly db:RuntimeDatabase;
  readonly extensions:ExtensionRepository;
  maintenance=false;
  preferencesStore:SettingsStore|undefined;
  private policyWatch:PolicyWatch=new PolicyWatch();
  private options:RuntimeOptions=runtimeOptions({});
  contextTokens=32000;
  readonly chats:ConversationRepository;
  readonly profiles:ProfileRepository;
  readonly ledger:TaskLedger;
  readonly scheduler:TaskScheduler;
  readonly drafts:DraftRepository;
  readonly capabilities:CapabilityRegistry=new CapabilityRegistry();
  readonly hdc:NativeHdcTransport=new NativeHdcTransport();
  readonly device:HarmonyDevice;
  readonly workspace:WorkspaceStore;
  readonly workspaceTools:WorkspaceTools;
  useWorkspace=true;
  useWeb=false;
  useSystem=false;
  searchUrl='';
  readonly background:BackgroundService;
  readonly noticeDriver:HarmonyNotifications;
  readonly notifications:TaskNotifications;
  private loading:Promise<void>|undefined;
  private subscribers:Set<()=>void>=new Set();
  private settings:Map<string,ModelSettings>=new Map();
  private live:Map<string,LiveTask>=new Map();
  private leases:Map<string,HarmonyBackground>=new Map();
  private observer:commonEventManager.CommonEventSubscriber|undefined;
  private publishTimer:number=-1;
  private geometry='';
  private initialized=false;
  private chatsLoaded=false;
  private screenMonitorReady=false;
  private executingDeviceId='';
  foreground=true;
  continuousBackground=false;
  deviceScreenshots=true;
  warning='';
  backgroundDetail='';
  routeConversationId='';
  routeFocusInput=false;
  private context:common.UIAbilityContext;
  constructor(context:common.UIAbilityContext){
    this.context=context;
    const db=new RuntimeDatabase(context);this.db=db;this.extensions=new ExtensionRepository(new DatabaseDocumentStorage(db,'extensions'),()=>{this.policyWatch.refresh((id:string)=>policyKey(this.extensions.document,id,this.options));this.signal();},this.dataGate);
    this.workspace=new WorkspaceStore(context.filesDir);this.workspaceTools=new WorkspaceTools(this.workspace);
    this.profiles=new ProfileRepository(new DatabaseProfileStorage(db,context.filesDir),new AssetSecretStorage(),():string=>util.generateRandomUUID(),this.dataGate);
    this.chats=new ConversationRepository(new DatabaseConversationStorage(db,context.filesDir),():string=>util.generateRandomUUID(),():number=>Date.now(),():void=>this.signal(),2,this.dataGate);
    this.drafts=new DraftRepository(new DatabaseDocumentStorage(db,'drafts'));
    this.ledger=new TaskLedger(new DatabaseDocumentStorage(db,'tasks'),():string=>util.generateRandomUUID(),():void=>{this.signal();if(this.initialized)this.notifications.changed();});
    this.device=new HarmonyDevice(this.hdc,screenSize,():boolean=>this.deviceScreenshots);
    this.background=new BackgroundService(():HarmonyBackground=>new HarmonyBackground(this.context,true,(message:string):void=>{this.backgroundDetail=message;this.signal();}),():void=>this.signal());
    this.noticeDriver=new HarmonyNotifications(():common.UIAbilityContext=>this.context);
    this.notifications=new TaskNotifications(this.ledger,this.noticeDriver,(message:string):void=>{this.warning=message;this.signal();});
    this.scheduler=new TaskScheduler(this.ledger,(record:TaskRecord):ManagedTask=>this.worker(record),(message:string):void=>{this.warning=message;this.signal();});
  }
  initialize():Promise<void>{if(!this.loading)this.loading=this.load().catch((e:Error):never=>{this.loading=undefined;throw e as Error;});return this.loading;}
  private async load():Promise<void>{
    if(this.initialized)return;
    this.preferencesStore=await SettingsStore.open(this.context,this.db);this.configureTools(runtimeOptions(this.preferencesStore.snapshot()));
    await this.profiles.load();await this.extensions.load();const root=await this.db.read('workspace_root');if(root){if(!root.startsWith(this.context.filesDir+'/workspace-restore-'))throw new Error('工作区根路径无效');this.workspace.useRoot(root);}
    if(!this.chatsLoaded){await this.chats.load();this.chatsLoaded=true;}await this.ledger.load();
    try{await this.drafts.load();}catch(_){this.warning='草稿读取失败，原草稿仍保留；聊天记录已载入';}
    if(!this.chats.selectedId)await this.chats.create(this.profiles.document.selectedId);
    this.notifications.initialize();this.initialized=true;
    this.capabilities.set('models','模型网络',deviceInfo.sdkApiVersion>=23?'available':'unsupported','需要 API 23 以上；具体服务通过连接测试核验');
    this.capabilities.set('gui','主屏操作','disconnected','请连接本机无线调试','authorized HDC');
    this.capabilities.set('virtual','后台虚拟屏','unverified','尚未验证三方 App 的定向截图与输入闭环');
    this.capabilities.set('liveview','实况窗','unverified','需确认场景权益与真实创建结果');
    this.capabilities.set('floating','系统悬浮入口','unverified','需要对应签名 ACL 与用户授权');
    this.capabilities.set('power','长按电源键','unsupported','普通应用替换小艺的入口尚未验证；可从通知或桌面打开');
    this.capabilities.set('background','持续后台','unverified','没有通用 Agent 长时资格证明；短时额度与定位实验分开显示');
    try{
      this.observer=await commonEventManager.createSubscriber({events:[commonEventManager.Support.COMMON_EVENT_SCREEN_OFF,commonEventManager.Support.COMMON_EVENT_SCREEN_ON,'com.example.harmonyagent.TASK_NOTICE_REMOVED']});
      commonEventManager.subscribe(this.observer,(error:Error,data:commonEventManager.CommonEventData):void=>{
        if(error||!data)return;
        if(data.event===commonEventManager.Support.COMMON_EVENT_SCREEN_OFF){this.noticeDriver.screenOn=false;for(const t of this.activeTasks())void this.scheduler.pause(t.id,'屏幕已关闭，亮屏后需手动继续').catch(()=>{});}
        if(data.event===commonEventManager.Support.COMMON_EVENT_SCREEN_ON)this.noticeDriver.screenOn=true;
        if(data.event==='com.example.harmonyagent.TASK_NOTICE_REMOVED'){
          const id=data.parameters?.notificationId as number|undefined,token=data.parameters?.taskToken as string|undefined;
          if(typeof id==='number'&&typeof token==='string')void this.notifications.dismissed(id,token).catch(()=>{});
        }
      });
      this.screenMonitorReady=true;
    }catch(_){this.warning='系统状态事件订阅未成功；请在能力诊断中核对，手机任务仍需手动接管';}
    this.signal();
  }
  subscribe(listener:()=>void):()=>void{this.subscribers.add(listener);return ():void=>{this.subscribers.delete(listener);};}
  private signal():void{
    if(this.publishTimer>=0)return;
    this.publishTimer=setTimeout(():void=>{this.publishTimer=-1;for(const f of this.subscribers){try{f();}catch(_){}}},60);
  }
  activeTasks():TaskRecord[]{return this.ledger.list().filter(t=>!terminal(t.state)&&t.state!=='queued');}
  currentTask(conversationId:string):TaskRecord|undefined{return this.ledger.list().find(t=>t.conversationId===conversationId&&!terminal(t.state)&&t.state!=='queued');}
  liveTask(id:string):LiveTask|undefined{const value=this.live.get(id);return value?{id:value.id,reply:value.reply,reasoning:value.reasoning,phase:value.phase}:undefined;}
  get deviceBusy():boolean{return this.activeTasks().some(t=>t.mode==='device');}
  async connect(target:string):Promise<string>{
    if(this.deviceBusy)throw new Error('请先停止手机任务再修改连接');
    await this.hdc.start(this.context);
    try{const message=await this.device.connect(target);this.capabilities.set('gui','主屏操作','available',message,'authorized HDC');this.signal();return message;}
    catch(e){this.capabilities.set('gui','主屏操作','disconnected',(e as Error).message,'authorized HDC');throw e as Error;}
  }
  async submit(conversationId:string,text:string,settings:ModelSettings,profileId:string,mode:'chat'|'device',sourceRunId='',attachments:string[]=[],revisionOf=''):Promise<TaskRecord>{
    await this.initialize();if(this.maintenance)throw new Error('正在备份或恢复数据，请稍后');
    if(!this.chats.get(conversationId))throw new Error('会话已不存在，请新建或选择会话');
    if(!settings.model||!settings.baseUrl)throw new Error('请先配置并选择模型');
    if(mode==='device'&&!this.screenMonitorReady)throw new Error('屏幕状态监听未就绪，请重新打开应用后再操作手机');
    if(mode==='device'&&!this.device.isConnected)throw new Error('请先连接本机无线调试');
    this.profiles.rememberSessionKey(profileId,settings.baseUrl,settings.apiKey);
    const clean=redactSecrets(text,[settings.apiKey]);
    if(sourceRunId){const priorId=this.ledger.get(sourceRunId)?.continuationId;const prior=priorId?this.ledger.get(priorId):undefined;if(prior)return prior;}
    const task=await this.ledger.admit({conversationId,text:clean,modelLabel:settings.model,profileId,mode,sourceRunId,attachments,revisionOf});
    if(task.state==='queued')this.settings.set(task.id,{baseUrl:settings.baseUrl,model:settings.model,apiKey:settings.apiKey,screenshots:settings.screenshots,allowInsecureHttp:settings.allowInsecureHttp,streaming:settings.streaming,protocol:settings.protocol,maxOutputTokens:settings.maxOutputTokens,reasoningEffort:settings.reasoningEffort,extraHeaders:settings.extraHeaders});
    this.drafts.setMessage(conversationId,'');this.drafts.setAttachments(conversationId,[]);try{await this.drafts.flush();}catch(_){this.warning='任务已接纳，但输入草稿清理失败';}
    if(sourceRunId)await this.notifications.clearContinued(sourceRunId).catch(()=>{});
    this.scheduler.kick();return task;
  }
  private async resolveSettings(record:TaskRecord):Promise<ModelSettings>{
    const pending=this.settings.get(record.id);if(pending)return pending;
    const profile=this.profiles.document.profiles.find((p:ModelProfile):boolean=>p.id===record.profileId);
    if(!profile)throw new Error('原模型配置不可用，请打开会话重新选择');
    return {baseUrl:profile.baseUrl,model:record.modelLabel,apiKey:await this.profiles.key(profile),screenshots:profile.screenshots,allowInsecureHttp:profile.allowInsecureHttp,streaming:profile.streaming,protocol:profile.protocol,maxOutputTokens:profile.maxOutputTokens,reasoningEffort:profile.reasoningEffort,extraHeaders:profile.extraHeaders};
  }
  private deviceTools():Array<Record<string,Object>>{return availableDeviceTools(this.device);}
  private worker(record:TaskRecord):ManagedTask{
    let loop:AgentLoop|undefined,run:ConversationRun|undefined,session:TaskSession|undefined;
    const web=new WebTools(new HarmonyHttpTransport());web.searchUrl=this.searchUrl;
    const setupControl=new RunControl(),remotes:McpClient[]=[];let subtask:SubtaskTools|undefined;
    let stopped=false,paused=false,reason='';
    const stop=(message:string):void=>{stopped=true;reason=message;setupControl.stop();for(const remote of remotes)remote.cancel();subtask?.cancel();run?.stop(message);session?.stop(message);loop?.stop();web.cancel();if(record.mode==='device')this.hdc.cancel();};
    return {
      stop,
      pause:(message:string):void=>{paused=true;reason=message;setupControl.pause();loop?.pause();},
      resume:async():Promise<void>=>{
        if(stopped)throw new Error('任务已停止');
        if(record.mode==='device'){
          if(!this.device.isConnected)throw new Error('调试连接已断开，请停止并重新连接');
          if(!await this.noticeDriver.canAlert())throw new Error('请先亮屏并解锁手机');
          await this.context.moveAbilityToBackground();
        }
        paused=false;setupControl.resume();loop?.resume();
      },
      steer:(text:string):void=>{if(!loop)throw new Error('任务仍在准备');loop.steer(text);},
      run:async():Promise<void>=>{
        const secrets:string[]=[];
        this.policyWatch.watch(record.id,record.conversationId,policyKey(this.extensions.document,record.conversationId,this.options),()=>{const message='执行配置已改变，旧任务已停止；请确认新配置后重新发送';stop(message);void this.scheduler.stop(record.id,message).catch(()=>{});});
        try{
        const settings=await this.resolveSettings(record);secrets.push(settings.apiKey);
        if(stopped)throw new Error('CANCELLED');
        if(record.mode==='chat'&&!this.foreground)await this.acquireChatLease(record.id);
        if(record.mode==='device'){
          if(!this.foreground&&!this.background.active)throw new Error('请返回助手，再启动需要后台授权的手机任务');
          this.deviceScreenshots=settings.screenshots;this.device.resetActionHistory();
          try{await this.noticeDriver.request();}catch(_){this.warning='通知未授权；可返回助手查看任务';}
        }
        const live:LiveTask={id:record.id,reply:'',reasoning:'',phase:'正在准备'};this.live.set(record.id,live);
        const emit=(event:RunEvent):void=>{
          const current=this.ledger.get(record.id);if(!current||terminal(current.state)||stopped)return;
          if(event.kind==='stream'){live.reply=redactSecrets(event.text,secrets);run?.capture(live.reply);}
          else if(event.kind==='reasoning'){live.reasoning=redactSecrets(event.text,secrets);run?.capture(live.reasoning,true);}
          else{
            live.phase=redactSecrets(event.toolName?(TOOL_LABELS[event.toolName]||event.toolName)+' · '+(event.kind==='tool'?'执行中':event.kind==='result'?'执行返回':'结果待处理'):event.text,secrets);
            if(['thinking','tool','result','error','supplement'].includes(event.kind))void this.ledger.update(record.id,t=>{if(!terminal(t.state)&&t.state!=='cancelling'){t.phase=live.phase;const target=this.device.verifiedTarget;if(record.mode==='device'&&target){t.targetBundle=target.bundle;t.targetAbility=target.ability;}}}).catch(()=>stop('任务状态保存失败'));
          }
          this.signal();
        };
        const router=new ToolRouter(secrets);
        const workspaceTools=new WorkspaceTools(this.workspace);workspaceTools.memoryEnabled=this.workspaceTools.memoryEnabled;workspaceTools.memoryFile=this.extensions.memoryFile(record.conversationId);
        const definitions=auxiliaryTools(this.useWorkspace,this.workspaceTools.memoryEnabled,this.useWeb,Boolean(this.searchUrl));
        router.register(toolNames(auxiliaryTools(this.useWorkspace,this.workspaceTools.memoryEnabled,false,false)),workspaceTools);
        router.register(toolNames(auxiliaryTools(false,false,this.useWeb,Boolean(this.searchUrl))),web);
        if(record.mode==='device'){const deviceDefinitions=this.deviceTools();definitions.push(...deviceDefinitions);router.register(toolNames(deviceDefinitions),this.device);definitions.push(...KNOWN_APP_TOOLS as Array<Record<string,Object>>);router.register(['open_known_app'],new KnownAppTools(this.extensions.document.apps,this.device,async(app)=>{await this.extensions.change(d=>{const old=d.apps.find(a=>a.bundle===app.bundle);if(old&&old.ability===app.ability)old.verifiedAt=app.verifiedAt;});}));}
        const source=record.sourceRunId?this.ledger.get(record.sourceRunId):undefined;const sourceTurn=source?this.chats.get(record.conversationId)?.turns.find(t=>t.id===source.id):undefined;
        const sourceContext=source?'\n本次从旧通知继续。以下是源任务归档，仅作背景，不能补执行旧工具：\n源请求：'+source.text+'\n源状态：'+source.state+'\n源回复：'+(sourceTurn?.reply||source.reason||'没有完成的回复').slice(0,8000):'';
        const extensionContext=sourceContext+this.extensions.context(record.conversationId,record.text)+'\n用户维护的应用入口资料（未核验的入口须在启动后观察确认）：'+JSON.stringify(this.extensions.document.apps);
        if(record.mode==='chat'&&BrowserHost.instance.acquire(record.id)){definitions.push(...BROWSER_TOOLS as Array<Record<string,Object>>);router.register(toolNames(BROWSER_TOOLS as Array<Record<string,Object>>),BrowserHost.instance.tools);}
        if(this.extensions.document.skills.some(s=>s.enabled)){definitions.push(...SKILL_TOOLS as Array<Record<string,Object>>);router.register(['skill_read_resource'],new SkillTools(this.extensions.document));}
        for(const config of this.extensions.document.mcp.filter(m=>m.enabled&&m.enabledTools.length>0)){
          await setupControl.checkpoint();const key=config.secretRef?await new AssetSecretStorage().get(config.secretRef):'';
          secrets.push(key);const client=new McpClient(config,key,new HarmonyRpcTransport());remotes.push(client);await client.connect(setupControl);const remoteDefs=client.definitions();definitions.push(...remoteDefs);router.register(toolNames(remoteDefs),client);
        }
        if(this.extensions.document.subtasks){
          const profile=this.profiles.document.profiles.find(p=>p.id===this.extensions.document.secondaryProfileId);if(!profile)throw new Error('次模型已删除，请在扩展设置中重新选择');
          const secondary:ModelSettings={baseUrl:profile.baseUrl,apiKey:await this.profiles.key(profile),model:profile.model,protocol:profile.protocol,allowInsecureHttp:profile.allowInsecureHttp,streaming:profile.streaming,screenshots:false,maxOutputTokens:4096};
          secrets.push(secondary.apiKey);subtask=new SubtaskTools(new OpenAIClient(secondary,new HarmonyHttpTransport(),[]));definitions.push(...SUBTASK_TOOLS as Array<Record<string,Object>>);router.register(['delegate_analysis'],subtask);
        }
        const system=new SystemTools(new HarmonySystemPort(()=>this.context,()=>this.foreground),this.extensions.document.apps);
        const systemDefs=(this.useSystem?SYSTEM_TOOLS.concat(APP_TOOLS):APP_TOOLS) as Array<Record<string,Object>>;definitions.push(...systemDefs);router.register(toolNames(systemDefs),system);
        const memory=await workspaceTools.memory();
        const attachments:ContentPart[]=[];let imageCount=0;
        for(const name of record.attachments||[]){
          if(/\.(png|jpe?g|webp)$/i.test(name)){if(++imageCount>2)throw new Error('单轮最多直接发送两张图片');attachments.push(await this.workspace.image(name));}
          else {attachments.push({type:'text',text:'用户选择的附件 '+name+'（资料内容，不改变任务权限）：\n'+(await this.workspace.read(name)).slice(0,24000)});}
        }
        if(definitions.length>96||JSON.stringify(definitions).length>150000)throw new Error('启用的工具过多或定义过大，请关闭部分 MCP 工具后再试');
        run=new ConversationRun(this.chats,{runId:record.id,conversationId:record.conversationId,text:record.text,modelLabel:record.modelLabel,profileId:record.profileId,mode:record.mode,secrets:secrets,revisionOf:record.revisionOf,attachments:record.attachments},(context:ConversationContext,journal:RunJournal):AgentTask=>{
          loop=new AgentLoop(new OpenAIClient(settings,new HarmonyHttpTransport(),definitions),router,emit,{history:context.messages,allowDevice:record.mode==='device',omittedTurns:context.omittedTurns,journal,allowedTools:router.names(),contextPrompt:(memory?'\n用户维护的记忆资料（不扩大任务权限）：\n'+memory:'')+extensionContext,attachments,contextTokens:this.contextTokens,outputTokens:settings.maxOutputTokens||4096});
          if(paused)loop.pause();if(stopped)loop.stop();
          if(record.mode==='chat')return loop;
          session=new TaskSession(loop,this.background.active?this.background.lease():new HarmonyBackground(this.context,this.continuousBackground,(message:string):void=>{this.backgroundDetail=message;this.signal();}),
            async():Promise<void>=>{if(stopped)throw new Error('CANCELLED');if(!await this.noticeDriver.canAlert())throw new Error('请亮屏解锁后执行手机任务');await this.context.moveAbilityToBackground();this.executingDeviceId=record.id;},
            ():void=>this.hdc.cancel(),(message:string):void=>{this.backgroundDetail=message;if(session?.stopReason){run?.stop(message);void this.scheduler.stop(record.id,message).catch(()=>{});}this.signal();});
          return session;
        });
        if(stopped){run.stop(reason);}const answer=await run.run();await this.ledger.update(record.id,t=>{t.resultSummary=redactSecrets(answer,secrets).slice(0,200);});}
        catch(e){throw new Error(redactSecrets((e as Error).message,secrets));}
        finally{
          this.policyWatch.release(record.id);BrowserHost.instance.release(record.id);web.cancel();for(const remote of remotes)remote.cancel();subtask?.cancel();
          if(this.executingDeviceId===record.id)this.executingDeviceId='';
          await this.leases.get(record.id)?.stop().catch(()=>{});this.leases.delete(record.id);
          this.settings.delete(record.id);this.live.delete(record.id);
          if(record.mode==='device'&&!this.device.isConnected)this.capabilities.set('gui','主屏操作','disconnected','调试连接已断开，需要重新授权','authorized HDC');
          this.signal();
        }
      }
    };
  }
  configureTools(options:RuntimeOptions):void{
    this.options=options;this.useWorkspace=options.useWorkspace;this.useWeb=options.useWeb;this.useSystem=options.useSystem;
    this.searchUrl=options.searchUrl;this.workspaceTools.memoryEnabled=options.memoryEnabled;this.contextTokens=options.contextTokens;
    this.continuousBackground=options.continuousBackground;this.notifications.alertsEnabled=options.completionAlerts;this.notifications.inlineReplyEnabled=options.inlineReply;
    this.policyWatch.refresh((id:string)=>policyKey(this.extensions.document,id,options));
  }
  capabilityHeader():string{return '系统 API：'+deviceInfo.sdkApiVersion+'；设备形态：'+deviceInfo.deviceType+'\n当前主屏执行位：'+(this.deviceBusy?'占用':'空闲')+'；活动任务：'+this.activeTasks().length+'\n后台状态：'+this.background.state+' · '+this.background.message;}
  refreshCapabilities():void{
    this.capabilities.set('gui','主屏操作',this.device.isConnected?'available':'disconnected',this.device.isConnected?'已通过本机 HDC 身份和 UITest 检查':'需要重新连接与授权',this.device.debugIdentity||'HDC 未连接');
    this.capabilities.set('workspace','文件工作区',this.useWorkspace?'available':'unsupported',this.useWorkspace?'仅限导入文件和应用沙箱':'用户关闭');
    this.capabilities.set('memory','长期记忆',this.workspaceTools.memoryEnabled?'available':'unsupported',this.workspaceTools.memoryEnabled?'按会话角色隔离，限制注入预算':'用户关闭');
    this.capabilities.set('web','网页工具',this.useWeb?'available':'unsupported',this.useWeb?'HTTPS 抓取；搜索需要配置 SearXNG':'用户关闭');
    this.capabilities.set('browser','助手内浏览器',BrowserHost.instance.ready()?'available':'disconnected','聊天页打开浏览器后才注册工具；无法控制外部浏览器');
    this.capabilities.set('mcp','远程 MCP','unverified','每次任务初始化并核验工具目录；只注册逐项开启的工具');
    this.capabilities.set('screen_events','屏幕事件',this.screenMonitorReady?'available':'unverified','已息屏则暂停；亮屏不会自动继续');
    this.capabilities.set('system','系统选择器',this.useSystem?'available':'unsupported',this.useSystem?'选择联系人、日历编辑及位置授权均需前台':'用户关闭');
  }
  private requireIdle():void{if(this.ledger.list().some(t=>!terminal(t.state)))throw new Error('请先结束任务中心的所有运行和排队任务');}
  clearRecoveryDirectories():number{this.requireIdle();if(this.maintenance)throw new Error('数据维护正在进行');return this.workspace.clearRecoveryDirectories(this.context.filesDir);}
  async backup():Promise<string>{this.requireIdle();if(this.maintenance)throw new Error('数据维护正在进行');const release=this.dataGate.maintain();this.maintenance=true;try{await this.drafts.flush();return JSON.stringify(prepareBackup(this.chats.snapshot(),this.profiles.document,this.extensions.document,await this.workspace.backupFiles(),parseSettings((await this.db.read('settings'))||'{}'),this.drafts.editors()));}finally{this.maintenance=false;release();}}
  async restoreBackup(raw:string):Promise<void>{
    this.requireIdle();if(this.maintenance)throw new Error('数据维护正在进行');const backup=validateBackup(raw);const release=this.dataGate.maintain();let committed=false,hydrated=false;this.maintenance=true;
    try{await this.drafts.flush();await this.notifications.flush();const old=this.ledger.list();const next=this.ledger.nextNotificationId;
      const root=WorkspaceStore.stage(this.context.filesDir,backup.files);await this.db.restore(backup,root,next);committed=true;
      this.workspace.useRoot(root);this.ledger.acceptEmptyCommitted(next);this.chats.acceptCommitted(JSON.stringify(backup.conversations));this.profiles.clearSessionKeys();await this.profiles.load();await this.extensions.load();await this.drafts.load();this.preferencesStore=await SettingsStore.open(this.context,this.db);this.configureTools(runtimeOptions(this.preferencesStore.snapshot()));
      for(const t of old)await this.noticeDriver.cancel(t.notificationId).catch(()=>{});
      hydrated=true;this.warning='备份已恢复。模型与 MCP 密钥未导入，请重新填写；原工作区文件保留在旧目录。';this.signal();
    }catch(e){if(committed&&!hydrated)this.warning='备份已提交，但内存状态加载失败，请完全退出并重新打开助手；为防止混用新旧数据，已暂停数据操作';throw e as Error;}finally{this.maintenance=committed&&!hydrated;if(!this.maintenance)release();}
  }
  onForeground():void{this.foreground=true;BrowserHost.instance.setForeground(true);for(const id of this.leases.keys()){const lease=this.leases.get(id);this.leases.delete(id);void lease?.stop().catch(()=>{this.warning='短时后台资源释放失败，请重新打开助手';this.signal();});}if(this.executingDeviceId)void this.scheduler.pause(this.executingDeviceId,'已返回助手，手机操作暂停').catch(()=>{});}
  private async acquireChatLease(id:string):Promise<void>{
    if(this.foreground||this.leases.has(id)||terminal(this.ledger.get(id)?.state||'interrupted'))return;
    const lease=new HarmonyBackground(this.context,false);this.leases.set(id,lease);
    try{await lease.start((reason:string):void=>{if(!this.foreground&&this.leases.get(id)===lease)void this.scheduler.stop(id,reason).catch(()=>{});},():boolean=>this.foreground||this.leases.get(id)!==lease||terminal(this.ledger.get(id)?.state||'interrupted'));}
    catch(e){const applies=!this.foreground&&this.leases.get(id)===lease;await lease.stop().catch(()=>{});if(this.leases.get(id)===lease)this.leases.delete(id);if(applies)throw e as Error;}
  }
  onBackground():void{
    this.foreground=false;BrowserHost.instance.setForeground(false);if(BrowserHost.instance.owner)void this.scheduler.pause(BrowserHost.instance.owner,'网页任务需要助手保持前台；返回后手动继续').catch(()=>{});
    for(const r of this.activeTasks())if(r.mode==='chat')void this.acquireChatLease(r.id).catch(()=>{void this.scheduler.stop(r.id,'后台额度不可用，请回到助手继续').catch(()=>{});});
  }
  geometryChanged(width:number,height:number,rotation:number):void{
    const value=width+'x'+height+'@'+rotation;if(this.geometry&&this.geometry!==value){this.device.invalidate();if(this.executingDeviceId)void this.scheduler.pause(this.executingDeviceId,'屏幕尺寸或旋转变化，请确认后继续').catch(()=>{});}this.geometry=value;
  }
  async route(want:Want):Promise<void>{
    await this.initialize();
    const id=want.parameters?.runId,token=want.parameters?.taskToken,action=want.parameters?.taskAction;
    if(typeof id!=='string'||typeof token!=='string'||typeof action!=='string')return;
    const task=this.ledger.validAction(id,token);if(!task)return;if(!this.chats.get(task.conversationId))throw new Error('此通知对应的会话已删除，请选择其他会话');
    this.routeConversationId=task.conversationId;this.routeFocusInput=action==='continue';
    if(action==='stop')await this.scheduler.stop(id);
    else if(action==='pause')await this.scheduler.pause(id);
    else if(action==='resume')await this.scheduler.resume(id);
    else if(action==='open-app'){
      if(task.targetBundle&&task.targetAbility)await this.context.startAbility({bundleName:task.targetBundle,abilityName:task.targetAbility});
    }else if(action==='continue'){
      const input=want.parameters?.['harmony.reply'];
      if(typeof input==='string'&&input.trim()){
        this.drafts.setMessage(task.conversationId,input.slice(0,8000));await this.drafts.flush();
        if(input.length>8000)throw new Error('回复过长，已保留前 8000 字符供编辑');
        const settings=await this.resolveSettings(task);await this.submit(task.conversationId,input,settings,task.profileId,task.mode,task.id);
      }
    }
    this.signal();
  }
}
