// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/components/AgentModelPicker.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
export class EtaListDataSource<T> implements IDataSource {
  private values: T[] = [];
  private listeners: DataChangeListener[] = [];
  totalCount(): number { return this.values.length; }
  getData(index: number): T { return this.values[index]; }
  replace(values: T[]): void { this.values=values;this.listeners.forEach(listener=>listener.onDataReloaded()); }
  registerDataChangeListener(listener: DataChangeListener): void { if(!this.listeners.includes(listener))this.listeners.push(listener); }
  unregisterDataChangeListener(listener: DataChangeListener): void { this.listeners=this.listeners.filter(current=>current!==listener); }
}
