import { speechRecognizer, textToSpeech } from '@kit.CoreSpeechKit';
import { audio } from '@kit.AudioKit';
import { common, abilityAccessCtrl } from '@kit.AbilityKit';
import { util } from '@kit.ArkTS';
import { speechChunks } from '../core/audio';
export class SpeechService {
 private static visible=true;
 static foreground():void{SpeechService.visible=true;}
 private speechGeneration=0;
 private static active:SpeechService|undefined;
 static background():void{SpeechService.visible=false;SpeechService.active?.cancel();SpeechService.active?.stopSpeaking();}
 private recognizer:speechRecognizer.SpeechRecognitionEngine|undefined;
 private capturer:audio.AudioCapturer|undefined;
 private speaker:textToSpeech.TextToSpeechEngine|undefined;
 private session='';private generation=0;private timer:number=-1;
 private chunks:string[]=[];private cursor=0;private paused=false;
 private onText:(text:string)=>void=()=>{};
 private onStatus:(text:string)=>void=()=>{};
 constructor(){SpeechService.active=this;}
 private async engine(result:(text:string)=>void,status:(text:string)=>void):Promise<number>{
  this.cancel();this.onText=result;this.onStatus=status;const gen=this.generation;
  const engine=await speechRecognizer.createEngine({language:'zh-CN',online:1});if(gen!==this.generation){engine.shutdown();throw new Error('语音已取消');}
  this.recognizer=engine;this.session=util.generateRandomUUID();let committed='';
  engine.setListener({onStart:():void=>status('正在听…'),onEvent:():void=>{},onResult:(_id:string,r:speechRecognizer.SpeechRecognitionResult):void=>{if(gen!==this.generation)return;const text=committed+r.result;if(r.isFinal)committed=text;result(text.slice(0,8000));},onComplete:():void=>{if(gen===this.generation){status('转写完成，请检查后发送');this.cancel();}},onError:(_id:string,code:number):void=>{if(gen===this.generation){status('语音服务不可用：'+code+'；请检查离线语音资源');this.cancel();}}});
  engine.startListening({sessionId:this.session,audioInfo:{audioType:'pcm',sampleRate:16000,soundChannel:1,sampleBit:16}});return gen;
 }
 async start(context:common.UIAbilityContext,result:(text:string)=>void,status:(text:string)=>void):Promise<void>{
  const permission=await abilityAccessCtrl.createAtManager().requestPermissionsFromUser(context,['ohos.permission.MICROPHONE']);if(permission.authResults.some(v=>v!==0))throw new Error('未授予麦克风权限');
  if(!SpeechService.visible)throw new Error('请在助手前台使用语音输入');
  const gen=await this.engine(result,status);
  try{const capturer=await audio.createAudioCapturer({streamInfo:{samplingRate:audio.AudioSamplingRate.SAMPLE_RATE_16000,channels:audio.AudioChannel.CHANNEL_1,sampleFormat:audio.AudioSampleFormat.SAMPLE_FORMAT_S16LE,encodingType:audio.AudioEncodingType.ENCODING_TYPE_RAW},capturerInfo:{source:audio.SourceType.SOURCE_TYPE_MIC,capturerFlags:0}});if(gen!==this.generation){await capturer.release();return;}this.capturer=capturer;
   capturer.on('readData',(buffer:ArrayBuffer):void=>{if(gen!==this.generation)return;try{this.recognizer?.writeAudio(this.session,new Uint8Array(buffer));}catch(_){status('语音写入失败');this.cancel();}});await capturer.start();this.timer=setTimeout(()=>{void this.finish();},60000);
  }catch(e){this.cancel();throw e as Error;}
 }
 async transcribe(pcm:Uint8Array,result:(text:string)=>void,status:(text:string)=>void):Promise<void>{
  const gen=await this.engine(result,status);status('正在转写所选音频…');
  for(let at=0;at<pcm.length;at+=3200){if(gen!==this.generation)return;this.recognizer?.writeAudio(this.session,pcm.slice(at,at+3200));await new Promise<void>(resolve=>setTimeout(resolve,100));}
  if(gen===this.generation){this.recognizer?.finish(this.session);this.timer=setTimeout(()=>this.cancel(),15000);}
 }
 async finish():Promise<void>{clearTimeout(this.timer);const c=this.capturer;this.capturer=undefined;if(c){await c.stop().catch(()=>{});await c.release().catch(()=>{});}this.recognizer?.finish(this.session);this.timer=setTimeout(()=>this.cancel(),15000);}
 cancel():void{this.generation++;clearTimeout(this.timer);const c=this.capturer;this.capturer=undefined;if(c)void c.stop().catch(()=>{}).finally(()=>c.release().catch(()=>{}));try{this.recognizer?.cancel(this.session);this.recognizer?.shutdown();}catch(_){}this.recognizer=undefined;}
 async speak(text:string,status:(text:string)=>void):Promise<void>{
  this.stopSpeaking();this.chunks=speechChunks(text);this.cursor=0;this.paused=false;if(!this.chunks.length)throw new Error('没有可播报的文字');
  const gen=this.speechGeneration;const speaker=await textToSpeech.createEngine({language:'zh-CN',person:13,online:1});if(gen!==this.speechGeneration||!SpeechService.visible){speaker.shutdown();return;}this.speaker=speaker;
  this.speaker.setListener({onStart:():void=>status('正在播报'),onComplete:():void=>{if(gen!==this.speechGeneration||this.paused)return;this.cursor++;if(this.cursor<this.chunks.length)this.playChunk();else{status('播报完成');this.stopSpeaking();}},onStop:():void=>{},onError:(_id:string,code:number):void=>{status('播报不可用：'+code);this.stopSpeaking();}});this.playChunk();
 }
 private playChunk():void{this.speaker?.speak(this.chunks[this.cursor],{requestId:util.generateRandomUUID()});}
 pauseSpeech():void{this.paused=true;this.speaker?.stop();}
 resumeSpeech():void{if(this.paused&&this.speaker){this.paused=false;this.playChunk();}}
 stopSpeaking():void{this.speechGeneration++;this.paused=true;try{this.speaker?.stop();this.speaker?.shutdown();}catch(_){}this.speaker=undefined;this.chunks=[];}
}
