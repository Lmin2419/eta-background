package io.github.mangi.eta.assistant

import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** 2026111608：不再比对框架 jar 的哈希，改查 dex 里有没有长按广播名。 */
class OplusPowerFrameworkScanTest {
    @get:Rule val temp = TemporaryFolder()

    private fun jar(name: String, vararg entries: Pair<String, ByteArray>): File = temp.newFile(name).also { file ->
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (entry, bytes) -> zip.putNextEntry(ZipEntry(entry)); zip.write(bytes); zip.closeEntry() }
        }
    }

    private fun dexWith(text: String?): ByteArray {
        val filler = ByteArray(3 * (1 shl 20) + 17) { (it % 251).toByte() }
        // 广播名放在 1MB 块的边界附近，验证跨块也找得到。
        val head = filler.copyOfRange(0, (1 shl 20) - 10)
        return head + (text?.toByteArray(Charsets.US_ASCII) ?: ByteArray(0)) + filler
    }

    @Test fun findsTheBroadcastInAnyFrameworkJar() {
        val plain = jar("services.jar", "classes.dex" to dexWith(null))
        val oplus = jar("oplus-services.jar", "classes2.dex" to dexWith(OplusPowerContract.ACTION))
        assertEquals(true, OplusPowerRoot.frameworkHasRoute(listOf(plain, oplus)))
    }

    @Test fun readableDexWithoutTheBroadcastIsUnsupported() {
        val a = jar("oplus-services.jar", "classes.dex" to dexWith("oppo.intent.action.something.else"))
        val b = jar("services.jar", "classes.dex" to dexWith(null))
        assertEquals(false, OplusPowerRoot.frameworkHasRoute(listOf(a, b)))
    }

    @Test fun strippedJarsAreUnknownNotRejected() {
        val stripped = jar("oplus-services.jar", "META-INF/MANIFEST.MF" to "Manifest-Version: 1.0\n".toByteArray())
        assertNull(OplusPowerRoot.frameworkHasRoute(listOf(stripped, File(temp.root, "missing.jar"))))
    }

    @Test fun streamingSearchMatchesAcrossChunks() {
        val marker = "abcdef".toByteArray()
        val data = ByteArray((1 shl 20) - 3) + marker + ByteArray(10)
        assertTrue(OplusPowerRoot.contains(ByteArrayInputStream(data), marker))
        assertFalse(OplusPowerRoot.contains(ByteArrayInputStream(ByteArray(5000)), marker))
    }
}
