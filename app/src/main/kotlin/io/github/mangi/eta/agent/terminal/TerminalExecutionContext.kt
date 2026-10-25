package io.github.mangi.eta.agent.terminal

import org.json.JSONObject

/** 同一次运行固定目录和 Linux 环境；Android 默认身份根据已确认的授权选择。 */
internal data class TerminalExecutionContext(
    val linuxEnvironment: TerminalEnvironment,
    val linuxRootfsPath: String?,
    val userWorkspacePath: String = TerminalRuntime.userWorkspacePath,
) {
    init {
        require(linuxEnvironment.isLinux)
    }

    val androidWorkspacePath: String
        get() = userWorkspacePath

    val linuxBackend: LinuxExecutionBackend
        get() = LinuxEnvironmentPaths.backendOf(linuxRootfsPath)

    val linuxIdentity: String
        get() = if (linuxBackend == LinuxExecutionBackend.PROOT) "user" else "root"

    val linuxWorkspacePath: String
        get() = TerminalRuntime.LINUX_WORKSPACE_PATH

    val linuxHostWorkspacePath: String
        get() = if (linuxIdentity == "root") TerminalRuntime.ROOT_WORKSPACE_PATH else userWorkspacePath

    val linuxReady: Boolean
        get() = LinuxEnvironmentPaths.rootfsReady(linuxRootfsPath)

    fun identityFor(environment: TerminalEnvironment, rootAvailable: Boolean): String =
        if (environment.isLinux) linuxIdentity else if (rootAvailable) "root" else "user"

    fun description(rootAvailable: Boolean): String = buildString {
        append("本次运行的命令和文件工具共用默认执行上下文：environment=android，identity=")
        append(identityFor(TerminalEnvironment.ANDROID, rootAvailable))
        append("，工作区=")
        append(JSONObject.quote(androidWorkspacePath))
        append("。已授权 Root 时直接使用 Root，未确认授权时使用 App 身份。identity=user 可显式选择普通身份；工作目录不因身份变化而移动。\n")
        append("environment=linux 使用已选择的 ")
        append(linuxEnvironment.wireName)
        append("，backend=")
        append(linuxBackend.wireName)
        append("，identity=")
        append(linuxIdentity)
        append("，ready=")
        append(linuxReady)
        append("。Linux 工作区 ")
        append(JSONObject.quote(linuxWorkspacePath))
        append(" 映射宿主目录 ")
        append(JSONObject.quote(linuxHostWorkspacePath))
        append("；只有宿主目录相同时才与默认 Android 工作区共享文件。Linux 路径按 Linux 环境解释。")
    }
}
