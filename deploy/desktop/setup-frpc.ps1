# 电脑端（Windows）：安装 frpc，把 dsh-bridge(127.0.0.1:7788) 映射到中转服务器，并写入配对 token。
#
#   powershell -ExecutionPolicy Bypass -File setup-frpc.ps1 'pxd://服务器IP:7000?frp=…&token=…&port=17788'
#
# 安装到 %USERPROFILE%\.dsh-bridge\，并注册一个登录后自动运行的计划任务。
param([Parameter(Mandatory = $true, Position = 0)][string]$Code)
$ErrorActionPreference = 'Stop'

$FrpVersion = '0.72.0'
$Sha = @{
  'windows_amd64' = '5197c1b69564089cdc229fc1260a0e25db1e722b1b7db45fe1542c3c43edf9ae'
  'windows_arm64' = 'e4b83b66c4045971dd9a47cdef16dd02af471aaa6667dd303e63b1a669162927'
}
$HomeDir = if ($env:DSH_BRIDGE_HOME) { $env:DSH_BRIDGE_HOME } else { Join-Path $env:USERPROFILE '.dsh-bridge' }
$LocalPort = if ($env:BRIDGE_LOCAL_PORT) { $env:BRIDGE_LOCAL_PORT } else { '7788' }

function Say($m) { Write-Host "==> $m" -ForegroundColor Cyan }

if (-not $Code.StartsWith('pxd://')) { throw "配对串应以 pxd:// 开头（由服务器部署脚本打印）" }
$uri = [Uri]($Code -replace '^pxd://', 'http://')
$query = @{}
foreach ($pair in $uri.Query.TrimStart('?').Split('&')) {
  $kv = $pair.Split('=', 2)
  if ($kv.Count -eq 2) { $query[$kv[0]] = $kv[1] }
}
$Server = $uri.Host
$ServerPort = $uri.Port
$FrpToken = $query['frp']
$BridgeToken = $query['token']
$RemotePort = if ($query['port']) { $query['port'] } else { '17788' }
if (-not $FrpToken -or -not $BridgeToken) { throw '配对串不完整' }

$Arch = if ($env:PROCESSOR_ARCHITECTURE -eq 'ARM64') { 'arm64' } else { 'amd64' }
New-Item -ItemType Directory -Force -Path (Join-Path $HomeDir 'bin') | Out-Null
$Frpc = Join-Path $HomeDir 'bin\frpc.exe'

$installed = $false
if (Test-Path $Frpc) { $installed = ((& $Frpc --version) -eq $FrpVersion) }
if ($installed) {
  Say "frpc $FrpVersion 已安装"
} else {
  $name = "frp_${FrpVersion}_windows_$Arch"
  $want = $Sha["windows_$Arch"]
  $tmp = Join-Path $env:TEMP "pxp-frp-$([guid]::NewGuid())"
  New-Item -ItemType Directory -Path $tmp | Out-Null
  $zip = Join-Path $tmp "$name.zip"
  $official = "https://github.com/fatedier/frp/releases/download/v$FrpVersion/$name.zip"
  $urls = @()
  if ($env:FRP_MIRROR) { $urls += "$($env:FRP_MIRROR.TrimEnd('/'))/$official" }
  $urls += $official, "https://ghfast.top/$official", "https://gh-proxy.com/$official"
  $ok = $false
  [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
  foreach ($url in $urls) {
    try {
      Say "下载 frpc：$url"
      Invoke-WebRequest -Uri $url -OutFile $zip -UseBasicParsing -TimeoutSec 300
      if ((Get-FileHash $zip -Algorithm SHA256).Hash.ToLower() -eq $want) { $ok = $true; break }
      Say '校验不符，换下一个地址'
    } catch { Say "失败：$($_.Exception.Message)" }
  }
  if (-not $ok) { throw "frpc 下载失败，可手动下载 $name.zip 解压后把 frpc.exe 放到 $Frpc" }
  Expand-Archive -Path $zip -DestinationPath $tmp -Force
  Copy-Item (Join-Path $tmp "$name\frpc.exe") $Frpc -Force
  Remove-Item $tmp -Recurse -Force
  Say "frpc 已安装到 $Frpc"
}

$config = @"
# 由 setup-frpc.ps1 生成
serverAddr = "$Server"
serverPort = $ServerPort
loginFailExit = false
auth.method = "token"
auth.token = "$FrpToken"
transport.tls.enable = true
transport.poolCount = 1
transport.heartbeatInterval = 10
transport.heartbeatTimeout = 30
log.to = "$($HomeDir -replace '\\', '/')/frpc.log"
log.level = "info"
log.maxDays = 3

[[proxies]]
name = "dsh-bridge"
type = "tcp"
localIP = "127.0.0.1"
localPort = $LocalPort
remotePort = $RemotePort
"@
$configPath = Join-Path $HomeDir 'frpc.toml'
[IO.File]::WriteAllText($configPath, $config, (New-Object Text.UTF8Encoding $false))
[IO.File]::WriteAllText((Join-Path $HomeDir 'token'), "$BridgeToken`n", (New-Object Text.UTF8Encoding $false))
& $Frpc verify -c $configPath | Out-Null
Say "已写入 $configPath 与 dsh-bridge token"

$taskName = 'PangxiaopangFrpc'
$action = New-ScheduledTaskAction -Execute $Frpc -Argument "-c `"$configPath`""
$trigger = New-ScheduledTaskTrigger -AtLogOn -User $env:USERNAME
$settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -RestartCount 999 -RestartInterval (New-TimeSpan -Minutes 1) -ExecutionTimeLimit ([TimeSpan]::Zero)
Unregister-ScheduledTask -TaskName $taskName -Confirm:$false -ErrorAction SilentlyContinue
Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger -Settings $settings -Description 'frpc for pangxiaopang desktop bridge' | Out-Null
Start-ScheduledTask -TaskName $taskName
Say "已注册计划任务 $taskName（登录后自动运行，日志：$HomeDir\frpc.log）"

Write-Host ''
Write-Host '完成。接下来：'
Write-Host '  1. 重启 DeepSeek Harness（dsh web），让 dsh-bridge 读取新的 token'
Write-Host "  2. 检查：浏览器打开 http://127.0.0.1:$LocalPort/v1/health 应返回 {`"ok`":true,...}"
Write-Host '  3. 手机上粘贴服务器脚本打印的 pxp:// 配对码'
