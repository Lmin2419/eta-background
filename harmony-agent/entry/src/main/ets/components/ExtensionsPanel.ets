import { importAppCatalog, exportAppCatalog } from '../core/workflows';
import { RequestEpoch } from '../core/requestEpoch';
import { rolePng } from '../core/characterPng';
import { tone } from '../core/theme';
import { common } from '@kit.AbilityKit';
import { util } from '@kit.ArkTS';
import { AppRuntime } from '../platform/AppRuntime';
import { Skill, RoleCard, McpConfig, KnownApp } from '../core/extensions';
import { ModelProfile } from '../core/profiles';
import { McpClient, RemoteTool } from '../core/mcp';
import { HarmonyRpcTransport } from '../platform/McpTransport';
import { AssetSecretStorage } from '../platform/ProfileStore';
import { chooseData, saveData, saveBinary } from '../platform/DataFiles';
import { validateBackup } from '../core/backup';
import { RunControl } from '../core/types';

@Component
export struct ExtensionsPanel {
 @StorageLink('darkTheme') private darkTheme:boolean=false;
 private color(value:string):string{return tone(value,this.darkTheme);}
 @Prop conversationId:string='';
 onRestored:()=>void=()=>{};
 @State private skills:Skill[]=[];
 @State private roles:RoleCard[]=[];
 @State private mcps:McpConfig[]=[];
 @State private profiles:ModelProfile[]=[];
 @State private apps:KnownApp[]=[];
 @State private roleId:string='';
 @State private secondaryId:string='';
 @State private subtasks:boolean=false;
 @State private busy:boolean=false;
 @State private status:string='';
 @State private section:string='skills';
 @State private draft:string='';
 @State private mcpId:string='';
 @State private mcpName:string='';
 @State private mcpAddress:string='';
 @State private mcpKey:string='';
 @State private remoteTools:RemoteTool[]=[];
 @State private selectedTools:string[]=[];
 @State private appName:string='';
 @State private appBundle:string='';
 @State private appAbility:string='EntryAbility';
 private mcpEpoch:RequestEpoch=new RequestEpoch();
 private probe:McpClient|undefined;
 private control:RunControl|undefined;
 private pendingRestore:string='';
 private host():AppRuntime{return AppRuntime.get(this.getUIContext().getHostContext() as common.UIAbilityContext);}
 private context():common.UIAbilityContext{return this.getUIContext().getHostContext() as common.UIAbilityContext;}
 aboutToAppear():void{this.refresh();}
 aboutToDisappear():void{this.invalidateMcp();this.mcpKey='';this.pendingRestore='';}
 private refresh():void{const h=this.host(),d=h.extensions.document;this.skills=d.skills.slice();this.roles=d.roles.slice();this.mcps=d.mcp.slice();this.apps=d.apps.slice();this.roleId=d.roleSelections[this.conversationId]||'';this.secondaryId=d.secondaryProfileId;this.subtasks=d.subtasks;this.profiles=h.profiles.document.profiles.slice();}
 private async action(kind:string,id:string=''):Promise<void>{
  if(this.busy)return;this.busy=true;let release:()=>void=()=>{};
  try{const h=this.host();if(!['backup','restore','restore-check','cleanup'].includes(kind))release=h.dataGate.enter();if(h.maintenance)throw new Error('数据维护正在进行');
   if(kind==='skill-import'||kind==='role-import'){const raw=await chooseData(this.context(),4_000_000,kind==='role-import');if(!raw)return;if(kind==='skill-import')await h.extensions.installSkill(raw);else await h.extensions.installRole(raw);this.status='已导入；技能默认关闭，请检查后启用';}
   else if(kind==='skill-save')await h.extensions.installSkill(this.draft,true);
   else if(kind==='role-save')await h.extensions.installRole(this.draft,true);
   else if(kind==='skill-toggle')await h.extensions.change(d=>{const s=d.skills.find(s=>s.id===id);if(s)s.enabled=!s.enabled;});
   else if(kind==='skill-remove')await h.extensions.change(d=>{d.skills=d.skills.filter(s=>s.id!==id);});
   else if(kind==='role-remove')await h.extensions.change(d=>{d.roles=d.roles.filter(r=>r.id!==id);for(const key of Object.keys(d.roleSelections))if(d.roleSelections[key]===id)d.roleSelections[key]='';});
   else if(kind==='role-select'){let target=this.conversationId;const existing=h.chats.get(target);if((h.extensions.document.roleSelections[target]||'')!==id&&existing?.turns.length){target=await h.chats.create(existing.profileId,'chat');this.status='已创建独立角色会话，旧角色历史与记忆保持分离';}const selected=target;await h.extensions.change(d=>{d.roleSelections[selected]=id;});}
   else if(kind==='role-png'){const role=h.extensions.document.roles.find(r=>r.id===id);if(role)await saveBinary(this.context(),role.name+'.png',rolePng(new util.Base64Helper().encodeToStringSync(new util.TextEncoder().encodeInto(JSON.stringify({spec:'chara_card_v2',spec_version:'2.0',data:role})))));}
   else if(kind==='skill-export'){const skill=h.extensions.document.skills.find(r=>r.id===id);if(skill)await saveData(this.context(),skill.id+'.skill.json',JSON.stringify(skill,null,2));}
   else if(kind==='role-export'){const role=h.extensions.document.roles.find(r=>r.id===id);if(role)await saveData(this.context(),role.name+'.json',JSON.stringify(role,null,2));}
   else if(kind==='secondary')await h.extensions.change(d=>{d.secondaryProfileId=this.secondaryId;d.subtasks=this.subtasks;});
   else if(kind==='mcp-save')await this.saveMcp();
   else if(kind==='mcp-toggle')await h.extensions.change(d=>{const m=d.mcp.find(m=>m.id===id);if(m)m.enabled=!m.enabled;});
   else if(kind==='mcp-remove'){const old=h.extensions.document.mcp.find(m=>m.id===id);await h.extensions.change(d=>{d.mcp=d.mcp.filter(m=>m.id!==id);});if(old?.secretRef)await new AssetSecretStorage().remove(old.secretRef);}
   else if(kind==='app-import'){const raw=await chooseData(this.context(),100000);if(!raw)return;const apps=importAppCatalog(raw);await h.extensions.change(d=>{for(const app of apps){const old=d.apps.find(a=>a.bundle===app.bundle);if(old&&old.ability!==app.ability)throw new Error('目录中已有不同入口：'+app.name+'；请先检查原条目');if(!old)d.apps.push(app);}});this.status='已导入目录；导入信息不能证明应用已安装';}
   else if(kind==='app-export'){await saveData(this.context(),'harmony-apps.json',exportAppCatalog(h.extensions.document.apps));}
   else if(kind==='app-observed'){const target=h.device.verifiedTarget;if(!target)throw new Error('当前没有通过启动和页面观察确认的应用入口，请先执行应用打开任务');this.appBundle=target.bundle;this.appAbility=target.ability;this.appName=this.appName||target.bundle;this.status='已填入观察确认的入口，请核对应用名并保存';}
   else if(kind==='app-save')await h.extensions.change(d=>{d.apps=d.apps.filter(a=>a.bundle!==this.appBundle).concat([{name:this.appName,bundle:this.appBundle,ability:this.appAbility,verifiedAt:0}]);});
   else if(kind==='app-remove')await h.extensions.change(d=>{d.apps=d.apps.filter(a=>a.bundle!==id);});
   else if(kind==='cleanup'){this.status='已清理 '+h.clearRecoveryDirectories()+' 个未使用的恢复目录；当前工作区未修改';}
   else if(kind==='backup'){const raw=await h.backup();this.status=await saveData(this.context(),'harmony-agent-backup.json',raw)?'备份已导出（不含模型/MCP 密钥，不含运行授权）':'已取消';}
   else if(kind==='restore-check'){const raw=await chooseData(this.context(),48_000_000);if(!raw)return;const b=validateBackup(raw);this.pendingRestore=raw;this.getUIContext().showAlertDialog({title:'恢复已校验的备份',message:b.conversations.conversations.length+' 个会话、'+b.profiles.profiles.length+' 个服务、'+b.files.length+' 个文件。将替换当前记录；密钥需重新填写。请先导出当前备份。',primaryButton:{value:'取消',action:()=>{this.pendingRestore='';}},secondaryButton:{value:'恢复',action:()=>{this.action('restore');}}});}
   else if(kind==='restore'){if(!this.pendingRestore)throw new Error('请先选择备份');await h.restoreBackup(this.pendingRestore);this.pendingRestore='';this.status=h.warning;this.onRestored();}
   this.refresh();
  }catch(e){this.status=(e as Error).message;}finally{release();this.busy=false;}
 }
 private invalidateMcp():number{const generation=this.mcpEpoch.next();this.control?.stop();this.probe?.cancel();return generation;}
 private async testMcp():Promise<void>{
  if(this.busy)return;const generation=this.invalidateMcp();this.busy=true;this.remoteTools=[];this.control=new RunControl();
  const config:McpConfig={id:this.mcpId||'preview',name:this.mcpName||'MCP',url:this.mcpAddress,enabled:false,enabledTools:[]};
  let client:McpClient|undefined;
  try{client=new McpClient(config,this.mcpKey,new HarmonyRpcTransport());this.probe=client;const tools=await client.connect(this.control);this.mcpEpoch.require(generation);this.remoteTools=tools;this.status='已读取 '+tools.length+' 个工具；勾选后保存。此测试没有执行工具';}
  catch(e){if(this.mcpEpoch.current(generation))this.status=(e as Error).message;}
  finally{client?.cancel();if(this.mcpEpoch.current(generation)){this.probe=undefined;this.busy=false;}}
 }
 private async editMcp(config:McpConfig):Promise<void>{
  if(this.busy)return;const generation=this.invalidateMcp();this.mcpId=config.id;this.mcpName=config.name;this.mcpAddress=config.url;this.selectedTools=config.enabledTools.slice();this.remoteTools=[];this.mcpKey='';
  try{if(config.secretRef){const key=await new AssetSecretStorage().get(config.secretRef);if(this.mcpEpoch.current(generation))this.mcpKey=key;}}
  catch(e){if(this.mcpEpoch.current(generation))this.status=(e as Error).message;}
 }
 private async saveMcp():Promise<void>{
  const secrets=new AssetSecretStorage(),h=this.host(),old=h.extensions.document.mcp.find(m=>m.id===this.mcpId),generation=this.mcpEpoch.next();let alias='';let committed=false;
  const value:McpConfig={id:this.mcpId||util.generateRandomUUID(),name:this.mcpName,url:this.mcpAddress,enabledTools:this.selectedTools.slice(),enabled:old?.enabled||false};
  try{if(this.mcpKey){alias='mcp-key-'+util.generateRandomUUID();await secrets.put(alias,this.mcpKey);}
    this.mcpEpoch.require(generation);value.secretRef=alias||undefined;
    await h.extensions.change(d=>{d.mcp=d.mcp.filter(m=>m.id!==value.id).concat([value]);});committed=true;this.mcpId=value.id;if(old?.secretRef)await secrets.remove(old.secretRef);this.status='MCP 配置已保存，按服务开关启用';
  }finally{if(alias&&!committed)await secrets.remove(alias).catch(()=>{});}
 }
 build(){
  Column({space:12}){
   Text('扩展与数据').fontSize(22).fontWeight(FontWeight.Bold).width('100%')
   Select([{value:'技能与角色'},{value:'远程 MCP'},{value:'次模型与应用'},{value:'备份恢复'}]).value(this.section).onSelect((i:number)=>{this.section=['skills','mcp','secondary','backup'][i];this.draft='';})
   if(this.section==='skills'){
    Row({space:8}){Button('导入 Skill JSON').onClick(()=>this.action('skill-import'));Button('导入角色 JSON/PNG').onClick(()=>this.action('role-import'))}.enabled(!this.busy)
    Text('技能资源以 JSON 包保存，默认关闭；脚本资源只能读取，不会自动执行。角色与世界书按当前会话选择。').fontSize(12).width('100%')
    ForEach(this.skills,(s:Skill)=>{Column({space:6}){Row(){Text(s.name+' @'+s.version).layoutWeight(1);Toggle({type:ToggleType.Switch,isOn:s.enabled}).onChange(()=>this.action('skill-toggle',s.id))};Row({space:8}){Button('编辑').onClick(()=>this.draft=JSON.stringify(s,null,2));Button('导出').onClick(()=>this.action('skill-export',s.id));Button('卸载').onClick(()=>this.action('skill-remove',s.id))}}},(s:Skill)=>s.id)
    Button('当前会话：普通助手').onClick(()=>this.action('role-select',''))
    ForEach(this.roles,(r:RoleCard)=>{Column(){Text((this.roleId===r.id?'✓ ':'')+r.name).width('100%');Flex({wrap:FlexWrap.Wrap}){Button('选择').onClick(()=>this.action('role-select',r.id));Button('编辑').onClick(()=>this.draft=JSON.stringify(r,null,2));Button('JSON').onClick(()=>this.action('role-export',r.id));Button('PNG').onClick(()=>this.action('role-png',r.id));Button('删除').onClick(()=>this.action('role-remove',r.id))}}},(r:RoleCard)=>r.id)
    TextArea({placeholder:'Skill 或角色 JSON；可编辑后保存。',text:this.draft}).height(160).onChange((v:string)=>this.draft=v)
    Row({space:8}){Button('保存 / 替换 Skill').onClick(()=>this.action('skill-save'));Button('保存 / 替换角色').onClick(()=>this.action('role-save'))}.enabled(!this.busy&&Boolean(this.draft))
   }else if(this.section==='mcp'){
    Text('支持 Streamable HTTP。工具逐项开启；取消或断连后不自动重放。').fontSize(12).width('100%')
    ForEach(this.mcps,(m:McpConfig)=>{Row({space:8}){Text(m.name).layoutWeight(1).onClick(()=>this.editMcp(m));Toggle({type:ToggleType.Switch,isOn:m.enabled}).onChange(()=>this.action('mcp-toggle',m.id));Button('删除').onClick(()=>this.action('mcp-remove',m.id))}},(m:McpConfig)=>m.id)
    Button('新建服务').enabled(!this.busy).onClick(()=>{this.invalidateMcp();this.mcpId='';this.mcpName='';this.mcpAddress='';this.mcpKey='';this.selectedTools=[];this.remoteTools=[];})
    TextInput({placeholder:'服务名称',text:this.mcpName}).enabled(!this.busy).onChange((v:string)=>this.mcpName=v)
    TextInput({placeholder:'https://example.com/mcp',text:this.mcpAddress}).enabled(!this.busy).onChange((v:string)=>{if(v!==this.mcpAddress){this.invalidateMcp();this.mcpAddress=v;this.mcpKey='';this.remoteTools=[];this.selectedTools=[];}})
    TextInput({placeholder:'Bearer token（存入本机安全存储）',text:this.mcpKey}).type(InputType.Password).enabled(!this.busy).onChange((v:string)=>{if(v!==this.mcpKey){this.invalidateMcp();this.mcpKey=v;this.remoteTools=[];}})
    Button(this.busy?'连接中…':'连接并读取工具目录').enabled(!this.busy).onClick(()=>this.testMcp())
    ForEach(this.remoteTools,(t:RemoteTool)=>{Row(){Text(t.name+' · '+t.description.slice(0,80)).fontSize(12).layoutWeight(1);Checkbox({name:t.name}).select(this.selectedTools.includes(t.name)).onChange((v:boolean)=>this.selectedTools=v?Array.from(new Set(this.selectedTools.concat([t.name]))):this.selectedTools.filter(n=>n!==t.name))}},(t:RemoteTool)=>t.name)
    Button('保存 MCP').enabled(!this.busy).onClick(()=>this.action('mcp-save'))
   }else if(this.section==='secondary'){
    Text('子模型只处理文本分析，单次主任务最多 4 次，不获得手机或文件权限。').fontSize(12)
    Select(this.profiles.map((p:ModelProfile):SelectOption=>({value:p.name+' · '+p.model}))).value(this.profiles.find((p:ModelProfile):boolean=>p.id===this.secondaryId)?.name||'选择次模型').onSelect((i:number)=>this.secondaryId=this.profiles[i]?.id||'')
    Row(){Text('启用次模型分析').layoutWeight(1);Toggle({type:ToggleType.Switch,isOn:this.subtasks}).onChange((v:boolean)=>this.subtasks=v)}
    Button('保存次模型选择').onClick(()=>this.action('secondary'))
    Text('常用应用入口（需要已知 bundle / ability；不声称枚举全部应用）').fontSize(12)
    Flex({wrap:FlexWrap.Wrap}){Button('导入目录').onClick(()=>this.action('app-import'));Button('导出目录').onClick(()=>this.action('app-export'));Button('填入最近确认的入口').onClick(()=>this.action('app-observed'))}.enabled(!this.busy)
    ForEach(this.apps,(a:KnownApp)=>{Row(){Text(a.name+' · '+a.bundle+(a.verifiedAt?' · 曾观察到':' · 未核验')).fontSize(12).layoutWeight(1);Button('删除').onClick(()=>this.action('app-remove',a.bundle))}},(a:KnownApp)=>a.bundle)
    TextInput({placeholder:'应用名',text:this.appName}).onChange((v:string)=>this.appName=v)
    TextInput({placeholder:'com.example.app',text:this.appBundle}).onChange((v:string)=>this.appBundle=v)
    TextInput({placeholder:'EntryAbility',text:this.appAbility}).onChange((v:string)=>this.appAbility=v)
    Button('保存应用入口').onClick(()=>this.action('app-save'))
   }else{
    Text('备份包含会话、模型元数据、角色、技能、MCP 配置与工作区文件。密钥和运行授权不导出。恢复前结束全部运行和排队任务。').fontSize(13).width('100%')
    Button('导出备份').enabled(!this.busy).onClick(()=>this.action('backup'))
    Button('清理旧恢复目录').enabled(!this.busy).onClick(()=>this.getUIContext().showAlertDialog({title:'清理旧恢复目录',message:'只删除未使用的历史恢复副本，当前工作区和最初的工作区保留。请先确认恢复结果。',primaryButton:{value:'取消',action:()=>{}},secondaryButton:{value:'清理',action:()=>{this.action('cleanup');}}}))
    Button('选择备份并校验').enabled(!this.busy).onClick(()=>this.action('restore-check'))
   }
   if(this.status){Text(this.status).fontSize(12).width('100%').copyOption(CopyOptions.LocalDevice)}
  }.padding(20).width('100%').backgroundColor(this.color('#FFFFFF')).borderRadius(22)
 }
}
