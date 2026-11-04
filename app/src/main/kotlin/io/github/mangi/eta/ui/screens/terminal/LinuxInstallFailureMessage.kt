package io.github.mangi.eta.ui.screens.terminal

import android.content.Context
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.terminal.PackageProfileInstallResult
import io.github.mangi.eta.agent.terminal.PackageProfileInstallStep

/** Only locally classified metadata is shown; shell output never enters this message. */
internal fun linuxInstallFailureMessage(
    context: Context,
    profileTitle: String,
    failure: PackageProfileInstallResult.Failed,
): String {
    val step = context.getString(when (failure.step) {
        PackageProfileInstallStep.PRECHECK -> R.string.linux_install_step_precheck
        PackageProfileInstallStep.SYSTEM_PACKAGES -> R.string.linux_install_step_packages
        PackageProfileInstallStep.PROFILE_SETUP -> R.string.linux_install_step_setup
        PackageProfileInstallStep.BROWSER_DOWNLOAD -> R.string.linux_install_step_browser
        PackageProfileInstallStep.ACTIVATE_MARKER -> R.string.linux_install_step_marker
    })
    val reason = context.getString(when (failure.code) {
        "INSTALL_TIMEOUT" -> R.string.linux_install_reason_timeout
        "INSTALL_NETWORK_TIMEOUT" -> R.string.linux_install_reason_network_timeout
        "INSTALL_DNS_FAILED" -> R.string.linux_install_reason_dns
        "INSTALL_TLS_FAILED" -> R.string.linux_install_reason_tls
        "INSTALL_NETWORK_FAILED" -> R.string.linux_install_reason_network
        "INSTALL_DOWNLOAD_DENIED" -> R.string.linux_install_reason_denied
        "INSTALL_DOWNLOAD_RATE_LIMITED" -> R.string.linux_install_reason_rate_limit
        "INSTALL_DOWNLOAD_NOT_FOUND" -> R.string.linux_install_reason_not_found
        "INSTALL_DISK_FULL" -> R.string.linux_install_reason_space
        "PACKAGE_MANAGER_BUSY" -> R.string.linux_install_reason_busy
        "APT_REPAIR_FAILED" -> R.string.linux_install_reason_apt
        "INSTALL_DEPENDENCY_UNAVAILABLE", "PYTHON_DEPENDENCY_MISSING", "NATIVE_LIBRARY_MISSING" -> R.string.linux_install_reason_dependency
        "INSTALL_PLATFORM_UNSUPPORTED", "UNSUPPORTED_DISTRIBUTION" -> R.string.linux_install_reason_platform
        "INSTALL_LAUNCH_UNAVAILABLE", "LINUX_ENVIRONMENT_NOT_READY", "LINUX_BUSYBOX_MISSING" -> R.string.linux_install_reason_environment
        "INSTALL_PERMISSION_DENIED", "INSTALL_READ_ONLY", "INSTALL_MARKER_FAILED" -> R.string.linux_install_reason_write
        else -> R.string.linux_install_reason_unknown
    })
    val diagnostic = buildList {
        add("${failure.step.name} / ${failure.code}")
        failure.exitCode?.let { add("exit=$it") }
        failure.elapsedMs?.let { add("elapsed_ms=$it") }
        if (failure.timedOut) add("timeout=true")
    }.joinToString(" · ")
    return context.getString(R.string.linux_install_failure_details, profileTitle, step, reason, diagnostic)
}
