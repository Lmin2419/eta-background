// Ported from app/src/main/kotlin/io/github/mangi/eta/agent/model/GeminiToolSchema.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import {JsonObject,array,object,copy} from './providerTypes';
const combinators=['allOf','anyOf','oneOf'],maps=['properties','patternProperties','$defs','definitions','dependentSchemas','dependencies'],single=['additionalProperties','unevaluatedProperties','propertyNames','additionalItems','unevaluatedItems','contains','contentSchema','not','if','then','else'],references=['$id','id','$anchor','$dynamicAnchor','$recursiveAnchor','$ref','$dynamicRef','$recursiveRef'];
const has=(o:JsonObject,k:string)=>Object.prototype.hasOwnProperty.call(o,k);
function children(schema:JsonObject,visit:(child:JsonObject,same:boolean)=>void):void{
 for(const key of combinators)for(const raw of array(schema[key])){const child=object(raw);if(child)visit(child,true);}
 for(const key of maps)for(const raw of Object.values(object(schema[key])||{})){const child=object(raw);if(child)visit(child,false);}
 for(const key of single){const child=object(schema[key]);if(child)visit(child,false);}
 for(const key of ['items','prefixItems']){const raw=schema[key],child=object(raw);if(child)visit(child,false);else for(const item of array(raw)){const nested=object(item);if(nested)visit(nested,false);}}
}
function canCopy(value:unknown):boolean{if(typeof value==='boolean')return true;const schema=object(value);if(!schema||references.some(k=>has(schema,k)))return false;let safe=true;children(schema,child=>{if(!canCopy(child))safe=false;});return safe;}
function project(schema:JsonObject,outer:Map<string,unknown>):void {
 const properties=object(schema.properties)||{},canDeclare=!['additionalProperties','unevaluatedProperties'].some(k=>has(schema,k)&&schema[k]!==true),inherited=!canDeclare||references.some(k=>has(schema,k))?new Map<string,unknown>():outer;
 if(canDeclare)for(const raw of array(schema.required)){if(typeof raw!=='string'||!inherited.has(raw))continue;const definition=inherited.get(raw);if(!has(properties,raw)&&canCopy(definition)){Object.defineProperty(properties,raw,{value:copy(definition),writable:true,configurable:true,enumerable:true});schema.properties=properties;}}
 const visible=new Map(inherited);for(const [name,value] of Object.entries(properties))visible.set(name,value);children(schema,(child,same)=>project(child,same?visible:new Map()));
}
export function geminiToolSchema(parameters:JsonObject):JsonObject{const schema=copy(parameters);project(schema,new Map());return schema;}
