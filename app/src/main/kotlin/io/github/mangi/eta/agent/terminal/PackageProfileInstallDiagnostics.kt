package io.github.mangi.eta.agent.terminal

import java.util.Locale

/** Safe metadata only: command output is evidence for a fixed code, never a user-facing detail. */
internal object PackageProfileInstallDiagnostics {
    fun failure(step: PackageProfileInstallStep, result: InstallerCommandResult) = PackageProfileInstallResult.Failed(
        stage = stage(step),
        step = step,
        code = code(step, result),
        exitCode = result.exitCode,
        timedOut = result.timedOut || result.exitCode == -2,
        elapsedMs = result.elapsedMs,
    )

    fun stage(step: PackageProfileInstallStep) = when (step) {
        PackageProfileInstallStep.PRECHECK -> PackageProfileInstallStage.CHECKING
        PackageProfileInstallStep.BROWSER_DOWNLOAD -> PackageProfileInstallStage.DOWNLOADING
        else -> PackageProfileInstallStage.INSTALLING
    }

    fun code(step: PackageProfileInstallStep, result: InstallerCommandResult): String {
        if (result.timedOut || result.exitCode == -2) return "INSTALL_TIMEOUT"
        if (result.exitCode == -1) return "INSTALL_LAUNCH_UNAVAILABLE"
        val evidence = result.output.lowercase(Locale.ROOT)
        return when {
            "eta_linux_busybox_missing" in evidence -> "LINUX_BUSYBOX_MISSING"
            "eta_linux_environment_not_ready" in evidence -> "LINUX_ENVIRONMENT_NOT_READY"
            "no space left on device" in evidence || "enospc" in evidence -> "INSTALL_DISK_FULL"
            "read-only file system" in evidence || "erofs" in evidence -> "INSTALL_READ_ONLY"
            "could not get lock" in evidence || "unable to acquire the dpkg frontend lock" in evidence -> "PACKAGE_MANAGER_BUSY"
            step == PackageProfileInstallStep.SYSTEM_PACKAGES && result.exitCode == DebianEnvironmentInstaller.APT_REPAIR_FAILED_EXIT_CODE -> "APT_REPAIR_FAILED"
            "unable to locate package" in evidence || "has no installation candidate" in evidence ||
                "no matching distribution found" in evidence -> "INSTALL_DEPENDENCY_UNAVAILABLE"
            "no module named" in evidence || "modulenotfounderror" in evidence -> "PYTHON_DEPENDENCY_MISSING"
            "error while loading shared libraries" in evidence || "cannot open shared object file" in evidence -> "NATIVE_LIBRARY_MISSING"
            "exec format error" in evidence || "unsupported platform" in evidence -> "INSTALL_PLATFORM_UNSUPPORTED"
            "temporary failure resolving" in evidence || "could not resolve host" in evidence ||
                "enotfound" in evidence || "eai_again" in evidence -> "INSTALL_DNS_FAILED"
            "certificate_verify_failed" in evidence || "cert_has_expired" in evidence ||
                "certificate verification failed" in evidence || "unable to verify the first certificate" in evidence ||
                "unable to get local issuer certificate" in evidence || "unable_to_verify_leaf_signature" in evidence ||
                "unable_to_get_issuer_cert_locally" in evidence || "err_tls_cert_altname_invalid" in evidence ||
                "self_signed_cert_in_chain" in evidence || "self-signed certificate" in evidence -> "INSTALL_TLS_FAILED"
            "etimedout" in evidence || "connection timed out" in evidence ||
                "read timed out" in evidence || "request timed out" in evidence ||
                "timed out after" in evidence || "und_err_connect_timeout" in evidence -> "INSTALL_NETWORK_TIMEOUT"
            "econnreset" in evidence || "econnrefused" in evidence || "enetunreach" in evidence ||
                "network is unreachable" in evidence -> "INSTALL_NETWORK_FAILED"
            HTTP_RATE_LIMIT.containsMatchIn(evidence) -> "INSTALL_DOWNLOAD_RATE_LIMITED"
            HTTP_DENIED.containsMatchIn(evidence) -> "INSTALL_DOWNLOAD_DENIED"
            HTTP_NOT_FOUND.containsMatchIn(evidence) -> "INSTALL_DOWNLOAD_NOT_FOUND"
            "permission denied" in evidence || "operation not permitted" in evidence -> "INSTALL_PERMISSION_DENIED"
            step == PackageProfileInstallStep.ACTIVATE_MARKER -> "INSTALL_MARKER_FAILED"
            else -> "INSTALL_FAILED"
        }
    }

    private val HTTP_RATE_LIMIT = Regex("(?:status(?: code)?|server returned code|http(?:/\\d(?:\\.\\d)?)?)[ :=]+429\\b")
    private val HTTP_DENIED = Regex("(?:status(?: code)?|server returned code|http(?:/\\d(?:\\.\\d)?)?)[ :=]+(?:401|403)\\b")
    private val HTTP_NOT_FOUND = Regex("(?:status(?: code)?|server returned code|http(?:/\\d(?:\\.\\d)?)?)[ :=]+404\\b")
}

/** One command per meaningful stage; no activation marker survives a failed setup. */
internal object PackageProfileInstallCommands {
    suspend fun run(
        profile: LinuxPackageProfile,
        spec: LinuxPackageSpec,
        packageHelper: String,
        onProgress: suspend (PackageProfileInstallProgress) -> Unit,
        runCommand: suspend (PackageProfileInstallStep, String, Long) -> InstallerCommandResult,
    ): PackageProfileInstallResult.Failed? {
        suspend fun execute(step: PackageProfileInstallStep, command: String, timeout: Long = 600): PackageProfileInstallResult.Failed? {
            onProgress(PackageProfileInstallProgress(PackageProfileInstallDiagnostics.stage(step)))
            // Coroutine cancellation propagates; it must not become a download failure or proceed to activation.
            val result = runCommand(step, command, timeout)
            return if (result.exitCode == 0 && !result.timedOut) null else PackageProfileInstallDiagnostics.failure(step, result)
        }
        if (spec.packages.isNotEmpty()) {
            execute(PackageProfileInstallStep.SYSTEM_PACKAGES,
                "${shellQuote(packageHelper)} install ${spec.packages.joinToString(" ", transform = ::shellQuote)}")?.let { return it }
        }
        val setup = buildString {
            spec.setupScript?.let { append(it).append('\n') }
            if (profile == LinuxPackageProfiles.KIMI) append("kimi --version >/dev/null\nkimi web --help >/dev/null\n")
        }
        if (setup.isNotBlank()) {
            val step = if (profile == LinuxPackageProfiles.WEB_BROWSER) PackageProfileInstallStep.BROWSER_DOWNLOAD
                else PackageProfileInstallStep.PROFILE_SETUP
            execute(step, "set -e\n$setup")?.let { return it }
        }
        val marker = "/${profile.markerName}"
        val temporary = "$marker.installing"
        val activate = """
            set -e
            eta_profile_marker=${shellQuote(marker)}
            eta_profile_temporary=${shellQuote(temporary)}
            trap 'rm -f "${'$'}eta_profile_temporary"' EXIT
            printf '%s\n' ${shellQuote("profile=${profile.revision}")} > "${'$'}eta_profile_temporary" || exit 71
            chmod 0644 "${'$'}eta_profile_temporary" || exit 71
            mv -f "${'$'}eta_profile_temporary" "${'$'}eta_profile_marker" || exit 71
        """.trimIndent()
        return execute(PackageProfileInstallStep.ACTIVATE_MARKER, activate, timeout = 30)
    }
}
