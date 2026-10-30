import { usageOf } from './usage';
import { ModelMetadata, metadataFor, safeHeaders } from './modelMetadata';
import { Message, ModelClient, ModelReply, RunControl } from './types';
import { CompletionStream } from './stream';
import { ModelProtocol, completeProtocol } from './protocols';

export interface HttpTransport { post(url: string, headers: Record<string,string>, body: string): Promise<string>; get?(url: string, headers: Record<string,string>): Promise<string>; stream?(url:string,headers:Record<string,string>,body:string,onChunk:(chunk:string)=>void):Promise<void>; cancel(): void }
export interface ModelSettings { baseUrl: string; model: string; apiKey: string; screenshots: boolean; allowInsecureHttp?: boolean; streaming?: boolean; protocol?:ModelProtocol; maxOutputTokens?:number; reasoningEffort?:string;extraHeaders?:Record<string,string> }
export const field = (type: string, description: string): Record<string,unknown> => ({type, description});
const observation = field('string','最近一次观察返回的 observation.id');
export function tool(name: string, description: string, properties: Record<string,unknown>, required: string[]): Record<string,unknown> {
  return {type:'function',function:{name,description,parameters:{type:'object',properties,required,additionalProperties:false}}};
}
export const TOOLS = [
  tool('observe_screen','读取当前页面控件，并按配置附带截图。操作前使用。',{},[]),
  tool('tap_element','点击当前观察中的控件；返回新页面。',{observation_id:observation,node_id:field('integer','控件 ID')},['observation_id','node_id']),
  tool('tap','按当前截图像素坐标点击；返回新页面。',{observation_id:observation,x:field('number','横坐标'),y:field('number','纵坐标')},['observation_id','x','y']),
  tool('swipe','滑动；坐标基于当前观察的屏幕像素。',{observation_id:observation,x1:field('number','起点横坐标'),y1:field('number','起点纵坐标'),x2:field('number','终点横坐标'),y2:field('number','终点纵坐标')},['observation_id','x1','y1','x2','y2']),
  tool('input_text','在指定可编辑控件输入文本，随后检查页面。',{observation_id:observation,node_id:field('integer','可编辑控件 ID'),text:field('string','输入内容，最多 1000 字符')},['observation_id','node_id','text']),
  tool('back','返回上一页。',{observation_id:observation},['observation_id']),
  tool('home','返回桌面。',{observation_id:observation},['observation_id']),
  tool('launch_app','启动已知应用入口，不猜测包名；不知道时从桌面操作。',{bundle:field('string','应用 bundleName'),ability:field('string','入口 abilityName')},['bundle','ability']),
  tool('long_press','长按当前观察中的位置。',{observation_id:observation,x:field('number','横坐标'),y:field('number','纵坐标')},['observation_id','x','y']),
  tool('drag','拖动当前页面对象。',{observation_id:observation,x1:field('number','起点横坐标'),y1:field('number','起点纵坐标'),x2:field('number','终点横坐标'),y2:field('number','终点纵坐标')},['observation_id','x1','y1','x2','y2']),
  tool('scroll','滚动明确的容器。多个容器必须指定 node_id；返回目标变化证据。',{observation_id:observation,node_id:field('integer','滚动容器 ID'),direction:{type:'string',enum:['up','down','left','right']}},['observation_id','direction']),
  tool('wait_for','有限等待页面出现指定文本；超时不表示内容不存在。',{text:field('string','目标文本'),timeout_ms:field('integer','100–10000毫秒，默认5000')},['text']),
  tool('wait','等待页面加载，最长 3 秒，再返回新观察。',{milliseconds:field('integer','100 到 3000')},['milliseconds'])
];

/** Accept a base URL or the full endpoint; never silently change a provider host. */
export function modelEndpoint(settings: ModelSettings, kind: 'chat' | 'models' = 'chat'): string {
  const value = settings.baseUrl.trim().replace(/\/+$/, '');
  if (value.length > 2048 || /[\s\\?#\u0000-\u001f\u007f]/.test(value)) throw new Error('模型 API 地址无效：不能包含空白、查询参数或片段');
  const match = /^(https?):\/\/([^/]+)(\/.*)?$/.exec(value);
  if (!match || match[2].includes('@') || !/^(?:[a-zA-Z0-9.-]+|\[[0-9a-fA-F:]+\])(?::[0-9]{1,5})?$/.test(match[2])) throw new Error('模型 API 地址必须为 HTTPS，例如 https://api.example.com/v1');
  const port = /:([0-9]+)$/.exec(match[2]);
  if (port && (Number(port[1]) < 1 || Number(port[1]) > 65535)) throw new Error('API 端口无效');
  if (match[1] !== 'https' && !settings.allowInsecureHttp) throw new Error('默认仅允许 HTTPS；本地 HTTP 服务需手动开启明文连接');
  const base = value.replace(/\/(?:chat\/completions|responses|messages)$/, '');
  return base + (kind === 'chat' ? '/chat/completions' : '/models');
}
function headers(settings: ModelSettings): Record<string,string> {
  if (/[\r\n\u0000]/.test(settings.apiKey)) throw new Error('API Key 包含无效字符');
  const result: Record<string,string> = {'Content-Type':'application/json'};
  if(settings.apiKey.trim()){if(settings.protocol==='anthropic')result['x-api-key']=settings.apiKey.trim();else if(settings.protocol==='gemini')result['x-goog-api-key']=settings.apiKey.trim();else result.Authorization='Bearer '+settings.apiKey.trim();}
  if(settings.protocol==='anthropic')result['anthropic-version']='2023-06-01';
  return result;
}
function parse(raw: string): any {
  if (raw.length > 2_000_000) throw new Error('模型响应超过大小限制');
  try { return JSON.parse(raw); } catch (_) { throw new Error('服务返回的内容不是有效 JSON，请检查接口地址'); }
}
function reply(raw: string): ModelReply {
  const data=parse(raw);const choice = data?.choices?.[0];
  const message = choice?.message;
  if (!message || message.role !== 'assistant' || (message.content != null && typeof message.content !== 'string')) throw new Error('模型没有返回有效的 assistant 消息');
  if (message.tool_calls != null && !Array.isArray(message.tool_calls)) throw new Error('无效工具调用格式');
  for (const c of message.tool_calls || []) {
    if (!c || typeof c.id !== 'string' || !c.id || c.type !== 'function' || typeof c.function?.name !== 'string' || !c.function.name || typeof c.function?.arguments !== 'string') throw new Error('无效工具调用格式');
  }
  if (!message.content && !message.tool_calls?.length) throw new Error('模型返回空消息');
  return {message: {role:'assistant',content:message.content ?? null, ...(message.tool_calls ? {tool_calls:message.tool_calls} : {})},finishReason:choice.finish_reason,usage:usageOf(data.usage),reasoning:typeof message.reasoning_content==='string'?message.reasoning_content.slice(0,32000):undefined};
}
export interface ModelList { ids: string[]; partial: boolean;metadata?:ModelMetadata[] }
export async function fetchModels(settings: ModelSettings, transport: HttpTransport): Promise<ModelList> {
  if (!transport.get) throw new Error('当前传输不支持模型列表，请手动输入模型 ID');
  const base=modelEndpoint(settings,'models');
  const metadata:ModelMetadata[]=[];const ids: string[] = [];const seen=new Set<string>();const cursors=new Set<string>();
  let url=base,partial=false;
  for(let page=0;page<50;page++) {
    const data=parse(await transport.get(url,{...headers(settings),...safeHeaders(settings.extraHeaders)}));
    if(settings.protocol==='gemini'){data.data=data.models?.map((m:{name:string;displayName?:string;inputTokenLimit?:number})=>({id:m.name?.replace(/^models\//,''),name:m.displayName,inputTokenLimit:m.inputTokenLimit}));data.has_more=Boolean(data.nextPageToken);data.last_id=data.nextPageToken;}
    if(!Array.isArray(data?.data))throw new Error('服务未提供标准模型列表，请手动输入模型 ID');
    for(const row of data.data) {
      if(typeof row?.id==='string'&&row.id.trim()&&row.id.length<=256&&!seen.has(row.id)) {
        if(ids.length>=5000){partial=true;break;}
        seen.add(row.id);ids.push(row.id);metadata.push(metadataFor(row,row.id));
      }
    }
    if(partial)break;
    if(!data.has_more) {partial=Boolean(data.next);break;}
    const cursor=data.last_id||data.data[data.data.length-1]?.id;
    if(typeof cursor!=='string'||!cursor||cursor.length>256||cursors.has(cursor)||page===49){partial=true;break;}
    cursors.add(cursor);
    // Never follow a server-supplied URL with the user's bearer token.
    url=base+(settings.protocol==='gemini'?'?pageToken=':'?after=')+encodeURIComponent(cursor)+(settings.protocol==='gemini'?'&pageSize=100':'&limit=100');
  }
  if (!ids.length) throw new Error('没有可选模型，请手动输入模型 ID');
  return {ids,partial,metadata};
}
/** This probe sends synthetic text only; returned tools are validated, never executed. */
export async function testModel(settings: ModelSettings, transport: HttpTransport): Promise<string> {
  const probeTools = [tool('connection_check','Connection test only. Return {ok:true}.',{ok:{type:'boolean',enum:[true]}},['ok'])];
  const response = await new OpenAIClient(settings, transport, probeTools).complete([
    {role:'user',content:'Call connection_check with ok=true exactly once. This is a connection test; do not call any other tool.'}
  ], new RunControl());
  if (response.finishReason === 'length') throw new Error('测试回复被截断，尚未验证工具调用');
  const calls = response.message.tool_calls || [];
  if (calls.length !== 1 || calls[0].function.name !== 'connection_check') return '接口可用，但未验证 Tool Calling；执行手机任务前请换用支持工具调用的模型';
  const args = parse(calls[0].function.arguments);
  if (!args || args.ok !== true || Object.keys(args).length !== 1) throw new Error('测试工具参数无效，尚未验证工具调用');
  return '文本接口与工具调用测试通过；截图理解能力仍需实际任务验证';
}
export class OpenAIClient implements ModelClient {
  constructor(private settings: ModelSettings, private transport: HttpTransport, private tools: Record<string,unknown>[] = TOOLS) {}
  cancel(): void { this.transport.cancel(); }
  async complete(messages: Message[], control: RunControl, onText?: (text:string)=>void,onReasoning?:(text:string)=>void): Promise<ModelReply> {
    control.check();
    if (!this.settings.model.trim()) throw new Error('请填写模型名称');
    if(this.settings.protocol&&this.settings.protocol!=='chat_completions')return completeProtocol(this.settings.protocol,modelEndpoint(this.settings).replace(/\/chat\/completions$/,''),this.settings,this.transport,this.tools,messages,control,onText||(()=>{}),onReasoning||(()=>{}));
    const streaming=this.settings.streaming!==false&&Boolean(this.transport.stream);
    const body=JSON.stringify({model:this.settings.model.trim(),messages,...(this.settings.maxOutputTokens?{max_tokens:this.settings.maxOutputTokens}:{}),...(this.settings.reasoningEffort?{reasoning_effort:this.settings.reasoningEffort}:{}),...(this.tools.length?{tools:this.tools}:{}),stream:streaming});
    if(streaming) {
      const parser=new CompletionStream(text=>{control.check();onText?.(text);},text=>{control.check();onReasoning?.(text);});
      await this.transport.stream!(modelEndpoint(this.settings),{...headers(this.settings),...safeHeaders(this.settings.extraHeaders)},body,chunk=>{control.check();parser.feed(chunk);});
      control.check();return parser.finish(reply);
    }
    const raw = await this.transport.post(modelEndpoint(this.settings), {...headers(this.settings),...safeHeaders(this.settings.extraHeaders)},body);
    control.check();
    const result=reply(raw);if(typeof result.message.content==='string')onText?.(result.message.content);if(result.reasoning)onReasoning?.(result.reasoning);return result;
  }
}
