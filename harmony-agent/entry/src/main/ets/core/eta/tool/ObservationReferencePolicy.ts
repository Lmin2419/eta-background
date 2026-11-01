// Ported from app/src/main/kotlin/io/github/mangi/eta/agent/tool/ObservationReferencePolicy.kt @ 3b1794d
// Copyright © 2026 蛮吉 (Mangi-11). PolyForm Noncommercial 1.0.0.
export enum ObservationStatus {
  MATCH = 'MATCH',
  NO_OBSERVATION = 'NO_OBSERVATION',
  ID_REQUIRED = 'ID_REQUIRED',
  STALE = 'STALE',
}
export function validateObservationReference(currentId: string | null, requestedId: string | null): ObservationStatus {
  if (currentId === null) return ObservationStatus.NO_OBSERVATION;
  if (requestedId === null || requestedId.trim() === '') return ObservationStatus.ID_REQUIRED;
  if (requestedId !== currentId) return ObservationStatus.STALE;
  return ObservationStatus.MATCH;
}
