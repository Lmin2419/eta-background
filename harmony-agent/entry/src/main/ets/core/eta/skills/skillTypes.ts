// Ported from app/src/main/kotlin/io/github/mangi/eta/agent/skill/SkillModels.kt, SkillInstallationModels.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
export interface SkillEntry {id:string;name:string;description:string;rootPath:string;skillFilePath:string;compatibility:string|null;metadata:Record<string,string>;hasScripts:boolean;hasReferences:boolean;hasAssets:boolean;hasEvals:boolean;source:string;enabled:boolean;installed:boolean;}
export interface SkillRegistryEntry {enabled:boolean;source:string;installState:string;}
export interface SkillRegistry {read():Promise<Record<string,SkillRegistryEntry>>;write(entries:Record<string,SkillRegistryEntry>):Promise<void>;}
export interface BuiltinSkill {id:string;name:string;description:string;files:{path:string;content:string}[];}
export interface SkillCandidate {id:string;name:string;description:string;relativePath:string;directory:string;}
export interface InstalledSkill {id:string;name:string;description:string;}
export interface SkillConflict {id:string;name:string;existingSource:string;replaceAllowed:boolean;}
export type SkillErrorCode='INVALID_ARCHIVE'|'ARCHIVE_TOO_LARGE'|'TOO_MANY_ENTRIES'|'ENTRY_TOO_LARGE'|'EXTRACTED_CONTENT_TOO_LARGE'|'ENTRY_PATH_TOO_DEEP'|'UNSAFE_ENTRY_PATH'|'DUPLICATE_ENTRY'|'NO_SKILL_FOUND'|'MULTIPLE_SKILLS_FOUND'|'INVALID_SKILL'|'INVALID_SELECTION'|'DUPLICATE_SKILL_ID'|'TARGET_NOT_REPLACEABLE'|'CANCELLED'|'IO_ERROR'|'COMMIT_FAILED';
export type SkillInstallResult={kind:'success';installed:InstalledSkill[]}|{kind:'conflict';conflicts:SkillConflict[];archiveSha256:string|null}|{kind:'failure';code:SkillErrorCode;recoveryRequired:boolean};
export class SkillInstallError extends Error {constructor(readonly code:SkillErrorCode){super(code);}}
export class SkillRecoveryRequired extends Error {constructor(){super('SKILL_RECOVERY_REQUIRED');}}
export function skillFail(code:SkillErrorCode):never{throw new SkillInstallError(code);}
export function skillFailure(error:unknown):SkillInstallResult{return {kind:'failure',code:error instanceof SkillInstallError?error.code:error instanceof SkillRecoveryRequired?'COMMIT_FAILED':'IO_ERROR',recoveryRequired:error instanceof SkillRecoveryRequired};}
export interface SkillPackageLimits {maxArchiveBytes:number;maxExtractedBytes:number;maxSingleFileBytes:number;maxSkillFileBytes:number;maxEntries:number;maxPathDepth:number;}
export const SKILL_LIMITS:SkillPackageLimits={maxArchiveBytes:32*1024*1024,maxExtractedBytes:128*1024*1024,maxSingleFileBytes:32*1024*1024,maxSkillFileBytes:512*1024,maxEntries:2048,maxPathDepth:16};
export const SKILL_ROOT='skills',SKILL_WORK='.eta-skill-installer',SKILL_JOURNAL='pending-install.json';
export function validSkillId(value:string):boolean{return value.length>=1&&value.length<=64&&/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(value);}
export function checkSkillCancelled(isCancelled:()=>boolean):void{if(isCancelled())skillFail('CANCELLED');}
