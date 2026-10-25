package io.github.mangi.eta.agent.terminal

import io.github.mangi.eta.core.AgentLogger
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Test

class RootShellTerminalControllerCancellationTest {
    private val hosts = mutableListOf<BackgroundTerminalJobs>()
    private val taskDirectories = mutableListOf<File>()
    @After fun closeBackgroundHosts() {
        hosts.forEach { it.close() }
        taskDirectories.forEach { it.deleteRecursively() }
    }

    @Test
    fun interruptAllStopsSynchronousCommandWithoutWaitingForTimeout() {
        val controller = RootShellTerminalController(NoOpLogger)
        val entered = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val result = AtomicReference<String>()
        val worker = thread(name = "terminal-cancel-test", isDaemon = true) {
            entered.countDown()
            result.set(
                controller.executeTool("terminal", JSONObject().put("action", "exec")
                    .put("command", "sleep 30")
                    .put("cwd", System.getProperty("java.io.tmpdir"))
                    .put("timeout_ms", 30_000)
                    .put("identity", "user")
                    .put("merge_stderr", false)
                )
            )
            finished.countDown()
        }

        try {
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            waitUntilProcessBlocks(worker)
            controller.interruptAll()
            assertTrue(finished.await(2, TimeUnit.SECONDS))
            assertFalse(JSONObject(result.get()).getBoolean("ok"))
        } finally {
            controller.closeAll()
            worker.join(2_000)
        }
    }

    @Test
    fun interruptAllStopsDescendantProcess() {
        val controller = RootShellTerminalController(NoOpLogger)
        val childPidFile = File.createTempFile("eta-terminal-child-", ".pid")
        val finished = CountDownLatch(1)
        val result = AtomicReference<String>()
        val worker = thread(name = "terminal-child-cancel-test", isDaemon = true) {
            result.set(
                controller.executeTool("terminal", JSONObject().put("action", "exec")
                    .put("command", "sleep 30 & echo ${'$'}! > ${shellQuote(childPidFile.absolutePath)}; wait")
                    .put("cwd", System.getProperty("java.io.tmpdir"))
                    .put("timeout_ms", 30_000)
                    .put("identity", "user")
                    .put("merge_stderr", false)
                )
            )
            finished.countDown()
        }

        try {
            val childPid = waitForChildPid(childPidFile, worker)
            assertTrue(isProcessRunning(childPid))

            controller.interruptAll()

            assertTrue(finished.await(2, TimeUnit.SECONDS))
            assertFalse(JSONObject(result.get()).getBoolean("ok"))
            assertTrue(waitUntilProcessExits(childPid))
        } finally {
            controller.closeAll()
            worker.join(2_000)
            childPidFile.delete()
        }
    }

    @Test
    fun completedBackgroundJobCleansGrandchildProcess() {
        val controller = newIsolatedGroupController()
        try {
            val started = JSONObject(
                controller.executeTool("terminal_job", JSONObject().put("action", "start")
                    .put("command", "sh -c 'sleep 30 & echo CHILD_PID=${'$'}!' &")
                    .put("cwd", System.getProperty("java.io.tmpdir"))
                    .put("timeout_ms", 30_000)
                    .put("identity", "user")
                    .put("merge_stderr", false)
                )
            )
            assertTrue(started.toString(), started.getBoolean("ok"))

            val completed = waitForAsyncResult(controller, started.getString("job_id"))
            val childPid = Regex("CHILD_PID=(\\d+)")
                .find(completed.getString("output"))
                ?.groupValues
                ?.get(1)
                ?.toLong()
                ?: error("async 输出缺少 child PID")

            assertTrue(waitUntilProcessExits(childPid))
        } finally {
            controller.closeAll()
        }
    }

    private fun waitUntilProcessBlocks(worker: Thread) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
        while (
            worker.state != Thread.State.WAITING &&
            worker.state != Thread.State.TIMED_WAITING &&
            System.nanoTime() < deadline
        ) {
            Thread.yield()
        }
    }

    private fun waitForChildPid(pidFile: File, worker: Thread): Long {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
        while (System.nanoTime() < deadline) {
            runCatching { pidFile.readText().trim().toLongOrNull() }
                .getOrNull()
                ?.let { pid -> return pid }
            if (!worker.isAlive) break
            Thread.sleep(10)
        }
        error("未观察到 sleep 子进程")
    }

    private fun waitUntilProcessExits(pid: Long): Boolean {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
        while (System.nanoTime() < deadline) {
            if (!isProcessRunning(pid)) return true
            Thread.sleep(10)
        }
        return !isProcessRunning(pid)
    }

    private fun waitForAsyncResult(
        controller: RootShellTerminalController,
        jobId: String,
    ): JSONObject {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
        do {
            val result = JSONObject(
                controller.executeTool("terminal_job", JSONObject().put("action", "read")
                    .put("job_id", jobId)
                    .put("offset_chars", 0)
                    .put("max_chars", 16_000)
                    .put("close_if_done", false)
                )
            )
            if (!result.getBoolean("running")) return result
            Thread.sleep(10)
        } while (System.nanoTime() < deadline)
        error("async job 未在预期时间内结束")
    }

    private fun isProcessRunning(pid: Long): Boolean =
        runCatching {
            val process = ProcessBuilder("ps", "-o", "stat=", "-p", pid.toString())
                .redirectErrorStream(true)
                .start()
            val state = process.inputStream.bufferedReader().use { reader -> reader.readText().trim() }
            process.waitFor(1, TimeUnit.SECONDS)
            state.isNotEmpty() && !state.startsWith("Z")
        }.getOrDefault(false)

    private fun newIsolatedGroupController(): RootShellTerminalController {
        val setsid = File.createTempFile("eta-test-setsid-", ".py").apply {
            writeText(
                """
                #!/usr/bin/python3
                import os
                import sys

                args = sys.argv[1:]
                if args and args[0] == "-w":
                    args = args[1:]
                os.setsid()
                os.execvp(args[0], args)
                """.trimIndent()
            )
            check(setExecutable(true))
            deleteOnExit()
        }
        val directory = java.nio.file.Files.createTempDirectory("eta-background-test").toFile().also { taskDirectories += it }
        val supervisor = DetachedTaskSupervisor(NoOpLogger, File(directory, "tasks.json"),
            daemonDir = File(directory, "logs").path,
            oneShotSupervisor = ShellProcessSupervisor(allowTreeFallback = false, setsidCommand = setsid.absolutePath))
        val jobs = BackgroundTerminalJobs(supervisor, NoOpLogger).also { hosts += it }
        return RootShellTerminalController(
            logger = NoOpLogger,
            detachedSupervisor = supervisor,
            backgroundJobs = jobs,
            processSupervisor = ShellProcessSupervisor(
                allowTreeFallback = false,
                setsidCommand = setsid.absolutePath,
            ),
        )
    }

    private object NoOpLogger : AgentLogger {
        override fun debug(message: () -> String) = Unit
        override fun info(message: String) = Unit
        override fun warn(message: String) = Unit
        override fun error(message: String, throwable: Throwable?) = Unit
    }
}
