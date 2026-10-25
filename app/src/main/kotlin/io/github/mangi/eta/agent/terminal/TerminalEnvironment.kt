package io.github.mangi.eta.agent.terminal

internal const val SELECTED_LINUX_WIRE_NAME = "linux"

/** Eta 支持的 Linux 用户态发行版。内核仍由 Android 提供，发行版只替换 rootfs。 */
internal enum class LinuxDistribution(val wireName: String) {
    ALPINE("alpine"),
    DEBIAN("debian"),
}

internal enum class TerminalEnvironment(
    val wireName: String,
    val linuxDistribution: LinuxDistribution? = null,
) {
    ANDROID("android"),
    ALPINE("alpine", LinuxDistribution.ALPINE),
    DEBIAN("debian", LinuxDistribution.DEBIAN),
}

internal val TerminalEnvironment.isLinux: Boolean
    get() = linuxDistribution != null

internal val LinuxDistribution.terminalEnvironment: TerminalEnvironment
    get() = when (this) {
        LinuxDistribution.ALPINE -> TerminalEnvironment.ALPINE
        LinuxDistribution.DEBIAN -> TerminalEnvironment.DEBIAN
    }
