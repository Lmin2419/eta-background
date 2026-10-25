#!/usr/bin/env bash
# 构建前校验：etax 的 12 处接线必须全部存在于源码里。
# 上游升级后如果有哪处被冲掉 / 改名，这里直接失败，不会悄悄打出一个缺功能的包。
set -euo pipefail
cd "$(dirname "$0")/.."
K=app/src/main/kotlin/io/github/mangi/eta

fail=0
need() {  # need <文件> <必须出现的文本> <说明>
  if ! grep -qF -- "$2" "$1" 2>/dev/null; then
    echo "::error::缺少接线：$3（$1 中找不到 \`$2\`）"
    fail=1
  else
    echo "ok  $3"
  fi
}

need $K/agent/accessibility/AgentAccessibilityService.kt 'com.eta.shadow.Boot.init(this)'           '1 无障碍连接 → 启动观察者'
need $K/agent/accessibility/AgentAccessibilityService.kt 'com.eta.shadow.Boot.onEvent(event)'       '2 无障碍事件 → 观察者'
need $K/agent/accessibility/AgentAccessibilityService.kt 'com.eta.shadow.Boot.detach(this)'         '3 无障碍解绑 → 释放观察者'
need $K/agent/runtime/AgentExecutionService.kt  'com.eta.shadow.ShadowNotice.decorate('              '4 前台通知正文'
need $K/agent/runtime/AgentExecutionService.kt  'com.eta.shadow.ShadowNotice.applyStyle(builder)'    '5 前台通知多行样式'
need $K/ui/MainActivity.kt                      'com.eta.shadow.ShadowNotice.handleHandoverIntent(intent)' '6 通知点击 → 移回主屏'
need $K/agent/mcp/McpRunContext.kt              'com.eta.shadow.ShadowTools.intercept('              '7 工具执行拦截 screen_*'
need $K/agent/model/AgentLoop.kt                'com.eta.shadow.ShadowNotice.append('                '8 流式正文 → 通知栏'
need $K/agent/model/AgentModelClient.kt         'com.eta.shadow.ShadowSchema.append(tools)'          '9 工具表注入'
need $K/agent/runtime/AgentRuntimeService.kt    'com.eta.shadow.ShadowNotice.onRunStarted()'         '10 任务开始'
need $K/agent/runtime/AgentRuntimeService.kt    'com.eta.shadow.ShadowNotice.onRunFinished('         '11 任务结束（成功/出错）'
need app/build.gradle.kts                       'applicationId = "io.github.mangi.etax"'             '12 独立包名 etax'

# 包名相关常量必须跟随 applicationId，否则 etax 包里会残留指向官方包的字符串
if grep -rnE 'const val [A-Z_]+ = "io\.github\.mangi\.eta"' $K >/dev/null; then
  echo "::error::发现写死官方包名的常量（应改为 BuildConfig.APPLICATION_ID）："
  grep -rnE 'const val [A-Z_]+ = "io\.github\.mangi\.eta"' $K
  fail=1
fi

[ "$fail" = 0 ] && echo "接线校验通过" || { echo "接线校验失败"; exit 1; }
