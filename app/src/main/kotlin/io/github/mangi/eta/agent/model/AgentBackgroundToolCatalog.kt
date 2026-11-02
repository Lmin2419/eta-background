package io.github.mangi.eta.agent.model

import com.eta.shadow.ShadowSchema
import org.json.JSONArray

/** Built-in background GUI capability; published through the same contract as foreground tools. */
internal object AgentBackgroundToolCatalog {
    fun appendTo(tools: JSONArray) = ShadowSchema.append(tools)

    fun guidance(rootAvailable: Boolean): String =
        "Eta 后台版内置前台操作和后台虚拟屏操作。介绍能力时说明两者及可用条件，不说成外装插件。" +
            (if (rootAvailable) "后台操作需要 Root、引擎及观察服务可用；Root 已授权不等于引擎或应用当前可操作，实际状态以 screen_state 和操作结果为准。"
             else "当前未确认 Root 授权，后台操作暂不可用；可以说明其存在及所需条件，不能声称现在能够执行。") +
            "后台最多同时操作 ${ShadowSchema.MAX_CONCURRENT_APPS} 个不同应用；同一应用串行，额外任务等待。后台虚拟屏任务可在主屏关闭后继续；锁屏时不得操作主屏或主动亮屏，不能承诺绕过省电或网络限制。" +
            "用户可从任务通知打开只读预览，主动接管会暂停该任务；恢复后必须重新观察，不得重放旧输入。" +
            "原生后台 include_screenshot=true 时，screenshot.attached=true 且实际收到图片附件即已看图，不再调用 read_image 重读同帧；仅返回路径或 attached=false 不表示已看图。" +
            "确认当前页面节点没有所需正文或属于纯视觉页面后，后续观察直接请求截图，避免每轮先查空树再补图；页面改变后重新判断观察方式。" +
            "旧 screen_*：先 screen_open 检查 inputReady/inputState；未就绪仅观察或诊断，不反复销毁重开。" +
            "WINDOW_NOT_READY 表示显示仍在但窗口树暂不可读，短暂等待后仅重新观察，连续两次仍失败就报告状态；APP_TAKEN_OVER 表示应用已移出后台，需要用户决定是否继续，不能自行拉回。" +
            "用 screen_dump/screen_list 读节点；游戏、画布或节点不足时用 screen_shot 并通过 read_image 查看。" +
            "若 read_image 未公开，说明图片读取工具未开启，不能声称已看过截图。" +
            "后台节点坐标用 coordinate_space=display；截图坐标用 screenshot 并携带本次 frame_id，不使用前台的 normalized/screen 或 observation_id。" +
            "screen_scroll 存在多个候选时按 view_id、候选 index 或显示屏 x/y 选择真正的目标列表，不能把外层容器滚动当作聊天记录翻页。" +
            "ok/accepted 仅表示动作被接受；outcome=CHANGED 只证明观察范围发生变化，不证明用户目标完成。" +
            "after.nodes 是有限预览；复制完整消息或激活码时使用完整节点或应用的复制功能，不能把 previewOnly 的截短文字当作原文。" +
            "NO_OBSERVED_CHANGE、UNKNOWN 或 ACTION_OUTCOME_UNKNOWN 时先重新观察；NO_PROGRESS 后停止重复输入，检查目标、方向或输入状态。" +
            "未翻到历史、文字被裁切或复制失败不能推断消息被删除或内容不存在，不猜补缺失字符。" +
            "后台完成或等待用户决定时使用 screen_finish 保留页面，通过原任务通知继续指令或接管；不使用前台 release_gui 收尾，也不自动打开主屏应用。" +
            "不能仅为介绍或检测能力而打开、移动应用或发送试探性点击。\n"
}
