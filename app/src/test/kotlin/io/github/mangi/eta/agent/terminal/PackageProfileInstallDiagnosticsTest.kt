package io.github.mangi.eta.agent.terminal

import java.util.concurrent.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PackageProfileInstallDiagnosticsTest {
    @Test fun lateFailureAfterMoreThan64KiBIsRetainedWithoutReturningPrivateOutput() {
        val buffer = InstallerOutputBuffer(captureTail = true)
        repeat(20) { buffer.append("download progress ".repeat(500).toByteArray()) }
        buffer.append("\nNo space left on device: https://user:password@download.test/private?token=secret\n".toByteArray())
        assertTrue(buffer.truncated())
        assertTrue(buffer.text().length <= 64 * 1024)
        val failure = PackageProfileInstallDiagnostics.failure(PackageProfileInstallStep.BROWSER_DOWNLOAD,
            InstallerCommandResult(1, buffer.text(), elapsedMs = 42_000))
        assertEquals("INSTALL_DISK_FULL", failure.code)
        assertEquals(PackageProfileInstallStage.DOWNLOADING, failure.stage)
        assertEquals(42_000L, failure.elapsedMs)
        for (secret in listOf("password", "download.test", "token", "secret")) assertFalse(failure.toString().contains(secret))
    }

    @Test fun numericExitCodesAreStageAwareAnd127IsNotAssumedToBeAMissingLibrary() {
        assertEquals("APT_REPAIR_FAILED", code(PackageProfileInstallStep.SYSTEM_PACKAGES, 72))
        assertEquals("INSTALL_FAILED", code(PackageProfileInstallStep.BROWSER_DOWNLOAD, 72))
        assertEquals("INSTALL_MARKER_FAILED", code(PackageProfileInstallStep.ACTIVATE_MARKER, 71))
        assertEquals("INSTALL_FAILED", code(PackageProfileInstallStep.BROWSER_DOWNLOAD, 71))
        assertEquals("INSTALL_FAILED", code(PackageProfileInstallStep.BROWSER_DOWNLOAD, 127))
        assertEquals("LINUX_BUSYBOX_MISSING", code(PackageProfileInstallStep.BROWSER_DOWNLOAD, 127, "ETA_LINUX_BUSYBOX_MISSING"))
        assertEquals("NATIVE_LIBRARY_MISSING", code(PackageProfileInstallStep.BROWSER_DOWNLOAD, 127,
            "error while loading shared libraries: libX11.so: cannot open shared object file"))
        assertEquals("INSTALL_LAUNCH_UNAVAILABLE", code(PackageProfileInstallStep.PRECHECK, -1))
    }

    @Test fun distinctKnownNetworkPackageAndFilesystemErrorsHaveFixedSafeCodes() {
        val cases = mapOf(
            "Could not get lock /var/lib/dpkg/lock" to "PACKAGE_MANAGER_BUSY",
            "E: Unable to locate package libmissing" to "INSTALL_DEPENDENCY_UNAVAILABLE",
            "ModuleNotFoundError: No module named 'patchright'" to "PYTHON_DEPENDENCY_MISSING",
            "Temporary failure resolving private.test" to "INSTALL_DNS_FAILED",
            "getaddrinfo ENOTFOUND private.test" to "INSTALL_DNS_FAILED",
            "CERTIFICATE_VERIFY_FAILED private chain" to "INSTALL_TLS_FAILED",
            "Error: unable to get local issuer certificate UNABLE_TO_GET_ISSUER_CERT_LOCALLY" to "INSTALL_TLS_FAILED",
            "request ETIMEDOUT" to "INSTALL_NETWORK_TIMEOUT",
            "Error: Request to https://download.test/private?token=secret timed out after 60000ms" to "INSTALL_NETWORK_TIMEOUT",
            "connect ECONNREFUSED private.test" to "INSTALL_NETWORK_FAILED",
            "connect ENETUNREACH 192.0.2.1:443" to "INSTALL_NETWORK_FAILED",
            "Download failed: server returned code 403 body 'secret'" to "INSTALL_DOWNLOAD_DENIED",
            "HTTP 429 secret" to "INSTALL_DOWNLOAD_RATE_LIMITED",
            "status code: 404 secret" to "INSTALL_DOWNLOAD_NOT_FOUND",
            "Read-only file system" to "INSTALL_READ_ONLY",
            "Permission denied" to "INSTALL_PERMISSION_DENIED",
        )
        cases.forEach { (evidence, expected) -> assertEquals(evidence, expected,
            code(PackageProfileInstallStep.BROWSER_DOWNLOAD, 1, evidence)) }
        assertEquals("INSTALL_FAILED", code(PackageProfileInstallStep.BROWSER_DOWNLOAD, 1,
            "https://user:secret@private.test/unrecognized-error?token=secret"))
    }

    @Test fun timeoutIsExplicitAndHasPriorityOverEarlierDownloadOutput() {
        val result = PackageProfileInstallDiagnostics.failure(PackageProfileInstallStep.BROWSER_DOWNLOAD,
            InstallerCommandResult(-2, "earlier EAI_AGAIN", elapsedMs = 600_000, timedOut = true))
        assertEquals("INSTALL_TIMEOUT", result.code)
        assertTrue(result.timedOut)
        assertEquals(-2, result.exitCode)
        assertEquals(600_000L, result.elapsedMs)
        assertEquals("INSTALL_TIMEOUT", code(PackageProfileInstallStep.BROWSER_DOWNLOAD, -2))
    }

    @Test fun failedPackageOrDownloadNeverWritesReadyMarker() = runBlocking {
        for (failedStep in listOf(PackageProfileInstallStep.SYSTEM_PACKAGES, PackageProfileInstallStep.BROWSER_DOWNLOAD)) {
            val steps = mutableListOf<PackageProfileInstallStep>()
            val stages = mutableListOf<PackageProfileInstallStage>()
            val failure = PackageProfileInstallCommands.run(LinuxPackageProfiles.WEB_BROWSER,
                LinuxPackageSpec(packages = listOf("dependency"), setupScript = "download-browser"), "/usr/local/bin/eta-apt",
                { stages += it.stage }) { step, _, _ ->
                steps += step
                InstallerCommandResult(if (step == failedStep) 1 else 0, "")
            }
            assertEquals(failedStep, failure?.step)
            assertFalse(steps.contains(PackageProfileInstallStep.ACTIVATE_MARKER))
            if (failedStep == PackageProfileInstallStep.BROWSER_DOWNLOAD) assertEquals(
                listOf(PackageProfileInstallStage.INSTALLING, PackageProfileInstallStage.DOWNLOADING), stages)
        }
    }

    @Test fun cancellationPropagatesAndCannotBeMistakenForTimeoutOrPublishMarker() {
        val cancellation = CancellationException("user cancelled")
        val steps = mutableListOf<PackageProfileInstallStep>()
        val thrown = assertThrows(CancellationException::class.java) {
            runBlocking {
                PackageProfileInstallCommands.run(LinuxPackageProfiles.WEB_BROWSER,
                    LinuxPackageSpec(setupScript = "download-browser"), "/usr/local/bin/eta-apt", {}) { step, _, _ ->
                    steps += step
                    throw cancellation
                }
            }
        }
        assertEquals("user cancelled", thrown.message)
        assertEquals(listOf(PackageProfileInstallStep.BROWSER_DOWNLOAD), steps)
    }

    @Test fun successfulSetupHasASeparateBoundedAtomicMarkerStep() = runBlocking {
        val steps = mutableListOf<PackageProfileInstallStep>()
        val failure = PackageProfileInstallCommands.run(LinuxPackageProfiles.WEB_BROWSER,
            LinuxPackageSpec(setupScript = "download-browser"), "/usr/local/bin/eta-apt", {}) { step, command, timeout ->
            steps += step
            if (step == PackageProfileInstallStep.BROWSER_DOWNLOAD) {
                assertFalse(command.contains("profile="))
                InstallerCommandResult(0, "")
            } else {
                assertEquals(30L, timeout)
                assertTrue(command.contains(".installing"))
                assertTrue(command.contains("mv -f"))
                InstallerCommandResult(71, "")
            }
        }
        assertEquals(listOf(PackageProfileInstallStep.BROWSER_DOWNLOAD, PackageProfileInstallStep.ACTIVATE_MARKER), steps)
        assertEquals("INSTALL_MARKER_FAILED", failure?.code)
    }

    private fun code(step: PackageProfileInstallStep, exit: Int, output: String = "") =
        PackageProfileInstallDiagnostics.code(step, InstallerCommandResult(exit, output))
}
