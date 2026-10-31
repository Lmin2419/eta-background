// Harmony adapter for app/src/main/kotlin/io/github/mangi/eta/data/db/EtaDatabase.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { ConversationDao, ConversationDatabase } from './conversationDao';
/** No write/delete methods are exposed for the old store. */
export interface LegacySnapshotSource { read(): Promise<LegacySnapshot>; }
export interface LegacySnapshot { document: string; evidence: string; }
export interface MigrationDao extends ConversationDao {
  migrationRecord(key: string): Promise<string | null>;
  putMigrationRecord(key: string, value: string): Promise<void>;
  diagnostic(code: string, at: number): Promise<void>;
}
export interface MigrationDatabase extends ConversationDatabase { migrationTransaction<T>(work: (dao: MigrationDao) => Promise<T>): Promise<T>; }
export type MigrationCode = 'source_invalid' | 'source_changed' | 'target_occupied' | 'verification_failed' | 'write_failed';
export class MigrationFailure extends Error { constructor(readonly code: MigrationCode) { super('Conversation migration: ' + code); } }
