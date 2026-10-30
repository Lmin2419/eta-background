import { ModelSettings, HttpTransport, OpenAIClient, testModel } from './model';
import { RunControl } from './types';
import { redactSecrets } from './conversations';
export interface DiagnosticStage {id:string;name:string;state:'passed'|'failed'|'unverified';detail:string;at:number}
export async function diagnoseModel(settings:ModelSettings,transport:HttpTransport,control:RunControl,publish:(stage:DiagnosticStage)=>void,vision=false):Promise<DiagnosticStage[]>{
  const results:DiagnosticStage[]=[];
  const record=(id:string,name:string,state:DiagnosticStage['state'],detail:string)=>{const r={id,name,state,detail:redactSecrets(detail,[settings.apiKey]).slice(0,500),at:Date.now()};results.push(r);publish(r);};
  for(const kind of ['text','stream','tools',...(vision?['vision']:[])]){
    await control.checkpoint();
    try{
      if(kind==='tools'){const message=await testModel(settings,transport);record(kind,'工具调用',message.includes('测试通过')?'passed':'unverified',message);continue;}
      if(kind==='stream'&&!transport.stream){record(kind,'流式','unverified','传输层没有流式接口');continue;}
      let chunks=0;
      const image='data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVR4nGP4//8/AAX+Av4N70a4AAAAAElFTkSuQmCC';
      const response=await new OpenAIClient({...settings,streaming:kind==='stream'},transport,[]).complete([{role:'user',content:kind==='vision'?[{type:'text',text:'这是用于连接诊断的合成图片，描述其颜色；不执行任何动作。'},{type:'image_url',image_url:{url:image}}]:'请用一句中文说明你已收到这条连接测试消息。不要调用工具。'}],control,()=>chunks++);
      if(response.finishReason!=='stop'||typeof response.message.content!=='string'||!response.message.content.trim())throw new Error('未收到完整文本回复');
      record(kind,kind==='text'?'文本':kind==='stream'?'流式':'图片输入',kind==='stream'&&chunks<2?'unverified':'passed',kind==='vision'?'合成图片请求被接受；真实页面理解仍需任务验证':kind==='stream'?(chunks>=2?'收到多次增量回调':'只收到一次输出，尚不能确认逐块流式'):'收到完整文本回复');
    }catch(e){control.check();record(kind,kind==='tools'?'工具调用':kind==='text'?'文本':kind==='stream'?'流式':'图片输入','failed',e instanceof Error?e.message:'请求失败');}
  }
  if(!vision)record('vision','图片输入','unverified','尚未发送合成图片测试');return results;
}
