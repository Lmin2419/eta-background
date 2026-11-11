# 电脑端（Windows）：把 dsh-bridge 插件装进 DeepSeek Harness 桌面版。
#
#   powershell -ExecutionPolicy Bypass -File install-plugin.ps1 [-Start] [-Force]
#
# 默认安装本仓库里的 dsh-bridge 目录（以链接方式，之后 git pull 再重启桌面版即可更新）。
#   -Start  安装后启动桌面版，并等待插件的健康检查通过
#   -Force  桌面版或旧的 dsh 进程还在运行时，直接结束它们
# 桌面版的插件只能在应用完全退出时修改（官方说明：先退出，再用桌面版自带的 dsh 命令）。
param(
  [string]$PluginDir = '',
  [switch]$Start,
  [switch]$Force
)
$ErrorActionPreference = 'Stop'

function Say($m) { Write-Host "==> $m" -ForegroundColor Cyan }
function Warn($m) { Write-Host "!!  $m" -ForegroundColor Yellow }
function Fail($m) { Write-Host "xx  $m" -ForegroundColor Red; exit 1 }

# ---- 插件目录 ---------------------------------------------------------------
if (-not $PluginDir) { $PluginDir = Join-Path $PSScriptRoot '..\..\dsh-bridge' }
$PluginDir = (Resolve-Path $PluginDir).Path
$Manifest = Join-Path $PluginDir 'package.json'
if (-not (Test-Path $Manifest)) { Fail "找不到插件：$Manifest" }
$PluginVersion = (Get-Content $Manifest -Raw | ConvertFrom-Json).version
Say "插件 dsh-bridge $PluginVersion：$PluginDir"

# ---- 桌面版 profile ---------------------------------------------------------
$DshHome = if ($env:DSH_HOME -and $env:DSH_HOME.Trim()) { $env:DSH_HOME } else { Join-Path $env:USERPROFILE '.dsh' }
$ProfileDir = Join-Path $DshHome 'profiles\desktop'
$ProfileManifest = Join-Path $ProfileDir 'package.json'
if (-not (Test-Path $ProfileManifest)) {
  Fail "没有找到桌面版的 profile（$ProfileDir）。请先打开一次 DeepSeek Harness 桌面版，再完全退出后重试。"
}

# ---- 桌面版自带的 dsh 命令 --------------------------------------------------
$Candidates = @()
foreach ($root in @("$env:LOCALAPPDATA\Programs", $env:ProgramFiles, ${env:ProgramFiles(x86)})) {
  if ($root -and (Test-Path $root)) {
    $Candidates += Get-ChildItem -Path $root -Directory -ErrorAction SilentlyContinue |
      ForEach-Object { Join-Path $_.FullName 'resources\runtime\cli\bin\dsh.cmd' } |
      Where-Object { Test-Path $_ }
  }
}
$Preferred = $Candidates | Where-Object { $_ -like '*DeepSeek Harness*' } | Select-Object -First 1
$Dsh = if ($Preferred) { $Preferred } else { $Candidates | Select-Object -First 1 }
if (-not $Dsh) {
  $cmd = Get-Command dsh.cmd -ErrorAction SilentlyContinue
  if ($cmd -and $cmd.Source -like '*resources\runtime\cli\bin\dsh.cmd') { $Dsh = $cmd.Source }
}
if (-not $Dsh) {
  Fail '没有找到桌面版自带的 dsh 命令（…\resources\runtime\cli\bin\dsh.cmd）。可在桌面版菜单「管理 dsh 命令…」中安装后重试。'
}
$AppDir = (Resolve-Path (Join-Path (Split-Path $Dsh) '..\..\..\..')).Path
$AppExe = Get-ChildItem -Path $AppDir -Filter '*.exe' -ErrorAction SilentlyContinue |
  Where-Object { $_.Name -notlike 'Uninstall*' } | Select-Object -First 1
Say "桌面版：$AppDir"

# ---- 先退出桌面版，并清理旧的独立 dsh 进程 --------------------------------
$Desktop = @()
if ($AppExe) { $Desktop = @(Get-Process -ErrorAction SilentlyContinue | Where-Object { $_.Path -eq $AppExe.FullName }) }
if ($Desktop.Count -gt 0) {
  if (-not $Force) { Fail '桌面版正在运行。请从任务栏托盘图标完全退出 DeepSeek Harness 后重试（或加 -Force）。' }
  Say '结束桌面版进程'
  $Desktop | Stop-Process -Force
  Start-Sleep -Seconds 3
}

# 旧的安装方式可能留下一个单独运行的 dsh web，它会占住 7788 端口，让桌面版里的插件起不来。
$Listener = Get-NetTCPConnection -LocalPort 7788 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
if ($Listener) {
  $Owner = Get-Process -Id $Listener.OwningProcess -ErrorAction SilentlyContinue
  $OwnerName = if ($Owner) { "$($Owner.ProcessName) (PID $($Owner.Id))" } else { "PID $($Listener.OwningProcess)" }
  if (-not $Force) {
    Fail "端口 7788 被 $OwnerName 占用，多半是旧方式启动的 dsh web。请先结束它（以及启动它的计划任务），或加 -Force。"
  }
  Say "结束占用 7788 端口的 $OwnerName"
  Stop-Process -Id $Listener.OwningProcess -Force
  Start-Sleep -Seconds 2
}

# ---- 安装 -------------------------------------------------------------------
$ProfileJson = Get-Content $ProfileManifest -Raw | ConvertFrom-Json
$Existing = $null
if ($ProfileJson.dependencies -and ($ProfileJson.dependencies.PSObject.Properties.Name -contains 'dsh-bridge')) {
  $Existing = $ProfileJson.dependencies.'dsh-bridge'
}
if ($Existing) {
  Say "已安装：dsh-bridge -> $Existing，先移除再重新安装"
  & $Dsh plugin --profile desktop remove dsh-bridge
  if ($LASTEXITCODE -ne 0) { Fail "移除旧插件失败（退出码 $LASTEXITCODE）" }
}
# 0.5.0 起插件自己管理 frpc：删除旧版注册的开机任务，避免两个 frpc 抢同一个通道。
if (Get-ScheduledTask -TaskName 'PangxiaopangFrpc' -ErrorAction SilentlyContinue) {
  Stop-ScheduledTask -TaskName 'PangxiaopangFrpc' -ErrorAction SilentlyContinue
  Unregister-ScheduledTask -TaskName 'PangxiaopangFrpc' -Confirm:$false -ErrorAction SilentlyContinue
  Get-Process frpc -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
  Say '已删除旧的 frpc 计划任务（改由插件随桌面版启动）'
}
Say '安装到桌面版 profile'
& $Dsh plugin --profile desktop add $PluginDir
if ($LASTEXITCODE -ne 0) { Fail "安装失败（退出码 $LASTEXITCODE）" }

$ProfileJson = Get-Content $ProfileManifest -Raw | ConvertFrom-Json
$Linked = $ProfileJson.dependencies.'dsh-bridge'
$Enabled = @($ProfileJson.dsh.profile.bundles) -contains 'dsh-bridge'
if (-not $Linked) { Fail 'profile 里没有出现 dsh-bridge，安装没有生效' }
Say "profile 已记录：dsh-bridge -> $Linked（启用：$Enabled）"
if (-not $Enabled) { Warn '插件已安装但未启用：打开桌面版 → 侧栏「插件」→ dsh-bridge，打开开关。' }

$TokenFile = Join-Path $env:USERPROFILE '.dsh-bridge\token'
if (-not (Test-Path $TokenFile)) {
  Warn "没有 $TokenFile：插件首次启动会生成新 token，与服务器配对串里的不一致。请重新运行 setup-frpc.ps1 写入 token。"
}

# ---- 启动并验证 -------------------------------------------------------------
if ($Start -and $AppExe) {
  Say "启动 $($AppExe.Name)"
  Start-Process -FilePath $AppExe.FullName
  $Health = $null
  for ($i = 0; $i -lt 90 -and -not $Health; $i++) {
    Start-Sleep -Seconds 2
    try { $Health = Invoke-RestMethod -Uri 'http://127.0.0.1:7788/v1/health' -TimeoutSec 2 } catch { }
  }
  if (-not $Health) { Fail '3 分钟内没有等到插件启动。打开桌面版 → 插件页，确认 dsh-bridge 已开启。' }
  Say "插件已运行：version=$($Health.version) runtime=$($Health.runtime)"
  if ($Health.runtime -ne 'desktop') { Warn '回应来自独立的 dsh 进程而不是桌面版，请结束其他 dsh 进程。' }
} else {
  Say '完成。打开 DeepSeek Harness 桌面版后执行：curl.exe http://127.0.0.1:7788/v1/health'
  Say "应返回 version=$PluginVersion、runtime=desktop。"
}
