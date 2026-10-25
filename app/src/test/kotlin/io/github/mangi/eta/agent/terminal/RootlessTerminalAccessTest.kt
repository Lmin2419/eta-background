package io.github.mangi.eta.agent.terminal

import io.github.mangi.eta.core.AgentLogger
import java.io.File
import java.nio.file.Files
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RootlessTerminalAccessTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun ordinaryCommandUsesAppIdentityWithoutGrant() {
        val controller = RootShellTerminalController(NoopLogger, rootAvailable = { false })
        try {
            val result = JSONObject(controller.executeTool("terminal", JSONObject()
                .put("command", "printf hello").put("cwd", temporary.root.path)))
            assertTrue(result.toString(), result.getBoolean("ok"))
            assertEquals("user", result.getString("identity"))
            assertEquals("hello", result.getString("stdout"))
        } finally { controller.closeAll() }
    }

    @Test fun explicitRootCommandIsRejectedWithoutGrant() {
        val controller = RootShellTerminalController(NoopLogger, rootAvailable = { false })
        try {
            val result = JSONObject(controller.executeTool("terminal", JSONObject()
                .put("command", "id").put("cwd", "/").put("identity", "root")))
            assertFalse(result.getBoolean("ok"))
            assertEquals("ROOT_REQUIRED", result.getString("code"))
        } finally { controller.closeAll() }
    }

    @Test fun ordinaryFileReadFollowsAccessibleLinksOutsideWorkspace() {
        val controller = RootShellTerminalController(NoopLogger, rootAvailable = { false })
        val workspace = File(TerminalRuntime.userWorkspacePath).apply { mkdirs() }
        val link = File(workspace, "test-link-${System.nanoTime()}")
        val outside = temporary.newFile("outside").apply { writeText("private") }
        try {
            Files.createSymbolicLink(link.toPath(), outside.toPath())
            val result = JSONObject(controller.fileTool("read_file", JSONObject()
                .put("path", link.path).put("max_bytes", 100)))
            assertTrue(result.toString(), result.getBoolean("ok"))
            assertEquals("private", result.getString("content"))
        } finally {
            controller.closeAll()
            Files.deleteIfExists(link.toPath())
        }
    }

    @Test fun fileToolsWriteAndReadAccessiblePathsOutsideWorkspace() {
        val controller = RootShellTerminalController(NoopLogger, rootAvailable = { false })
        val target = File(temporary.root, "notes/内存管理.md")
        try {
            val write = JSONObject(controller.fileTool("write_file", JSONObject()
                .put("path", target.path).put("content", "# 内存管理\n正文")
                .put("environment", "ANDROID").put("identity", "USER").put("unused", true)))
            assertTrue(write.toString(), write.getBoolean("ok"))
            assertEquals("# 内存管理\n正文", target.readText())
            val read = JSONObject(controller.fileTool("read_file", JSONObject()
                .put("cwd", temporary.root.path).put("path", "notes/内存管理.md")))
            assertTrue(read.toString(), read.getBoolean("ok"))
            assertEquals(target.readText(), read.getString("content"))
        } finally { controller.closeAll() }
    }

    @Test fun ordinaryReadsReportOffsetsForTheReturnedChunk() {
        val controller = RootShellTerminalController(NoopLogger, rootAvailable = { false })
        val file = File(TerminalRuntime.userWorkspacePath, "test-read-${System.nanoTime()}")
        try {
            file.parentFile!!.mkdirs()
            file.writeText("a".repeat(20_000))
            val first = JSONObject(controller.fileTool("read_file", JSONObject()
                .put("path", file.path).put("max_bytes", 200_000)))
            assertTrue(first.getBoolean("truncated"))
            assertEquals(first.getString("content").length, first.getInt("bytes_read"))
            val second = JSONObject(controller.fileTool("read_file", JSONObject()
                .put("path", file.path).put("offset_bytes", first.getInt("bytes_read"))
                .put("max_bytes", 200_000)))
            assertFalse(second.getBoolean("truncated"))
            assertEquals(20_000, first.getString("content").length + second.getString("content").length)
        } finally {
            controller.closeAll()
            file.delete()
        }
    }

    private object NoopLogger : AgentLogger {
        override fun debug(message: () -> String) = Unit
        override fun info(message: String) = Unit
        override fun warn(message: String) = Unit
        override fun error(message: String, throwable: Throwable?) = Unit
    }
}
