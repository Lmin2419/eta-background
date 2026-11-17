package io.github.mangi.eta.agent.model

/**
 * 只读查询一起跑（etax/design/10 第三期）：名单里的工具不改任何状态，同一轮里连续出现的几个可以并行执行。
 * 界面观察和界面操作不进名单（会抢同一个屏幕会话），终端命令不进名单（不知道命令会不会写），
 * 个人数据查询不进名单（底层走 Root 数据库查询，并发安全没核实过）。拿不准的一律不进。
 */
internal object ToolConcurrency {
    val readOnly: Set<String> = setOf(
        // 文件：每次调用是独立的一次性进程或本地文件读取。
        "read_file", "stat_file", "list_directory", "glob_files", "grep_files", "read_image",
        // 网页
        "web_search", "fetch_url",
        // 记忆、技能、对话历史的只读接口
        "memory_get", "skills_list", "skills_read", "skills_read_resource", "skills_list_curated", "skills_inspect_github",
        AgentConversationToolCatalog.READ_HISTORY,
        // 应用列表查询（系统包管理接口）
        "search_apps",
    )

    /** 同时执行的上限：手机上网络和 IO 都有限，太多反而慢。 */
    const val MAX_PARALLEL = 4

    fun isReadOnly(name: String): Boolean = name in readOnly
}
