/** Async editor results may only commit to the request generation that created them. */
export class RequestEpoch {
  private value=0;
  next():number{return ++this.value;}
  current(value:number):boolean{return value===this.value;}
  require(value:number):void{if(!this.current(value))throw new Error('编辑内容已变化，旧请求结果已忽略');}
}
