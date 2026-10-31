// Harmony RDB adapter for app/src/main/kotlin/io/github/mangi/eta/data/db/EtaDatabase.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { common } from '@kit.AbilityKit';
import { relationalStore } from '@kit.ArkData';
import { cryptoFramework } from '@kit.CryptoArchitectureKit';
import { util } from '@kit.ArkTS';
import { fileIo as fs } from '@kit.CoreFileKit';
import { ConversationSqlConnection, SqlConversationDatabase, SqlRow, SqlValue } from '../core/eta/storage/sqlConversationDatabase';
import { LegacySnapshot, LegacySnapshotSource, MigrationFailure } from '../core/eta/storage/migrationPorts';
import { SqlLegacySnapshotSource } from '../core/eta/storage/sqlLegacySnapshotSource';

class RdbConversationConnection implements ConversationSqlConnection {
  private store: relationalStore.RdbStore;
  constructor(store: relationalStore.RdbStore) { this.store = store; }
  execute(sql: string, args: SqlValue[] = []): Promise<void> { return this.store.executeSql(sql, args); }
  async query(sql: string, args: SqlValue[] = []): Promise<SqlRow[]> {
    const result = await this.store.querySql(sql, args); const rows: SqlRow[] = [];
    try {
      const columns = result.columnNames;
      while (result.goToNextRow()) {
        const row: SqlRow = {};
        for (let index = 0; index < columns.length; index++) {
          const value = result.getValue(index);
          if (value === null) row[columns[index]] = null;
          else if (typeof value === 'string') row[columns[index]] = value as string;
          else if (typeof value === 'number') row[columns[index]] = value as number;
          else throw new Error('Unexpected conversation database value');
        }
        rows.push(row);
      }
      return rows;
    } finally { result.close(); }
  }
  async begin(): Promise<void> { this.store.beginTransaction(); }
  async commit(): Promise<void> { this.store.commit(); }
  async rollback(): Promise<void> { this.store.rollBack(); }
}
/** Explicit opt-in factory. FoundationHost does not import or initialize this database in E07b2. */
export class EtaConversationDatabase extends SqlConversationDatabase {
  private nativeStore: relationalStore.RdbStore;
  private constructor(store: relationalStore.RdbStore) { super(new RdbConversationConnection(store)); this.nativeStore = store; }
  static async open(context: common.UIAbilityContext): Promise<EtaConversationDatabase> {
    const store = await relationalStore.getRdbStore(context, { name: 'eta_conversations_v1.db', securityLevel: relationalStore.SecurityLevel.S1 });
    return new EtaConversationDatabase(store);
  }
  // Owner must stop callers and await all transactions before closing.
  close(): Promise<void> { return this.nativeStore.close(); }
}
/** A separately opened, read-only handle; no old RuntimeDatabase migration side effects. */
export class EtaLegacyRdbSource implements LegacySnapshotSource {
  private nativeStore: relationalStore.RdbStore; private source: SqlLegacySnapshotSource;
  private constructor(store: relationalStore.RdbStore) { this.nativeStore = store; this.source = new SqlLegacySnapshotSource(new RdbConversationConnection(store)); }
  static async open(context: common.UIAbilityContext): Promise<EtaLegacyRdbSource> {
    const store = await relationalStore.getRdbStore(context, { name: 'assistant.db', securityLevel: relationalStore.SecurityLevel.S1, isReadOnly: true });
    return new EtaLegacyRdbSource(store);
  }
  read(): Promise<LegacySnapshot> { return this.source.read(); }
  close(): Promise<void> { return this.nativeStore.close(); }
}
/** File fallback is an explicit E07b3 decision, never a silent fallback after an RDB error. */
export class EtaLegacyFileSource implements LegacySnapshotSource {
  private path: string;
  constructor(context: common.UIAbilityContext) { this.path = context.filesDir + '/conversations.json'; }
  async read(): Promise<LegacySnapshot> {
    try { const raw = fs.readTextSync(this.path); return { document: raw, evidence: raw }; }
    catch (_) { throw new MigrationFailure('source_invalid'); }
  }
}
export async function etaConversationSha256(text: string): Promise<string> {
  const digest = cryptoFramework.createMd('SHA256');
  await digest.update({ data: new util.TextEncoder().encodeInto(text) });
  const blob = await digest.digest(); let hex = '';
  for (let i = 0; i < blob.data.length; i++) hex += blob.data[i].toString(16).padStart(2, '0');
  return hex;
}
