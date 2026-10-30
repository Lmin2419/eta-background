import { decodeBase64, crc32 } from './backup';
export function rolePng(encoded:string):Uint8Array{
 if(encoded.length>1_000_000||!/^[A-Za-z0-9+/]*={0,2}$/.test(encoded))throw new Error('角色卡编码无效');
 const base=decodeBase64('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVR4nGP4//8/AAX+Av4N70a4AAAAAElFTkSuQmCC');
 const data='chara\0'+encoded,chunk=new Uint8Array(data.length+12),view=new DataView(chunk.buffer);view.setUint32(0,data.length);chunk.set([116,69,88,116],4);for(let i=0;i<data.length;i++)chunk[8+i]=data.charCodeAt(i);view.setUint32(data.length+8,crc32(chunk.slice(4,data.length+8)));
 const result=new Uint8Array(base.length+chunk.length);result.set(base.slice(0,-12));result.set(chunk,base.length-12);result.set(base.slice(-12),base.length-12+chunk.length);return result;
}
