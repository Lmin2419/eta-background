import { UnknownToolOutcomeError } from './types';
import { evidence, NoProgressTracker, scrollTarget, targetObservation } from './actionEvidence';
import { DeviceTools, Observation, Rect, RunControl, ToolResult, UiNode } from './types';

export interface HdcTransport { command(command: string, target: string, timeoutMs: number): Promise<string> }
export interface ScreenSize { width: number; height: number; displayId?:number; rotation?:number }
export class HdcDisconnectedError extends Error {}
export function quote(value: string): string {
  if (value.includes('\0')) throw new Error('参数中不允许包含 NUL');
  return "'" + value.replace(/'/g, "'\\''") + "'";
}
export function validateTarget(value: string): string {
  const match = /^(\d{1,3}(?:\.\d{1,3}){3}):(\d{1,5})$/.exec(value.trim());
  if (!match || match[1].split('.').some(n => Number(n) > 255) || Number(match[2]) < 1 || Number(match[2]) > 65535) throw new Error('请输入无线调试页面显示的 IPv4 地址:端口');
  return value.trim();
}
function object(value: unknown): Record<string,unknown> {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string,unknown> : {};
}
function parseBounds(value: unknown): Rect | undefined {
  if (typeof value === 'string') {
    const n = value.match(/-?\d+(?:\.\d+)?/g)?.map(Number);
    if (n?.length === 4) return {left:n[0],top:n[1],right:n[2],bottom:n[3]};
  }
  const r = object(value);
  if (['left','top','right','bottom'].every(k => typeof r[k] === 'number' && Number.isFinite(r[k]))) return r as unknown as Rect;
  return undefined;
}
function hash(text: string): string {
  let h = 2166136261;
  for (let i=0;i<text.length;i++) h=Math.imul(h^text.charCodeAt(i),16777619);
  return (h>>>0).toString(16);
}
export function parseLayout(raw: string, width: number, height: number): {nodes:UiNode[];fingerprint:string;truncated:boolean;bundleName?:string} {
  if (raw.length > 4_000_000) throw new Error('页面树过大');
  const root: unknown = JSON.parse(raw);
  const nodes: UiNode[] = []; const signatures: string[] = []; const bundles=new Set<string>();
  const stack: unknown[] = Array.isArray(root) ? [...root] : [root];
  let visited=0;
  while(stack.length && visited++<10000) {
    const node=object(stack.pop()); const a=object(node.attributes || node);
    const b=parseBounds(a.bounds);if(typeof a.bundleName==='string'&&a.bundleName)bundles.add(a.bundleName);
    const children=node.children;
    if(Array.isArray(children)) for(let i=children.length-1;i>=0;i--) stack.push(children[i]);
    if(!b || a.visible === false || a.visible === 'false') continue;
    const sensitive=a.password === true || a.password === 'true' || a.isPassword === true;
    const fullText=sensitive?'[密码控件]':String(a.text || a.description || '');
    const text=fullText.slice(0,300);
    const type=String(a.type || a.componentType || '');
    const clickable=a.clickable===true || a.clickable==='true';
    const editable=!sensitive && (a.editable===true || a.editable==='true' || /TextInput|TextArea|EditText/.test(type));
    signatures.push(JSON.stringify([fullText,type,b,clickable,editable,a.bundleName || '',a.id || '',a.enabled,a.checked,a.selected]));
    if(nodes.length<120 && b.right>b.left && b.bottom>b.top && b.right>0 && b.bottom>0 && b.left<width && b.top<height) {
      nodes.push({id:nodes.length,text,type,clickable,editable,scrollable:a.scrollable===true||a.scrollable==='true',resourceId:typeof a.id==='string'?a.id:undefined,bounds:{left:Math.max(0,b.left),top:Math.max(0,b.top),right:Math.min(width,b.right),bottom:Math.min(height,b.bottom)}});
    }
  }
  if(stack.length) throw new Error('页面节点过多，无法完整校验，请切换到更简单的页面');
  if (!visited || !signatures.length) throw new Error('当前页面没有可读取的控件树，不能确认操作目标');
  return {nodes,fingerprint:hash(JSON.stringify([width,height,signatures])),truncated:stack.length>0 || signatures.length>120,bundleName:bundles.size===1?Array.from(bundles)[0]:undefined};
}

export class HarmonyDevice implements DeviceTools {
  private current: Observation | undefined;
  private sequence=0;
  private epoch=0;
  private target='';
  private shellSequence=0;
  private inputHelp='';
  debugIdentity='';
  private noProgress=new NoProgressTracker();
  private launched:{bundle:string;ability:string}|undefined;
  get verifiedTarget():{bundle:string;ability:string}|undefined{return this.launched&&this.current?.bundleName===this.launched.bundle?this.launched:undefined;}
  supports(name:string):boolean {if(name==='long_press')return this.inputHelp.includes('longClick');if(name==='drag')return this.inputHelp.includes('drag');return ['observe_screen','tap_element','tap','swipe','input_text','back','home','launch_app','wait','wait_for','scroll'].includes(name);}
  constructor(private transport:HdcTransport, private size:()=>ScreenSize, private screenshots:()=>boolean) {}
  resetActionHistory():void{this.noProgress.clear();this.launched=undefined;this.invalidate();}
  get isConnected():boolean {return Boolean(this.target);}
  invalidate():void { this.current=undefined; this.epoch++; }
  async connect(target:string):Promise<string> {
    this.invalidate(); this.target='';
    const valid=validateTarget(target);
    const answer=await this.transport.command('tconn '+valid,'',20000);
    // A tconn response alone does not establish authorization or shell access.
    this.target=valid;
    try {
      const identity=await this.shell('id');
      this.debugIdentity=identity.trim().slice(0,160);
      if(!/uid=\d+/.test(identity)) throw new Error('调试连接未授权，请确认手机上的授权弹窗');
      const help=await this.shell('uitest help');
      if(!help.includes('dumpLayout') || !help.includes('uiInput')) throw new Error('设备未提供所需 UITest 命令');
      try{this.inputHelp=await this.shell('uitest uiInput -h');}catch(_){this.inputHelp='';}
      if(!this.target)throw new Error('连接检测期间调试会话已失效');
      this.noProgress.clear();
      return '已连接本机，页面读取和输入命令可用';
    } catch(error) { this.target=''; throw new Error('连接未就绪：'+(error instanceof Error?error.message:answer)); }
  }
  private async shell(command:string, timeout=15000):Promise<string> {
    if(!this.target) throw new Error('请先连接本机无线调试');
    const marker='__HARMONY_AGENT_EXIT_'+(++this.shellSequence)+'__';
    const wrapped='(\n'+command+'\n)\nagent_rc=$?\nprintf "\\n'+marker+'%s\\n" "$agent_rc"';
    let output:string;
    try {output=await this.transport.command('shell sh -c '+quote(wrapped),this.target,timeout);}
    catch(_) {this.target='';this.debugIdentity='';this.invalidate();throw new HdcDisconnectedError('无线调试连接中断。请确认调试地址和授权后重新连接；未自动重试操作');}
    const at=output.lastIndexOf('\n'+marker);
    if(at<0) {this.target='';this.debugIdentity='';this.invalidate();throw new HdcDisconnectedError('调试会话中断或命令结果未知。请点“重新连接”并确认系统授权；未自动重试');}
    const status=output.slice(at+marker.length+1).trim();
    if(status!=='0') throw new Error('设备命令失败（'+status.slice(0,8)+'）：'+output.slice(0,300));
    return output.slice(0,at);
  }
  private async layout():Promise<string> {
    const path='/data/local/tmp/harmony_agent_'+Date.now()+'_'+(++this.sequence)+'.json';
    return this.shell('uitest dumpLayout -p '+quote(path)+' >/dev/null && cat '+quote(path)+'; agent_dump_rc=$?; rm -f '+quote(path)+'; exit "$agent_dump_rc"');
  }
  /** Explicit diagnostics only: no screen creation, app launch, input injection or model tool registration. */
  async virtualScreenDiagnostics(control:RunControl):Promise<string> {
    const checks=[{name:'调试身份',command:'id'},{name:'Ability 启动参数',command:'aa help'},
      {name:'UITest 能力',command:'uitest help'},{name:'显示服务目录',command:'hidumper -ls'}];
    const results:string[]=[];
    for(const check of checks) {
      control.check();
      try {
        const raw=await this.shell(check.command,8000);control.check();
        const output=check.name==='显示服务目录'?raw.split('\n').filter(line=>/display|screen|window/i.test(line)).join('\n'):raw;
        results.push(check.name+'\n'+(output.slice(0,5000)||'命令返回成功，但没有相关条目'));
      } catch(e) {control.check();results.push(check.name+'\n'+(e as Error).message);if(e instanceof HdcDisconnectedError){results.push('后续检测已停止，不能据此判断系统是否支持虚拟屏。');break;}}
    }
    return results.join('\n\n')+'\n\n以上仅是接口与帮助信息，未验证虚拟屏创建、应用迁移、截图或输入闭环。';
  }
  private async observe(control:RunControl, screenshot:boolean):Promise<Observation> {
    const epoch=this.epoch;
    const size=this.size();
    if(size.width<=0 || size.height<=0) throw new Error('屏幕尺寸不可用');
    const raw=await this.layout(); control.check();
    const parsed=parseLayout(raw,size.width,size.height);
    const obs:Observation={...parsed,id:'view-'+(++this.sequence),capturedAt:Date.now(),width:size.width,height:size.height,displayId:size.displayId||0,rotation:size.rotation||0,geometryEpoch:epoch};
    if(screenshot) {
      const path='/data/local/tmp/harmony_agent_'+Date.now()+'.png';
      const data=await this.shell('uitest screenCap -p '+quote(path)+' >/dev/null && base64 '+quote(path)+'; agent_cap_rc=$?; rm -f '+quote(path)+'; exit "$agent_cap_rc"',20000);
      control.check();
      const image=data.replace(/\s/g,'');
      if(image.length>12_000_000 || !image.startsWith('iVBORw0KGgo')) throw new Error('截图格式或大小不符合要求');
      const afterSize=this.size();
      const after=parseLayout(await this.layout(),afterSize.width,afterSize.height);
      if(after.fingerprint!==obs.fingerprint) throw new Error('采集期间页面变化，请重新观察');
      obs.screenshot=image;
    }
    control.check();
    if(epoch!==this.epoch || control.paused) throw new Error('观察期间已交还控制，请重新观察');
    this.current=obs; return {...obs};
  }
  private async validate(args:Record<string,unknown>, control:RunControl):Promise<Observation> {
    const obs=this.current;
    if(!obs || args.observation_id!==obs.id) throw new Error('STALE_OBSERVATION：请先重新 observe_screen');
    if(Date.now()-obs.capturedAt>60000) {this.invalidate();throw new Error('观察已过期，请重新观察');}
    const epoch=this.epoch;
    const size=this.size();
    const fresh=parseLayout(await this.layout(),size.width,size.height); control.check();
    if(epoch!==this.epoch || control.paused || fresh.fingerprint!==obs.fingerprint||(size.rotation||0)!==(obs.rotation||0)||(size.displayId||0)!==(obs.displayId||0)) {this.invalidate();throw new Error('PAGE_CHANGED：页面或控制权变化，请重新观察');}
    return obs;
  }
  async execute(name:string,args:Record<string,unknown>,control:RunControl):Promise<ToolResult> {
    await control.checkpoint();
    if(name==='observe_screen') return {ok:true,observation:await this.observe(control,this.screenshots())};
    if(name==='wait_for'){
      const text=args.text;if(typeof text!=='string'||!text.trim()||text.length>300)throw new Error('等待文本无效');
      const timeout=args.timeout_ms===undefined?5000:args.timeout_ms;if(typeof timeout!=='number'||!Number.isInteger(timeout)||timeout<100||timeout>10000)throw new Error('等待上限为 100–10000 毫秒');
      const until=Date.now()+timeout;let obs:Observation|undefined;
      do{await control.checkpoint();obs=await this.observe(control,false);if(obs.nodes.some(n=>n.text.includes(text)))return {ok:true,message:'已观察到目标文本',observation:obs};await new Promise<void>(resolve=>setTimeout(resolve,300));}while(Date.now()<until);
      return {ok:false,code:'WAIT_TIMEOUT',message:'未在期限内观察到目标文本，不能据此判断内容不存在',observation:obs};
    }
    if(name==='wait') {
      const ms=args.milliseconds;
      if(typeof ms!=='number' || !Number.isInteger(ms) || ms<100 || ms>3000) throw new Error('等待时长应在 100–3000 毫秒之间');
      await new Promise<void>(resolve=>setTimeout(resolve,ms)); await control.checkpoint();
      return {ok:true,observation:await this.observe(control,false)};
    }
    let before:Observation|undefined,key='',scrollNode:UiNode|undefined;
    if(name==='launch_app') {
      const bundle=args.bundle, ability=args.ability;
      if(typeof bundle!=='string' || !/^[A-Za-z][\w]*(?:\.[\w]+)+$/.test(bundle) || typeof ability!=='string' || !/^[\w.]+$/.test(ability)) throw new Error('无效应用入口');
      control.check();this.invalidate();
      try{await this.shell('aa start -b '+quote(bundle)+' -a '+quote(ability));}catch(e){control.check();if(e instanceof HdcDisconnectedError)throw e;throw new UnknownToolOutcomeError('启动命令没有返回可确认的成功结果，请检查当前应用后再继续');}
      this.launched={bundle,ability};
    } else {
      if(!['tap','tap_element','swipe','input_text','back','home','long_press','drag','scroll'].includes(name)||!this.supports(name)) throw new Error('工具不可用：'+name);
      const obs=await this.validate(args,control);before=obs;key=name+':'+JSON.stringify(args).replace(obs.id,'current')+':'+(obs.displayId||0);
      if(name==='scroll'){scrollNode=scrollTarget(obs.nodes,args.node_id);before=targetObservation(obs,scrollNode);key='scroll:'+String(scrollNode.resourceId||scrollNode.type+JSON.stringify(scrollNode.bounds))+':'+String(args.direction)+':'+(obs.displayId||0);}
      if(this.noProgress.blocked(key,before))return {ok:false,code:'NO_PROGRESS',message:'同一目标连续三次没有可验证变化，本次未执行。请重新观察或换目标，不要反复点击。',observation:obs,evidence:evidence(before,undefined,false)};
      const point=(key:string,limit:number):number=> {const n=args[key]; if(typeof n!=='number'||!Number.isFinite(n)||n<0||n>=limit) throw new Error('坐标越界：'+key);return Math.floor(n);};
      let cmd='';
      if(name==='back' || name==='home') cmd='uitest uiInput keyEvent '+(name==='back'?'Back':'Home');
      if(name==='tap') cmd=`uitest uiInput click ${point('x',obs.width)} ${point('y',obs.height)}`;
      if(name==='long_press')cmd=`uitest uiInput longClick ${point('x',obs.width)} ${point('y',obs.height)}`;
      if(name==='drag')cmd=`uitest uiInput drag ${point('x1',obs.width)} ${point('y1',obs.height)} ${point('x2',obs.width)} ${point('y2',obs.height)} 600`;
      if(name==='scroll'){
        const b=scrollNode!.bounds,w=b.right-b.left,h=b.bottom-b.top,d=args.direction;
        if(!['up','down','left','right'].includes(String(d))||w<20||h<20)throw new Error('滚动方向或目标区域无效');
        const cx=(b.left+b.right)/2,cy=(b.top+b.bottom)/2;let x1=cx,x2=cx,y1=cy,y2=cy;
        if(d==='down'){y1=b.top+h*0.8;y2=b.top+h*0.2;}if(d==='up'){y1=b.top+h*0.2;y2=b.top+h*0.8;}if(d==='right'){x1=b.left+w*0.8;x2=b.left+w*0.2;}if(d==='left'){x1=b.left+w*0.2;x2=b.left+w*0.8;}
        cmd=`uitest uiInput swipe ${Math.floor(x1)} ${Math.floor(y1)} ${Math.floor(x2)} ${Math.floor(y2)} 600`;
      }
      if(name==='swipe') cmd=`uitest uiInput swipe ${point('x1',obs.width)} ${point('y1',obs.height)} ${point('x2',obs.width)} ${point('y2',obs.height)} 600`;
      if(name==='tap_element' || name==='input_text') {
        const id=args.node_id;
        if(typeof id!=='number' || !Number.isInteger(id)) throw new Error('无效控件 ID');
        const node=obs.nodes.find(n=>n.id===id);
        if(!node) throw new Error('控件不存在');
        const x=Math.floor((node.bounds.left+node.bounds.right)/2),y=Math.floor((node.bounds.top+node.bounds.bottom)/2);
        cmd=`uitest uiInput click ${x} ${y}`;
        if(name==='input_text') {
          if(!node.editable) throw new Error('目标不是已识别的可编辑控件');
          if(typeof args.text!=='string' || args.text.length>1000 || args.text.includes('\0')) throw new Error('输入内容无效或超过 1000 字符');
          cmd=`uitest uiInput inputText ${x} ${y} ${quote(args.text)}`;
        }
      }
      control.check(); this.invalidate();
      try{await this.shell(cmd);}catch(e){control.check();if(e instanceof HdcDisconnectedError)throw e;throw new UnknownToolOutcomeError('操作命令没有返回可确认的成功结果，请检查页面后再继续');}
    }
    await new Promise<void>(resolve=>setTimeout(resolve,350));
    await control.checkpoint();
    let after:Observation;try{after=await this.observe(control,false);}catch(e){control.check();if(e instanceof HdcDisconnectedError)throw e;throw new UnknownToolOutcomeError('动作已提交，但后续观察失败，结果未知。任务停止，请先核对页面后再决定是否继续');}
    const afterEvidence=scrollNode?targetObservation(after,scrollNode):after;
    if(key)this.noProgress.record(key,before,afterEvidence);
    const proof=evidence(before,afterEvidence,true);
    return {ok:true,message:proof.outcome==='CHANGED'?'已观察到页面变化；仍需确认业务目标':proof.outcome==='NO_OBSERVED_CHANGE'?'命令已提交，未观察到目标变化；先核对页面，不要直接重放':'命令已提交，但缺少可比较的效果证据；先重新观察',observation:after,evidence:proof};
  }
}
