package io.github.mangi.eta.agent.terminal

import java.io.File
import org.json.JSONObject

/** 同一次运行固定目录和 Linux 环境；Android 默认身份根据已确认的授权选择。 */
internal data class TerminalExecutionContext(
    val linuxEnvironment: TerminalEnvironment,
    val linuxRootfsPath: String?,
    val userWorkspacePath: String = TerminalRuntime.userWorkspacePath,
    val sharedMounts: List<SharedFolderMount> = emptyList(),
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

    val linuxCommonToolsReady: Boolean
        get() = AlpineEnvironmentPaths.commonToolsReady(linuxRootfsPath)

    val linuxInstalledProfiles: List<String>
        get() {
            if (!linuxReady || linuxRootfsPath.isNullOrBlank()) return emptyList()
            val rootfs = File(linuxRootfsPath)
            return buildList {
                LinuxPackageProfiles.ALL.forEach { profile ->
                    if (linuxPackageProfileReady(rootfs, profile)) add(profile.id)
                }
                if (linuxApkAnalysisReady(rootfs)) add(APK_ANALYSIS_PROFILE_ID)
            }
        }

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
        append("，common_tools=")
        append(linuxCommonToolsReady)
        append("，profiles=")
        append(linuxInstalledProfiles.ifEmpty { listOf("none") }.joinToString("/"))
        append("。Linux 工作区 ")
        append(JSONObject.quote(linuxWorkspacePath))
        append(" 配置映射到宿主目录 ")
        append(JSONObject.quote(linuxHostWorkspacePath))
        append("（bind mount 配置；挂载成功后才对应同一物理文件夹，实际是否可用以本次挂载和命令执行结果为准）；")
        if (linuxHostWorkspacePath == androidWorkspacePath) {
            append("与默认 Android 工作区配置为同一目录，挂载成功后两边修改直接互通。")
        } else {
            append("与默认 Android 工作区不同目录，挂载成功后 Android 侧可通过 ")
            append(JSONObject.quote(linuxHostWorkspacePath))
            append(" 访问同一文件夹。")
        }
        if (sharedMounts.isEmpty()) {
            append("当前未配置额外共享目录（/workspace/mounts 仅在配置并成功挂载后可见）；")
        } else {
            append("已配置的额外共享目录（Linux 挂载点 <-> Android 源目录；这是配置映射，挂载成功时底层为同一物理文件夹，但不是本次挂载已验证成功的证明；实际可用性以终端或文件操作结果为准）：")
            append(
                sharedMounts.joinToString("，") { mount ->
                    "${JSONObject.quote("${SharedFolderMounts.LINUX_MOUNTS_ROOT}/${mount.name}")} <-> ${JSONObject.quote(mount.sourcePath)}"
                },
            )
            append("；")
        }
        val sdcardLink = "/" + "sdcard"
        if (linuxBackend == LinuxExecutionBackend.CHROOT) {
            append("chroot 配置将宿主 \"/storage/emulated/0\"（及符号链接 ")
            append(JSONObject.quote(sdcardLink))
            append("）与 \"/data/local/tmp\" 默认挂载至 Linux 同路径，实际可见性以本次挂载结果为准。")
        } else {
            append("PRoot 在已授予所有文件访问权限且源目录可读时，会配置将宿主 \"/storage/emulated/0\" 默认挂载至 Linux 同路径；实际可见性以本次挂载结果为准。")
        }
        append("Linux 路径按 Linux 环境解释。")
    }

    private companion object {
        const val APK_ANALYSIS_PROFILE_ID = "apk_analysis"
    }
}
