package io.github.mangi.eta.agent.terminal

import java.io.ByteArrayOutputStream
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

internal enum class BrowserDownloadComponent(val label: String) { CHROME("Chrome"), FFMPEG("FFmpeg") }

/** Framing only. Overlong lines are discarded whole, never exposed as a misleading partial message. */
internal class InstallerOutputLines(
    private val onLine: (String) -> Unit,
    private val maxLineBytes: Int = 8 * 1024,
) {
    init { require(maxLineBytes > 0) }
    private val line = ByteArrayOutputStream()
    private var discarding = false

    fun append(bytes: ByteArray, count: Int = bytes.size) {
        require(count in 0..bytes.size)
        for (index in 0 until count) {
            when (val byte = bytes[index].toInt() and 0xff) {
                10, 13 -> flush()
                else -> if (!discarding) {
                    if (line.size() < maxLineBytes) line.write(byte) else { discarding = true; line.reset() }
                }
            }
        }
    }

    fun finish() = flush()

    private fun flush() {
        if (!discarding && line.size() > 0) {
            // Progress is optional; a failed observer must not stop draining and deadlock the child.
            try { onLine(line.toByteArray().decodeToString()) } catch (_: Exception) { /* ignore observer failure */ }
        }
        line.reset()
        discarding = false
    }
}

/** Parse only the pinned CLI's component headers and progress bars, not arbitrary log text or URLs. */
internal class BrowserInstallProgressParser {
    private var component: BrowserDownloadComponent? = null
    private var previous: PackageProfileInstallProgress? = null

    fun accept(rawLine: String): PackageProfileInstallProgress? {
        val line = ANSI.replace(rawLine, "").trim()
        val starting = when {
            line.startsWith("Downloading Chrome for Testing ") || line.startsWith("Downloading Chromium ") -> BrowserDownloadComponent.CHROME
            line.startsWith("Downloading FFmpeg ") -> BrowserDownloadComponent.FFMPEG
            else -> null
        }
        if (starting != null) {
            component = starting
            // Every new SDK attempt starts afresh; do not carry 90% across a retry or component change.
            return emit(PackageProfileInstallProgress(PackageProfileInstallStage.DOWNLOADING, component = starting), force = true)
        }
        if (line.startsWith("Error:") || line.startsWith("Download failed:") || line.startsWith("Failed to install browsers") ||
            line.startsWith("Failed to download ")) {
            component = null
            return emit(PackageProfileInstallProgress(PackageProfileInstallStage.DOWNLOADING))
        }
        val active = component ?: return null
        val percent = PERCENT.matchEntire(line)?.groupValues?.get(1)?.toIntOrNull()
        if (percent != null && percent in 0..100) return emit(PackageProfileInstallProgress(
            PackageProfileInstallStage.DOWNLOADING, component = active, componentPercent = percent,
        ))
        val downloaded = when (active) {
            BrowserDownloadComponent.CHROME -> line.startsWith("Chrome for Testing ") || line.startsWith("Chromium ")
            BrowserDownloadComponent.FFMPEG -> line.startsWith("FFmpeg ")
        } && " downloaded to " in line
        if (downloaded) {
            component = null
            return emit(PackageProfileInstallProgress(PackageProfileInstallStage.DOWNLOADING,
                component = active, componentDownloaded = true))
        }
        return null
    }

    private fun emit(progress: PackageProfileInstallProgress, force: Boolean = false): PackageProfileInstallProgress? {
        if (!force && progress == previous) return null
        previous = progress
        return progress
    }

    private companion object {
        val ANSI = Regex("\\u001B\\[[0-?]*[ -/]*[@-~]")
        val PERCENT = Regex("^\\|[^|]*\\|\\s*(\\d{1,3})% of \\d+(?:\\.\\d+)? (?:KiB|MiB|GiB)$")
    }
}

/** A slow/suspending UI callback never blocks the subprocess pipe; only the latest safe update is queued. */
internal object BrowserInstallProgressRelay {
    suspend fun run(
        onProgress: suspend (PackageProfileInstallProgress) -> Unit,
        runCommand: suspend ((String) -> Unit) -> InstallerCommandResult,
    ): InstallerCommandResult = coroutineScope {
        val updates = Channel<PackageProfileInstallProgress>(Channel.CONFLATED)
        val parser = BrowserInstallProgressParser()
        val reporter = launch { for (progress in updates) onProgress(progress) }
        try {
            runCommand { line -> parser.accept(line)?.let { updates.trySend(it) } }
        } finally {
            updates.close()
            // Drain before the next install step, so an old download percentage cannot overwrite it.
            reporter.join()
        }
    }
}
