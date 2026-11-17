package io.github.mangi.eta.agent.model

import com.eta.shadow.ShadowSchema

/** Background execution uses the same public GUI tools and observation contract. */
internal object AgentBackgroundToolCatalog {
    fun guidance(): String =
        "胖小胖内置前台操作和后台虚拟屏操作，共用一套界面工具，后台需 Root 和引擎，以实际工具结果为准。" +
            "后台最多同时操作 ${ShadowSchema.MAX_CONCURRENT_APPS} 个不同应用，同一应用串行；后台虚拟屏任务可在主屏关闭后继续，锁屏时不操作主屏。" +
            "用户接管后暂停，继续前须重新观察，不重放旧输入。后台按键也用 press_key；不支持 open_uri、系统面板和 PASTE，打开应用用 launch_app。" +
            "WINDOW_NOT_READY 只重新观察，连续两次失败就报告；APP_TAKEN_OVER 等用户决定。" +
            "未翻到历史、文字被裁切或复制失败不能推断消息被删除，不猜补字符。\n"
}
