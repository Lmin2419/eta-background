// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/app/AgentMemoryStore.kt,
// ui/model/AgentMemoryUiState.kt and ui/screens/memory/AgentMemoryScreen.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11). Page rendering and asynchronous IO are separate ports.
import {AgentMemoryRepository,AgentMemoryError,MAX_MEMORY_BYTES} from './agentMemoryRepository';import {utf8Size} from '../searchSuggestionsBudget';import {memoryCoreBudget} from './agentMemoryContext';
export type MemoryNotice='load_failed'|'toggle_failed'|'saved'|'save_failed'|'cleared'|'clear_failed'|'too_large'|'';
export interface MemoryUiState{enabled:boolean;isLoading:boolean;isSaving:boolean;draft:string;savedContent:string;draftBytes:number;maxBytes:number;coreBudgetChars:number;notice:MemoryNotice;mode:'preview'|'edit';hasUnsavedChanges:boolean;canSave:boolean;}
export function emptyMemoryState():MemoryUiState{return {enabled:true,isLoading:true,isSaving:false,draft:'',savedContent:'',draftBytes:0,maxBytes:MAX_MEMORY_BYTES,coreBudgetChars:8000,notice:'',mode:'preview',hasUnsavedChanges:false,canSave:false};}
export class AgentMemoryUiStore{
 private state=emptyMemoryState();private generation=0;private toggling=false;
 constructor(private repository:AgentMemoryRepository,private changed:()=>void){}
 snapshot():MemoryUiState{const s=this.state;return {...s,hasUnsavedChanges:s.draft!==s.savedContent,canSave:!s.isLoading&&!s.isSaving&&s.draft!==s.savedContent&&s.draftBytes<=s.maxBytes};}
 private publish():void{this.changed();}
 close():void{this.generation++;}
 async refresh(window:number|null):Promise<void>{const generation=++this.generation;this.state={...this.state,isLoading:true,isSaving:false,notice:''};this.publish();try{const snapshot=await this.repository.snapshot(),enabled=await this.repository.isEnabled();if(generation!==this.generation)return;this.state={...emptyMemoryState(),enabled,isLoading:false,draft:snapshot.content,savedContent:snapshot.content,draftBytes:snapshot.byteSize,coreBudgetChars:memoryCoreBudget(window),mode:snapshot.content.trim()?'preview':'edit'};}catch(_){if(generation!==this.generation)return;this.state={...this.state,isLoading:false,notice:'load_failed'};}this.publish();}
 draft(value:string):void{if(this.state.isLoading)return;this.state={...this.state,draft:value,draftBytes:utf8Size(value),notice:''};this.publish();}
 mode(value:'preview'|'edit'):void{if(this.state.isLoading)return;this.state={...this.state,mode:value};this.publish();}
 dismissNotice():void{this.state={...this.state,notice:''};this.publish();}
 discard():void{this.draft(this.state.savedContent);}
 async toggle(value:boolean):Promise<void>{if(this.state.isLoading||this.toggling)return;this.toggling=true;const gen=this.generation;try{await this.repository.setEnabled(value);if(gen===this.generation)this.state={...this.state,enabled:value,notice:''};}catch(_){if(gen===this.generation)this.state={...this.state,notice:'toggle_failed'};}finally{this.toggling=false;this.publish();}}
 async save():Promise<void>{if(!this.snapshot().canSave)return;await this.write(this.state.draft,false);}
 async clear():Promise<void>{if(this.state.isSaving||this.state.isLoading)return;await this.write('',true);}
 private async write(target:string,clear:boolean):Promise<void>{const gen=this.generation;this.state={...this.state,isSaving:true,notice:''};this.publish();try{const snapshot=await this.repository.replaceAll(target);if(gen!==this.generation)return;this.state={...this.state,isSaving:false,savedContent:snapshot.content,draft:clear||this.state.draft===target?snapshot.content:this.state.draft,notice:clear?'cleared':'saved'};this.state.draftBytes=utf8Size(this.state.draft);}catch(e){if(gen!==this.generation)return;this.state={...this.state,isSaving:false,notice:e instanceof AgentMemoryError&&e.code==='MEMORY_TOO_LARGE'?'too_large':clear?'clear_failed':'save_failed'};}this.publish();}
}
