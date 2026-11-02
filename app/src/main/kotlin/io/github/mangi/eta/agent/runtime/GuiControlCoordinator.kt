package io.github.mangi.eta.agent.runtime

import java.util.ArrayDeque
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class GuiControlStatus(val wireValue: String) {
    Waiting("waiting"),
    Acquired("acquired"),
    Released("released"),
    UserPaused("user_paused");

    companion object {
        fun fromWireValue(value: String): GuiControlStatus? = entries.firstOrNull { it.wireValue == value }
    }
}

/** 手机界面只有一个操作所有者；锁只保护所有权，模型推理和工具执行不在锁内运行。 */
internal class GuiControlCoordinator {
    data class State(val ownerRunId: String? = null, val userPaused: Boolean = false)

    private val lock = ReentrantLock()
    private val changed = lock.newCondition()
    private val waiting = ArrayDeque<RunControl>()
    private var owner: RunControl? = null
    private var userPauseRequested = false
    private var pausedController: AgentRunController? = null
    private var generation = 0L
    private val mutableState = MutableStateFlow(State())
    val state: StateFlow<State> = mutableState.asStateFlow()

    fun openRun(
        runId: String,
        controller: AgentRunController,
        onStatusChanged: (GuiControlStatus) -> Unit = {},
    ): RunControl = RunControl(runId, controller, onStatusChanged).also { control ->
        control.bindCancellation(controller.register(control::close))
    }

    fun pauseForUser(runId: String): Boolean {
        val paused = lock.withLock {
            val current = owner?.takeIf { it.runId == runId && !it.closed } ?: return false
            if (userPauseRequested) return true
            userPauseRequested = true
            pausedController = current.controller
            generation++
            current.controller.pause()
            if (current.activeActions == 0) {
                mutableState.value = State(current.runId, userPaused = true)
                current
            } else {
                null
            }
        }
        paused?.notifyStatus(GuiControlStatus.UserPaused)
        return true
    }

    fun resumeForUser(): Boolean {
        val resumed = lock.withLock {
            if (!userPauseRequested) return false
            // Do not drop the GUI pause while a preview still owns manual input.
            val paused = pausedController
            if (paused != null && !paused.isCancelled && !paused.resume()) return false
            userPauseRequested = false
            generation++
            val current = owner?.takeUnless { it.closed }
            pausedController = null
            mutableState.value = State(current?.runId)
            changed.signalAll()
            current
        }
        resumed?.notifyStatus(GuiControlStatus.Acquired)
        return true
    }

    internal inner class RunControl internal constructor(
        val runId: String,
        internal val controller: AgentRunController,
        private val onStatusChanged: (GuiControlStatus) -> Unit,
    ) : AutoCloseable {
        internal var closed = false
        internal var activeActions = 0
        private var releaseRequested = false
        private var cancellationBinding: AgentRunController.ResourceBinding? = null

        internal fun bindCancellation(binding: AgentRunController.ResourceBinding) = lock.withLock {
            if (closed) binding.close() else cancellationBinding = binding
        }

        fun <T> withAction(block: (generation: Long) -> T): T {
            controller.throwIfCancelled()
            val queued = lock.withLock {
                checkOpen()
                if (owner !== this && !waiting.contains(this)) {
                    waiting.addLast(this)
                    owner != null || userPauseRequested || waiting.first() !== this
                } else {
                    false
                }
            }
            if (queued) notifyStatus(GuiControlStatus.Waiting)
            var acquired = false
            val actionGeneration = lock.withLock {
                while (true) {
                    checkOpen()
                    if (owner === this && !userPauseRequested) break
                    if (owner == null && !userPauseRequested && waiting.peekFirst() === this) {
                        waiting.removeFirst()
                        owner = this
                        generation++
                        acquired = true
                        mutableState.value = State(runId)
                        break
                    }
                    try {
                        changed.await()
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        waiting.remove(this)
                        changed.signalAll()
                        throw AgentRunCancelledException()
                    }
                }
                activeActions++
                generation
            }
            try {
                if (acquired) notifyStatus(GuiControlStatus.Acquired)
                if (controller.isCancelled) throw AgentRunCancelledException()
                return block(actionGeneration)
            } finally {
                val status = lock.withLock {
                    activeActions--
                    when {
                        activeActions != 0 -> null
                        releaseRequested || closed -> releaseLocked()
                        userPauseRequested -> {
                            mutableState.value = State(runId, userPaused = true)
                            GuiControlStatus.UserPaused
                        }
                        else -> null
                    }
                }
                status?.let(::notifyStatus)
            }
        }

        fun release() {
            val status = lock.withLock {
                waiting.remove(this)
                if (owner !== this) return
                releaseRequested = true
                generation++
                if (activeActions == 0) releaseLocked() else null
            }
            status?.let(::notifyStatus)
        }

        override fun close() {
            val status = lock.withLock {
                if (closed) return
                closed = true
                cancellationBinding?.close()
                cancellationBinding = null
                waiting.remove(this)
                changed.signalAll()
                if (owner !== this) return
                generation++
                if (activeActions == 0) releaseLocked() else null
            }
            status?.let(::notifyStatus)
        }

        private fun checkOpen() {
            if (closed || controller.isCancelled) throw AgentRunCancelledException()
        }

        private fun releaseLocked(): GuiControlStatus? {
            if (owner !== this) return null
            owner = null
            releaseRequested = false
            mutableState.value = State(userPaused = userPauseRequested)
            changed.signalAll()
            return GuiControlStatus.Released
        }

        internal fun notifyStatus(status: GuiControlStatus) = onStatusChanged(status)
    }
}
