/** Accept PCM16 mono 16 kHz WAV, including unknown RIFF chunks; never reinterpret other codecs as PCM. */
export function wavPcm(bytes:Uint8Array):Uint8Array{
 if(bytes.length>2_000_000||bytes.length<44)throw new Error('WAV 文件为空或超过一分钟上限');
 const text=(at:number,n:number)=>String.fromCharCode(...bytes.slice(at,at+n)),view=new DataView(bytes.buffer,bytes.byteOffset,bytes.byteLength);
 if(text(0,4)!=='RIFF'||text(8,4)!=='WAVE')throw new Error('请选择 WAV PCM16 单声道 16kHz 音频');
 let format=false,pcm:Uint8Array|undefined;
 for(let at=12;at+8<=bytes.length;){const length=view.getUint32(at+4,true);if(at+8+length>bytes.length)throw new Error('WAV 数据不完整');
 if(text(at,4)==='fmt '){if(length<16||view.getUint16(at+8,true)!==1||view.getUint16(at+10,true)!==1||view.getUint32(at+12,true)!==16000||view.getUint16(at+22,true)!==16)throw new Error('仅支持 PCM16、单声道、16kHz WAV');format=true;}
 if(text(at,4)==='data')pcm=bytes.slice(at+8,at+8+length);at+=8+length+(length%2);
 }
 if(!format||!pcm?.length||pcm.length%2||pcm.length>1_920_000)throw new Error('WAV 格式或时长无效');return pcm;
}
export function speechChunks(text:string):string[]{const cleaned=text.replace(/```[\s\S]*?```/g,'代码块已略过。').replace(/[*#`]/g,'').slice(0,12000),chunks:string[]=[];for(let at=0;at<cleaned.length;at+=300)chunks.push(cleaned.slice(at,at+300));return chunks;}
