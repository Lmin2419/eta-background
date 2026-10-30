export interface TokenUsage {input:number;output:number;total:number}
export function usageOf(value:any):TokenUsage|undefined{
 if(!value)return undefined;const input=value.input_tokens??value.prompt_tokens??value.promptTokenCount??value.input,output=value.output_tokens??value.completion_tokens??value.candidatesTokenCount??value.output;
 if(typeof input!=='number'||typeof output!=='number'||!Number.isSafeInteger(input)||!Number.isSafeInteger(output)||input<0||output<0)return undefined;
 const total=value.total_tokens??value.totalTokenCount??value.total??input+output;return {input,output,total:typeof total==='number'&&Number.isSafeInteger(total)&&total>=0?total:input+output};
}
export function sumUsage(a:TokenUsage|undefined,b:TokenUsage):TokenUsage{return {input:(a?.input||0)+b.input,output:(a?.output||0)+b.output,total:(a?.total||0)+b.total};}
