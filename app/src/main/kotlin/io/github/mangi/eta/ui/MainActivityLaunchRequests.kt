package io.github.mangi.eta.ui

import android.content.Intent
import android.os.Bundle
import io.github.mangi.eta.agent.voice.EtaAssistantOverlayService
import io.github.mangi.eta.ui.app.ArchivedConversationRequest
import io.github.mangi.eta.ui.voice.ACTION_SPEECH_SETTINGS

internal data class MainActivityLaunchRequests(
    val assistantConversationKey: String? = null,
    val conversationId: String? = null,
    val archivedConversation: ArchivedConversationRequest? = null,
    val speechSettings: Boolean = false,
    val terminal: Boolean = false,
) {
    fun accept(intent: Intent?): MainActivityLaunchRequests = when (intent?.action) {
        MainActivity.ACTION_OPEN_TERMINAL -> copy(terminal = true)
        ACTION_SPEECH_SETTINGS -> copy(speechSettings = true)
        MainActivity.ACTION_OPEN_CONVERSATION_ID -> copy(
            conversationId = intent.nonBlankExtra(MainActivity.EXTRA_CONVERSATION_ID),
        )
        MainActivity.ACTION_OPEN_ARCHIVED_CONVERSATION -> {
            val source = intent.nonBlankExtra(MainActivity.EXTRA_HANDOFF_SOURCE)
            val key = intent.nonBlankExtra(MainActivity.EXTRA_CONVERSATION_KEY)
            copy(
                archivedConversation = if (source != null && key != null) {
                    ArchivedConversationRequest(source, key)
                } else null,
            )
        }
        EtaAssistantOverlayService.ACTION_OPEN_CONVERSATION -> copy(
            assistantConversationKey = intent.nonBlankExtra(EtaAssistantOverlayService.EXTRA_CONVERSATION_KEY),
        )
        else -> this
    }

    fun saveTo(outState: Bundle) {
        outState.putBundle(STATE_KEY, Bundle().apply {
            putString("assistant_conversation_key", assistantConversationKey)
            putString("conversation_id", conversationId)
            putString("archive_source", archivedConversation?.source)
            putString("archive_conversation_key", archivedConversation?.conversationKey)
            putBoolean("speech_settings", speechSettings)
            putBoolean("terminal", terminal)
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
            return MainActivityLaunchRequests(
                assistantConversationKey = saved.getString("assistant_conversation_key"),
                conversationId = saved.getString("conversation_id"),
                archivedConversation = if (source != null && key != null) {
                    ArchivedConversationRequest(source, key)
                } else null,
                speechSettings = saved.getBoolean("speech_settings"),
                terminal = saved.getBoolean("terminal"),
            )
        }
    }
}

private fun Intent.nonBlankExtra(key: String): String? = getStringExtra(key)?.takeIf(String::isNotBlank)
