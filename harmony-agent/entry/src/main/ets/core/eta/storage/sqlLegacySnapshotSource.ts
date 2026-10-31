// Harmony 0.9 read-only source for the Eta database port @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { LegacySnapshotSource, LegacySnapshot, MigrationFailure } from './migrationPorts';
import { ConversationSqlConnection } from './sqlConversationDatabase';
import { SerialGate } from './conversationDao';
/** Supply an exclusively owned, read-only connection. Never initialize or alter the old schema. */
export class SqlLegacySnapshotSource implements LegacySnapshotSource {
  private gate = new SerialGate();
  constructor(private sql: ConversationSqlConnection) {}
  private async payload(table: 'documents' | 'conversations', id: string): Promise<string> {
    const parts: string[] = [];
    for (let offset = 1;; offset += 16384) {
      const rows = await this.sql.query(`SELECT substr(payload,?,16384) AS part,length(payload) AS size FROM ${table} WHERE id=?`, [offset, id]);
      if (rows.length !== 1 || typeof rows[0].part !== 'string') throw new MigrationFailure('source_invalid');
      parts.push(rows[0].part); if (offset + 16384 > Number(rows[0].size)) return parts.join('');
    }
  }
  read(): Promise<LegacySnapshot> {
    return this.gate.run(async () => {
      await this.sql.begin();
      try {
        const meta = await this.payload('documents', 'conversation_meta'), rows = await this.sql.query('SELECT id FROM conversations ORDER BY rowid');
        const raw: string[] = [];
        for (const row of rows) { const payload = await this.payload('conversations', String(row.id)); if (JSON.parse(payload).id !== row.id) throw new MigrationFailure('source_invalid'); raw.push(payload); }
        const value = JSON.parse(meta); const document = JSON.stringify({ version: value.version, selectedId: value.selectedId, conversations: raw.map(r => JSON.parse(r)) });
        const evidence = JSON.stringify({ meta, rows: raw }); await this.sql.commit(); return { document, evidence };
      } catch (_) { await this.sql.rollback(); throw new MigrationFailure('source_invalid'); }
    });
  }
}
