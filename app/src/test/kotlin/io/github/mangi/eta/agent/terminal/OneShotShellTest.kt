package io.github.mangi.eta.agent.terminal

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class OneShotShellTest {
    @Test
    fun transfersInputLargerThanPipeCapacityWithoutTruncation() {
        val input = ByteArray(512 * 1024) { (it % 251).toByte() }

        val result = execute("cat", input)

        assertEquals(0, result.exitCode)
        assertArrayEquals(input, result.output)
        assertArrayEquals(byteArrayOf(), result.stderr)
    }

    @Test
    fun earlyFailureKeepsExitCodeAndStderrWhenInputPipeCloses() {
        val result = execute("printf rejected >&2; exit 44", ByteArray(512 * 1024))

        assertEquals(44, result.exitCode)
        assertEquals("rejected", result.stderr.decodeToString())
    }

    @Test
    fun earlySuccessDoesNotHideIncompleteInput() {
        val result = execute("exit 0", ByteArray(512 * 1024))

        assertEquals(SHELL_STDIN_FAILED_EXIT_CODE, result.exitCode)
        assertEquals("命令输入传输失败", result.stderr.decodeToString())
    }

    @Test
    fun commandWithoutInputCanExitImmediately() {
        assertEquals(0, execute("exit 0", null).exitCode)
    }

    @Test
    fun blockedInputKeepsTimeoutResult() {
        val result = execute("sleep 30", ByteArray(512 * 1024), timeoutSeconds = 1)

        assertEquals(-2, result.exitCode)
    }

    private fun execute(command: String, input: ByteArray?, timeoutSeconds: Long = 5): OneShotShellResult =
        runOneShotShell(
            processSupervisor = ShellProcessSupervisor(),
            identity = "user",
            command = command,
            timeoutSeconds = timeoutSeconds,
            stdin = input,
        )
}
