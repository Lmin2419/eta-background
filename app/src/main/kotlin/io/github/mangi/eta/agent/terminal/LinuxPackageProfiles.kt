package io.github.mangi.eta.agent.terminal

import android.content.Context
import io.github.mangi.eta.core.AndroidAgentLogger
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

internal enum class PackageProfileInstallStage {
    CHECKING,
    DOWNLOADING,
    INSTALLING,
    COMPLETE,
}

internal enum class PackageProfileInstallStep {
    PRECHECK,
    SYSTEM_PACKAGES,
    PROFILE_SETUP,
    BROWSER_DOWNLOAD,
    ACTIVATE_MARKER,
}

internal data class PackageProfileInstallProgress(
    val stage: PackageProfileInstallStage,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
)

internal sealed interface PackageProfileInstallResult {
    data object AlreadyReady : PackageProfileInstallResult
    data object EnvironmentNotReady : PackageProfileInstallResult

    /** 依赖的 profile 尚未安装，按依赖链先装它。 */
    data class DependencyMissing(val profileId: String) : PackageProfileInstallResult
    data object Installed : PackageProfileInstallResult
    data class Failed(
        val stage: PackageProfileInstallStage,
        val step: PackageProfileInstallStep = PackageProfileInstallStep.PRECHECK,
        val code: String = "INSTALL_FAILED",
        val exitCode: Int? = null,
        val timedOut: Boolean = false,
        val elapsedMs: Long? = null,
    ) : PackageProfileInstallResult
}

internal data class LinuxPackageSpec(
    val packages: List<String> = emptyList(),
    val managedTool: ManagedLinuxTool? = null,
    val setupScript: String? = null,
)

internal data class LinuxPackageProfile(
    val id: String,
    val markerName: String,
    val revision: Int,
    val specs: Map<LinuxDistribution, LinuxPackageSpec>,
    /** 安装前必须就绪的前置 profile。 */
    val dependsOn: LinuxPackageProfile? = null,
) {
    fun spec(distribution: LinuxDistribution): LinuxPackageSpec = requireNotNull(specs[distribution])
    fun supports(distribution: LinuxDistribution): Boolean = distribution in specs
}

internal object LinuxPackageProfiles {
    val PYTHON = LinuxPackageProfile(
        id = "python",
        markerName = AlpineEnvironmentPaths.PYTHON_TOOLS_MARKER,
        revision = AlpineEnvironmentPaths.PYTHON_TOOLS_REVISION,
        specs = mapOf(
            LinuxDistribution.ALPINE to LinuxPackageSpec(
                managedTool = ManagedLinuxTool.UV,
                setupScript = """
                    UV_PYTHON_INSTALL_DIR=/opt/eta/python UV_PYTHON_BIN_DIR=/usr/local/bin UV_PYTHON_INSTALL_BIN=1 uv python install --default --force
                """.trimIndent(),
            ),
            LinuxDistribution.DEBIAN to LinuxPackageSpec(
                managedTool = ManagedLinuxTool.UV,
                setupScript = """
                    UV_PYTHON_INSTALL_DIR=/opt/eta/python UV_PYTHON_BIN_DIR=/usr/local/bin UV_PYTHON_INSTALL_BIN=1 uv python install --default --force
                """.trimIndent(),
            ),
        ),
    )
    val NODE = LinuxPackageProfile(
        id = "node",
        markerName = AlpineEnvironmentPaths.NODE_TOOLS_MARKER,
        revision = AlpineEnvironmentPaths.NODE_TOOLS_REVISION,
        specs = mapOf(
            LinuxDistribution.ALPINE to LinuxPackageSpec(
                packages = listOf("nodejs-current", "npm"),
            ),
            LinuxDistribution.DEBIAN to LinuxPackageSpec(
                // Node 官方 arm64 二进制链接 libatomic.so.1，归档安装不含系统依赖，需补装。
                packages = listOf("libatomic1"),
                managedTool = ManagedLinuxTool.NODE,
            ),
        ),
    )
    val SSH = LinuxPackageProfile(
        id = "ssh",
        markerName = AlpineEnvironmentPaths.SSH_TOOLS_MARKER,
        revision = AlpineEnvironmentPaths.SSH_TOOLS_REVISION,
        specs = mapOf(
            LinuxDistribution.ALPINE to LinuxPackageSpec(
                packages = listOf("openssh"),
                setupScript = "ssh-keygen -A >/dev/null 2>&1 || true",
            ),
            LinuxDistribution.DEBIAN to LinuxPackageSpec(
                packages = listOf("openssh-client", "openssh-server"),
                setupScript = "ssh-keygen -A >/dev/null 2>&1 || true",
            ),
        ),
    )

    /**
     * Kimi Code 使用 npm 分发，运行在 Node profile 之上；可选原生扩展由 npm 按平台安装。
     * 始终安装最新正式版（升级重装即可）；--prefix /usr/local 让 kimi 进入 PATH 首位，
     * 与 Node 归档自身的 prefix 无关。国内镜像优先，官方 registry 兜底。
     */
    private const val KIMI_INSTALL_SCRIPT =
        "npm install -g --prefix /usr/local --registry=https://registry.npmmirror.com " +
            "@moonshot-ai/kimi-code@latest || " +
            "npm install -g --prefix /usr/local @moonshot-ai/kimi-code@latest"

    val KIMI = LinuxPackageProfile(
        id = "kimi",
        markerName = AlpineEnvironmentPaths.KIMI_TOOLS_MARKER,
        revision = AlpineEnvironmentPaths.KIMI_TOOLS_REVISION,
        dependsOn = NODE,
        specs = mapOf(
            LinuxDistribution.ALPINE to LinuxPackageSpec(setupScript = KIMI_INSTALL_SCRIPT),
            LinuxDistribution.DEBIAN to LinuxPackageSpec(setupScript = KIMI_INSTALL_SCRIPT),
        ),
    )
    /** Optional reader dependencies, installed explicitly; no Chromium download here. */
    val WEB_HTTP = LinuxPackageProfile(
        id = "web_http",
        markerName = ".eta-web-http-ready",
        revision = 1,
        dependsOn = PYTHON,
        specs = mapOf(
            // curl_cffi/Patchright wheels are glibc-based. Do not attempt a musl
            // install and call an import failure a supported Alpine runtime.
            LinuxDistribution.DEBIAN to LinuxPackageSpec(setupScript = """
                rm -rf /opt/eta/web-python.installing
                uv venv --relocatable --python 3.12 /opt/eta/web-python.installing
                uv pip install --python /opt/eta/web-python.installing/bin/python \
                  'scrapling[fetchers,rag]==0.5' 'curl_cffi==0.17.0' \
                  'patchright==1.64.4' 'playwright==1.64.1'
                /opt/eta/web-python.installing/bin/python -c 'from scrapling.fetchers import FetcherSession; import importlib.metadata; assert importlib.metadata.version("scrapling") == "0.5"'
                rm -rf /opt/eta/web-python.previous
                if [ -d /opt/eta/web-python ]; then mv /opt/eta/web-python /opt/eta/web-python.previous; fi
                if ! mv /opt/eta/web-python.installing /opt/eta/web-python; then
                  if [ -d /opt/eta/web-python.previous ]; then mv /opt/eta/web-python.previous /opt/eta/web-python; fi
                  exit 71
                fi
                rm -rf /opt/eta/web-python.previous
            """.trimIndent()),
        ),
    )

    /** Download presence is not browser readiness: runtime must pass a sandboxed launch probe. */
    val WEB_BROWSER = LinuxPackageProfile(
        id = "web_browser",
        markerName = ".eta-web-browser-ready",
        revision = 1,
        dependsOn = WEB_HTTP,
        specs = mapOf(
            LinuxDistribution.DEBIAN to LinuxPackageSpec(
                packages = listOf(
                    "libnss3", "libatk1.0-0t64", "libatk-bridge2.0-0t64", "libcups2t64",
                    "libdrm2", "libxkbcommon0", "libxcomposite1", "libxdamage1", "libxfixes3",
                    "libxrandr2", "libgbm1", "libpango-1.0-0", "libcairo2", "libasound2t64",
                ),
                setupScript = """
                    PLAYWRIGHT_BROWSERS_PATH=/opt/eta/web-browsers \
                    PLAYWRIGHT_DOWNLOAD_CONNECTION_TIMEOUT=60000 \
                      /opt/eta/web-python/bin/python -m patchright install --no-shell chromium
                """.trimIndent(),
            ),
        ),
    )
    val ALL = listOf(PYTHON, NODE, SSH, KIMI, WEB_HTTP, WEB_BROWSER)
}

internal fun linuxPackageProfileReady(rootfs: File, profile: LinuxPackageProfile): Boolean {
    val marker = File(rootfs, profile.markerName)
    if (!marker.isFile) return false
    return marker.useLines { lines ->
        lines.any { line -> line.trim() == "profile=${profile.revision}" }
    }
}

/** 为当前选中的发行版按需安装单个工具 profile；成功后只写对应完成标记。 */
internal class LinuxPackageProfileInstaller(
    private val context: Context,
    private val distribution: LinuxDistribution,
    private val profile: LinuxPackageProfile,
) {
    private val rootfs = LinuxEnvironmentPaths.rootfsDir(context, distribution)
    private val managedToolInstaller = PinnedLinuxToolInstaller(context)

    fun isReady(): Boolean = linuxPackageProfileReady(rootfs, profile)

    suspend fun install(
        onProgress: suspend (PackageProfileInstallProgress) -> Unit = {},
    ): PackageProfileInstallResult {
        installMutex.lock()
        return try {
            installLocked(onProgress)
        } finally {
            installMutex.unlock()
        }
    }

    private suspend fun installLocked(
        onProgress: suspend (PackageProfileInstallProgress) -> Unit,
    ): PackageProfileInstallResult = withContext(Dispatchers.IO) {
        if (isReady()) return@withContext PackageProfileInstallResult.AlreadyReady
        onProgress(PackageProfileInstallProgress(PackageProfileInstallStage.CHECKING))
        if (!profile.supports(distribution)) {
            return@withContext PackageProfileInstallResult.Failed(
                PackageProfileInstallStage.CHECKING, code = "UNSUPPORTED_DISTRIBUTION",
            )
        }
        if (!LinuxEnvironmentPaths.rootfsReady(rootfs.absolutePath) ||
            !File(rootfs, AlpineEnvironmentPaths.COMMON_TOOLS_MARKER).isFile
        ) {
            return@withContext PackageProfileInstallResult.EnvironmentNotReady
        }
        profile.dependsOn?.let { dependency ->
            if (!linuxPackageProfileReady(rootfs, dependency)) {
                return@withContext PackageProfileInstallResult.DependencyMissing(dependency.id)
            }
        }

        val spec = profile.spec(distribution)
        spec.managedTool?.let { tool ->
            val installed = managedToolInstaller.install(
                tool = tool,
                distribution = distribution,
                rootfs = rootfs,
            ) { downloadedBytes, totalBytes ->
                onProgress(
                    PackageProfileInstallProgress(
                        stage = PackageProfileInstallStage.DOWNLOADING,
                        downloadedBytes = downloadedBytes,
                        totalBytes = totalBytes,
                    ),
                )
            }
            if (!installed) {
                return@withContext PackageProfileInstallResult.Failed(
                    PackageProfileInstallStage.DOWNLOADING,
                    step = PackageProfileInstallStep.PROFILE_SETUP,
                    code = "MANAGED_TOOL_DOWNLOAD_FAILED",
                )
            }
        }

        val packageHelper = when (distribution) {
            LinuxDistribution.ALPINE -> "/usr/local/bin/eta-apk"
            LinuxDistribution.DEBIAN -> "/usr/local/bin/eta-apt"
        }
        PackageProfileInstallCommands.run(profile, spec, packageHelper, onProgress) { step, command, timeout ->
            InstallerShellRunner.run(
                command = command,
                timeoutSeconds = timeout,
                environment = distribution.terminalEnvironment,
                linuxRootfsPath = rootfs.absolutePath,
                captureTail = true,
            ).also { result ->
                val code = if (result.exitCode == 0 && !result.timedOut) "OK" else PackageProfileInstallDiagnostics.code(step, result)
                AndroidAgentLogger.info(
                    "Package profile distribution=${distribution.wireName} profile=${profile.id} step=$step " +
                        "code=$code exitCode=${result.exitCode} timedOut=${result.timedOut} " +
                        "elapsedMs=${result.elapsedMs} outputTruncated=${result.outputTruncated}",
                )
            }
        }?.let { return@withContext it }

        onProgress(PackageProfileInstallProgress(PackageProfileInstallStage.COMPLETE))
        PackageProfileInstallResult.Installed
    }

    companion object {
        private val installMutex = Mutex()
    }
}
