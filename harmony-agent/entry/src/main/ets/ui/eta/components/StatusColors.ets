// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/components/StatusColors.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
export enum EtaRunStatus { Running, Success, Failed, Cancelled }
export enum EtaPermissionStatus { Available, Warning, Missing, Disabled }
export function runStatusColor(status: EtaRunStatus): ResourceColor {
  switch (status) {
    case EtaRunStatus.Running: return $r('app.color.eta_status_running');
    case EtaRunStatus.Success: return $r('app.color.eta_status_success');
    case EtaRunStatus.Failed: return $r('app.color.eta_status_error');
    default: return $r('app.color.eta_status_idle');
  }
}
export function runStatusLabel(status: EtaRunStatus): Resource {
  switch (status) {
    case EtaRunStatus.Running: return $r('app.string.tool_status_running');
    case EtaRunStatus.Success: return $r('app.string.tool_status_success');
    case EtaRunStatus.Failed: return $r('app.string.tool_status_failed');
    default: return $r('app.string.status_cancelled');
  }
}
export function permissionStatusColor(status: EtaPermissionStatus): ResourceColor {
  switch (status) {
    case EtaPermissionStatus.Warning: return $r('app.color.eta_status_warning');
    case EtaPermissionStatus.Missing: return $r('app.color.eta_status_error');
    default: return $r('app.color.eta_status_idle');
  }
}
export function permissionStatusLabel(status: EtaPermissionStatus): Resource {
  switch (status) {
    case EtaPermissionStatus.Available: return $r('app.string.status_ready');
    case EtaPermissionStatus.Warning: return $r('app.string.status_needs_attention');
    case EtaPermissionStatus.Missing: return $r('app.string.status_unauthorized');
    default: return $r('app.string.status_disabled');
  }
}
