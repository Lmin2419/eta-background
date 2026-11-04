package io.github.mangi.eta.agent.terminal

import java.util.concurrent.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class BrowserInstallProgressTest {
    @Test fun chunkedCrLfAndAnsiLinesReportOnlyRealComponentPercentages() {
        val parser = BrowserInstallProgressParser()
        val updates = mutableListOf<PackageProfileInstallProgress>()
        val lines = InstallerOutputLines(onLine = { parser.accept(it)?.let(updates::add) })
        val fixture = "Downloading Chrome for Testing 156.0 (playwright chromium v1248)\u001b[2m from https://user:secret@private.test/browser.zip?token=private\u001b[22m\r\n" +
            "|        |   0% of 190.5 MiB\r" +
            "|■■      |  10% of 190.5 MiB\n" +
            "|■■      |  10% of 190.5 MiB\n" +
            "|■■■■■■■■| 100% of 190.5 MiB"
        fixture.toByteArray().asList().chunked(7).forEach { lines.append(it.toByteArray()) }
        lines.finish()
        assertEquals(listOf(null, 0, 10, 100), updates.map { it.componentPercent })
        assertTrue(updates.all { it.component == BrowserDownloadComponent.CHROME && it.stage == PackageProfileInstallStage.DOWNLOADING })
        assertTrue(updates.all { it.downloadedBytes == 0L && it.totalBytes == 0L && !it.componentDownloaded })
        for (secret in listOf("secret", "private.test", "token", "browser.zip")) assertFalse(updates.toString().contains(secret))
    }

    @Test fun retryAndComponentChangeResetProgressRatherThanCarryPreviousPercentage() {
        val parser = BrowserInstallProgressParser()
        parser.accept(chromeHeader)
        assertEquals(90, parser.accept("|xxxx| 90% of 190.5 MiB")?.componentPercent)
        assertNull(parser.accept(chromeHeader)?.componentPercent)
        assertEquals(0, parser.accept("|| 0% of 190.5 MiB")?.componentPercent)
        val ffmpeg = parser.accept("Downloading FFmpeg (playwright ffmpeg v1013) from https://private.test")
        assertEquals(BrowserDownloadComponent.FFMPEG, ffmpeg?.component)
        assertNull(ffmpeg?.componentPercent)
        assertEquals(20, parser.accept("|xx| 20% of 1.9 MiB")?.componentPercent)
    }

    @Test fun componentCompletionIsNotOverallInstallationCompletionAndErrorsClearPercent() {
        val parser = BrowserInstallProgressParser()
        parser.accept(chromeHeader)
        val full = parser.accept("|full| 100% of 190.5 MiB")!!
        assertFalse(full.componentDownloaded)
        assertEquals(PackageProfileInstallStage.DOWNLOADING, full.stage)
        val extracted = parser.accept("Chrome for Testing 156.0 (playwright chromium v1248) downloaded to /private/path")!!
        assertTrue(extracted.componentDownloaded)
        assertEquals(PackageProfileInstallStage.DOWNLOADING, extracted.stage)
        assertNull(extracted.componentPercent)
        assertNull(parser.accept("|old| 10% of 190.5 MiB"))
        val failed = parser.accept("Error: Download failed: secret")!!
        assertNull(failed.component)
        assertNull(failed.componentPercent)
        assertFalse(failed.componentDownloaded)
        assertNull(parser.accept("|old| 100% of 190.5 MiB"))
    }

    @Test fun malformedUnknownOrUnboundProgressIsIgnored() {
        val parser = BrowserInstallProgressParser()
        assertNull(parser.accept("|xx| 20% of 190.5 MiB"))
        assertNull(parser.accept("Downloading UnknownComponent from https://private.test"))
        parser.accept(chromeHeader)
        for (line in listOf("business 100% of 2 MiB", "|xx| 120% of 190.5 MiB", "|xx| -1% of 190.5 MiB",
            "|xx| 50% of private", "|xx| 50% of 190.5 MiB secret")) assertNull(parser.accept(line))
    }

    @Test fun overlongLinesAreDroppedWholeAndObserverExceptionsDoNotStopDraining() {
        val lines = mutableListOf<String>()
        val framer = InstallerOutputLines({ line ->
            if (line == "throws") error("observer")
            lines += line
        }, maxLineBytes = 8)
        framer.append("oversized-line\nthrows\r\nok\n".toByteArray())
        framer.append("tail".toByteArray())
        framer.finish()
        assertEquals(listOf("ok", "tail"), lines)
    }

    @Test fun slowUiCallbackDoesNotBlockOutputAndDrainsBeforeNextStage() = runBlocking {
        val uiEntered = CompletableDeferred<Unit>()
        val releaseUi = CompletableDeferred<Unit>()
        val commandReturned = CompletableDeferred<Unit>()
        val reported = mutableListOf<PackageProfileInstallProgress>()
        val work = async {
            BrowserInstallProgressRelay.run({ progress ->
                uiEntered.complete(Unit)
                releaseUi.await()
                reported += progress
            }) { output ->
                output(chromeHeader)
                yield()
                uiEntered.await()
                repeat(100) { output("|bar| ${it + 1}% of 190.5 MiB") }
                commandReturned.complete(Unit)
                InstallerCommandResult(0, "")
            }
        }
        withTimeout(2_000) { commandReturned.await() }
        assertFalse(work.isCompleted)
        releaseUi.complete(Unit)
        assertEquals(0, withTimeout(2_000) { work.await() }.exitCode)
        assertEquals(100, reported.last().componentPercent)
        assertTrue(reported.size <= 2)
    }

    @Test fun cancellationStaysCancellationAndDoesNotCreateCompletionProgress() {
        val reported = mutableListOf<PackageProfileInstallProgress>()
        assertThrows(CancellationException::class.java) {
            runBlocking {
                BrowserInstallProgressRelay.run({ reported += it }) { output ->
                    output(chromeHeader)
                    output("|bar| 50% of 190.5 MiB")
                    yield()
                    throw CancellationException("cancel requested")
                }
            }
        }
        assertTrue(reported.none { it.stage == PackageProfileInstallStage.COMPLETE || it.componentDownloaded })
    }

    private val chromeHeader = "Downloading Chrome for Testing 156.0 (playwright chromium v1248) from https://private.test/chrome.zip"
}
