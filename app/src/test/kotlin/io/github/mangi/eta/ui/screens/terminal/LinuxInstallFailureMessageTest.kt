package io.github.mangi.eta.ui.screens.terminal

import io.github.mangi.eta.agent.terminal.PackageProfileInstallResult
import io.github.mangi.eta.agent.terminal.PackageProfileInstallStage
import io.github.mangi.eta.agent.terminal.PackageProfileInstallStep
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "en")
class LinuxInstallFailureMessageTest {
    @Test fun downloadTimeoutExposesItsStageAndDiagnosticMetadata() {
        val message = linuxInstallFailureMessage(RuntimeEnvironment.getApplication(), "Browser", failure(
            PackageProfileInstallStep.BROWSER_DOWNLOAD, "INSTALL_TIMEOUT", -2, timedOut = true,
        ))
        assertTrue(message.contains("Browser download or extraction"))
        assertTrue(message.contains("BROWSER_DOWNLOAD / INSTALL_TIMEOUT"))
        assertTrue(message.contains("exit=-2"))
        assertTrue(message.contains("elapsed_ms=600000"))
        assertTrue(message.contains("timeout=true"))
        assertFalse(message.contains("System dependencies"))
    }

    @Test fun dependencyFailureIsNotPresentedAsADownloadFailure() {
        val message = linuxInstallFailureMessage(RuntimeEnvironment.getApplication(), "Browser", failure(
            PackageProfileInstallStep.SYSTEM_PACKAGES, "INSTALL_DEPENDENCY_UNAVAILABLE", 100,
        ))
        assertTrue(message.contains("System dependencies"))
        assertTrue(message.contains("SYSTEM_PACKAGES / INSTALL_DEPENDENCY_UNAVAILABLE"))
        assertFalse(message.contains("Browser download or extraction"))
        assertFalse(message.contains("timeout=true"))
    }

    @Test fun legacyManagedDownloadBooleanDoesNotInventANetworkCause() {
        val message = linuxInstallFailureMessage(RuntimeEnvironment.getApplication(), "Python", failure(
            PackageProfileInstallStep.PROFILE_SETUP, "MANAGED_TOOL_DOWNLOAD_FAILED", null,
        ))
        assertTrue(message.contains("cause is not yet known"))
        assertFalse(message.contains("network connection failed"))
        assertFalse(message.contains("exit="))
    }

    private fun failure(step: PackageProfileInstallStep, code: String, exit: Int?, timedOut: Boolean = false) =
        PackageProfileInstallResult.Failed(PackageProfileInstallStage.INSTALLING, step, code, exit, timedOut, 600_000)
}
