// Desktop SQL execution port for Eta's ConversationDao translation @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
// Executes real SQLite SQL; not a Harmony RDB or device test. Node >=22.13 provides node:sqlite.
import { DatabaseSync } from 'node:sqlite';
import { ConversationSqlConnection, SqlRow, SqlValue } from '../../../entry/src/main/ets/core/eta/storage/sqlConversationDatabase';
export class SqliteConversationConnection implements ConversationSqlConnection {
  readonly native: DatabaseSync; failSql = ''; failCommit = false; executions: string[] = [];
  constructor(path = ':memory:', readOnly = false) { this.native = new DatabaseSync(path, { readOnly }); }
  async execute(sql: string, args: SqlValue[] = []): Promise<void> {
    this.executions.push(sql);
    if (this.failSql && sql.includes(this.failSql)) throw Error('Injected SQL failure');
    this.native.prepare(sql).run(...args);
  }
  async query(sql: string, args: SqlValue[] = []): Promise<SqlRow[]> { return this.native.prepare(sql).all(...args).map(row => ({ ...row })) as SqlRow[]; }
  async begin(): Promise<void> { this.native.exec('BEGIN'); }
  async commit(): Promise<void> { if (this.failCommit) throw Error('Injected commit failure'); this.native.exec('COMMIT'); }
  async rollback(): Promise<void> { this.native.exec('ROLLBACK'); }
  close(): void { this.native.close(); }
}
