package io.github.mangi.eta.agent.runtime

/**
 * 用户此刻正在看的对话（应用在前台、停在对话页）。任务在这个对话里完成时，回复已经在眼前，
 * 不再弹系统的完成横幅（状态栏里的卡片照常保留）——同一个信息不在两个地方出现。
 */
internal object ForegroundConversation {
    @Volatile
    var id: String? = null

    /** 应用在前台且停在首页或电脑会话页：别的对话的任务结束时由页面顶部的提示条告知，不再弹系统横幅。 */
    @Volatile
    var homeVisible: Boolean = false

    fun isShowing(conversationId: String?): Boolean = conversationId != null && conversationId == id
}
