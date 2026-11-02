package io.github.mangi.eta.ui

import android.content.Intent
import android.os.Bundle
import io.github.mangi.eta.agent.voice.EtaAssistantOverlayService
import io.github.mangi.eta.ui.app.ArchivedConversationRequest
import io.github.mangi.eta.ui.voice.ACTION_SPEECH_SETTINGS
import java.util.UUID

internal data class MainActivityLaunchRequests(
    val assistantConversationKey: String? = null,
    val conversationId: String? = null,
    val composeReply: Boolean = false,
    val archivedConversation: ArchivedConversationRequest? = null,
    val speechSettings: Boolean = false,
    val terminal: Boolean = false,
    val commonTask: CommonTaskLaunchRequest? = null,
) {
    fun accept(intent: Intent?): MainActivityLaunchRequests = when (intent?.action) {
        MainActivity.ACTION_OPEN_TERMINAL -> copy(terminal = true, commonTask = null)
        MainActivity.ACTION_RUN_COMMON_TASK -> {
            val id = intent.nonBlankExtra(MainActivity.EXTRA_COMMON_TASK_ID)?.takeIf { it.length <= 64 }
            val token = intent.nonBlankExtra(MainActivity.EXTRA_COMMON_TASK_TOKEN)?.takeIf { it.length in 32..64 }
            MainActivityLaunchRequests(commonTask = if (id != null && token != null) {
                CommonTaskLaunchRequest(id, token, UUID.randomUUID().toString())
            } else null)
        }
        ACTION_SPEECH_SETTINGS -> copy(speechSettings = true, commonTask = null)
        MainActivity.ACTION_OPEN_CONVERSATION_ID -> copy(
            commonTask = null,
            conversationId = intent.nonBlankExtra(MainActivity.EXTRA_CONVERSATION_ID),
            composeReply = intent.nonBlankExtra(MainActivity.EXTRA_CONVERSATION_ID) != null &&
                intent.getBooleanExtra(MainActivity.EXTRA_COMPOSE_REPLY, false),
        )
        MainActivity.ACTION_OPEN_ARCHIVED_CONVERSATION -> {
            val source = intent.nonBlankExtra(MainActivity.EXTRA_HANDOFF_SOURCE)
            val key = intent.nonBlankExtra(MainActivity.EXTRA_CONVERSATION_KEY)
            copy(
                commonTask = null,
                archivedConversation = if (source != null && key != null) {
                    ArchivedConversationRequest(source, key)
                } else null,
            )
        }
        EtaAssistantOverlayService.ACTION_OPEN_CONVERSATION -> copy(
            commonTask = null,
            assistantConversationKey = intent.nonBlankExtra(EtaAssistantOverlayService.EXTRA_CONVERSATION_KEY),
        )
        else -> this
    }

    fun consumeCommonTask(requestId: String): MainActivityLaunchRequests =
        if (commonTask?.requestId == requestId) copy(commonTask = null) else this

    fun saveTo(outState: Bundle) {
        outState.putBundle(STATE_KEY, Bundle().apply {
            putString("assistant_conversation_key", assistantConversationKey)
            putString("conversation_id", conversationId)
            putBoolean("compose_reply", composeReply)
            putString("archive_source", archivedConversation?.source)
            putString("archive_conversation_key", archivedConversation?.conversationKey)
            putBoolean("speech_settings", speechSettings)
            putBoolean("terminal", terminal)
            putString("common_task_id", commonTask?.taskId)
            putString("common_task_token", commonTask?.token)
            putString("common_task_request_id", commonTask?.requestId)
        })
    }

    companion object {
        private const val STATE_KEY = "eta_pending_launch_requests"

        fun restore(savedInstanceState: Bundle?, intent: Intent?): MainActivityLaunchRequests {
            // 系统可能保留最初的启动 Intent；空的待办状态也必须保存，防止重建后重放已消费的跳转。
            val saved = savedInstanceState?.getBundle(STATE_KEY)
                ?: return MainActivityLaunchRequests().accept(intent)
            val source = saved.getString("archive_source")
            val key = saved.getString("archive_conversation_key")
            val taskId = saved.getString("common_task_id")
            val taskToken = saved.getString("common_task_token")
            val taskRequestId = saved.getString("common_task_request_id")
            return MainActivityLaunchRequests(
                assistantConversationKey = saved.getString("assistant_conversation_key"),
                conversationId = saved.getString("conversation_id"),
                composeReply = saved.getBoolean("compose_reply"),
                archivedConversation = if (source != null && key != null) {
                    ArchivedConversationRequest(source, key)
                } else null,
                speechSettings = saved.getBoolean("speech_settings"),
                terminal = saved.getBoolean("terminal"),
                commonTask = if (taskId != null && taskToken != null && taskRequestId != null) {
                    CommonTaskLaunchRequest(taskId, taskToken, taskRequestId)
                } else null,
            )
        }
    }
}

private fun Intent.nonBlankExtra(key: String): String? = getStringExtra(key)?.takeIf(String::isNotBlank)
