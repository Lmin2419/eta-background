package io.github.mangi.eta.agent.model

import com.eta.shadow.ShadowSchema

/** Background execution uses the same public GUI tools and observation contract. */
internal object AgentBackgroundToolCatalog {
    fun guidance(): String =
        "Eta 后台版内置前台操作和后台虚拟屏操作，通过同一套界面工具执行，不是外装插件。" +
            "后台需要 Root、引擎及观察服务可用，以实际工具结果为准，可用 observe_screen(status_only=true, execution_location=background) 只读检查；后台最多同时操作 ${ShadowSchema.MAX_CONCURRENT_APPS} 个不同应用，同一应用串行，额外任务等待。" +
            "后台虚拟屏任务可在主屏关闭后继续；锁屏时不得操作主屏或主动亮屏，不能承诺绕过省电或网络限制。" +
            "用户可从任务通知预览或接管；接管、应用移到主屏后暂停，用户明确继续后须重新观察，禁止重放旧输入或自行拉回应用。" +
            "后台按键也用 press_key（BACK/HOME/ENTER/RECENTS）；屏幕阶段结束用 release_gui 保留后台页面与原任务通知，不自动打开主屏应用。" +
            "WINDOW_NOT_READY 可短暂等待后仅重新观察，连续两次仍失败就报告；APP_TAKEN_OVER 等待用户决定。" +
            "未翻到历史、文字被裁切或复制失败不能推断消息被删除或内容不存在，不猜补缺失字符。" +
            "不能仅为介绍或检测能力而打开、移动应用或发送试探性点击。\n"
}
