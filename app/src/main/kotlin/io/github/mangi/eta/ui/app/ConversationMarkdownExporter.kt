package io.github.mangi.eta.ui.app

import io.github.mangi.eta.R
import io.github.mangi.eta.agent.model.AgentFileReferencePromptCodec
import io.github.mangi.eta.agent.runtime.RunPerformanceSummary
import io.github.mangi.eta.ui.model.AgentChatMessageUi
import io.github.mangi.eta.ui.model.AgentMessageUi
import io.github.mangi.eta.ui.model.SystemNoticeCode
import io.github.mangi.eta.ui.model.SystemNoticeMessageUi
import io.github.mangi.eta.ui.model.ThinkingMessageUi
import io.github.mangi.eta.ui.model.ToolActivityMessageUi
import io.github.mangi.eta.ui.model.ToolActivityStatusUi
import io.github.mangi.eta.ui.model.ToolSummaryMessageUi
import io.github.mangi.eta.ui.model.UserMessageUi
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 会话导出：把内存消息流投影为自持的 Markdown 文档。
 * 文案全部由调用方注入，保持可本地化、可纯 JVM 测试。
 */
internal object ConversationMarkdownExporter {

    data class Labels(
        val user: String,
        val assistant: String,
        val thinking: String,
        val toolLineFormat: String,
        val toolsLineFormat: String,
        val argumentsFormat: String,
        val resultFormat: String,
        val imagesFormat: String,
        val toolStatusRunning: String,
        val toolStatusSuccess: String,
        val toolStatusFailed: String,
        val toolStatusUnknown: String,
        val noticeStopped: String,
        val noticeEmptyResult: String,
        val noticeModelRetry: String,
        val noticeRuntimeFailed: String,
        val noticeInterrupted: String,
        val noticeContextCompaction: String = "上下文压缩",
        val noticeRunPerformance: String = "执行统计",
    ) {
        fun toolStatus(status: ToolActivityStatusUi): String = when (status) {
            ToolActivityStatusUi.Running -> toolStatusRunning
            ToolActivityStatusUi.Success -> toolStatusSuccess
            ToolActivityStatusUi.Failed -> toolStatusFailed
            ToolActivityStatusUi.Unknown -> toolStatusUnknown
        }

        fun notice(code: SystemNoticeCode): String = when (code) {
            SystemNoticeCode.Stopped -> noticeStopped
            SystemNoticeCode.EmptyResult -> noticeEmptyResult
            SystemNoticeCode.ContextCompaction -> noticeContextCompaction
            SystemNoticeCode.ModelRetry -> noticeModelRetry
            SystemNoticeCode.RuntimeFailed -> noticeRuntimeFailed
            SystemNoticeCode.Interrupted -> noticeInterrupted
            SystemNoticeCode.RunPerformance, SystemNoticeCode.Marker -> noticeRunPerformance
        }
    }

    fun export(
        title: String,
        messages: List<AgentChatMessageUi>,
        labels: Labels,
    ): String {
        val blocks = messages.mapNotNull { message -> message.toMarkdownBlock(labels) }
        return buildString {
            append("# ").append(title.replace('\n', ' ')).append('\n')
            blocks.forEach { block ->
                append('\n').append(block).append('\n')
            }
        }
    }

    /** 界面语言的导出文案（手机对话、电脑会话共用）。 */
    fun labels(context: android.content.Context): Labels {
        fun text(id: Int) = context.getString(id)
        fun notice(code: SystemNoticeCode) = text(when (code) {
            SystemNoticeCode.Stopped -> R.string.system_notice_stopped
            SystemNoticeCode.EmptyResult -> R.string.system_notice_empty_result
            SystemNoticeCode.ContextCompaction -> R.string.context_compaction
            SystemNoticeCode.ModelRetry -> R.string.system_notice_model_retry
            SystemNoticeCode.RuntimeFailed -> R.string.system_notice_runtime_failed
            SystemNoticeCode.Interrupted -> R.string.system_notice_interrupted
            SystemNoticeCode.RunPerformance, SystemNoticeCode.Marker -> R.string.system_notice_run_performance
        })
        return Labels(
            user = text(R.string.conversation_export_user),
            assistant = text(R.string.conversation_export_assistant),
            thinking = text(R.string.conversation_export_thinking),
            toolLineFormat = text(R.string.conversation_export_tool_line),
            toolsLineFormat = text(R.string.conversation_export_tools_line),
            argumentsFormat = text(R.string.conversation_export_tool_arguments),
            resultFormat = text(R.string.conversation_export_tool_result),
            imagesFormat = text(R.string.conversation_export_images),
            toolStatusRunning = text(R.string.tool_status_running),
            toolStatusSuccess = text(R.string.tool_status_success),
            toolStatusFailed = text(R.string.tool_status_failed),
            toolStatusUnknown = text(R.string.tool_status_unknown),
            noticeStopped = notice(SystemNoticeCode.Stopped),
            noticeEmptyResult = notice(SystemNoticeCode.EmptyResult),
            noticeModelRetry = notice(SystemNoticeCode.ModelRetry),
            noticeContextCompaction = notice(SystemNoticeCode.ContextCompaction),
            noticeRuntimeFailed = notice(SystemNoticeCode.RuntimeFailed),
            noticeInterrupted = notice(SystemNoticeCode.Interrupted),
            noticeRunPerformance = notice(SystemNoticeCode.RunPerformance),
        )
    }

    fun defaultFileName(
        title: String,
        fallback: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): String {
        val sanitized = title
            .replace(WHITESPACE_RUNS, " ")
            .replace(FILENAME_UNSAFE_CHARS, "")
            .trim()
            .take(MAX_FILENAME_TITLE_CHARS)
            .trim()
            .ifBlank { fallback }
        val timestamp = SimpleDateFormat(FILENAME_TIMESTAMP_PATTERN, Locale.US).format(Date(nowMillis))
        return "Eta-$sanitized-$timestamp.md"
    }

    private fun AgentChatMessageUi.toMarkdownBlock(labels: Labels): String? = when (this) {
        is UserMessageUi -> buildString {
            append("## ").append(labels.user)
            val prompt = AgentFileReferencePromptCodec.parse(content)
            if (prompt.request.isNotBlank()) {
                append("\n\n").append(prompt.request)
            }
            prompt.references.forEach { reference ->
                append("\n\n- ").append(reference.absolutePath)
            }
            if (images.isNotEmpty()) {
                // 图片在消息里是 data URL，体积达 MB 级，导出只保留数量说明。
                append("\n\n_").append(labels.imagesFormat.format(images.size)).append('_')
            }
        }

        is AgentMessageUi -> content.takeIf { it.isNotBlank() }
            ?.let { "## ${labels.assistant}\n\n$it" }

        is ThinkingMessageUi -> content.takeIf { it.isNotBlank() }
            ?.let { "<details>\n<summary>${labels.thinking}</summary>\n\n$it\n\n</details>" }

        is ToolActivityMessageUi -> buildList {
            add(labels.toolLineFormat.format(toolName, labels.toolStatus(status)))
            command?.takeIf { it.isNotBlank() }?.let(::add)
            argumentsSummary.takeIf { it.isNotBlank() }
                ?.let { add(labels.argumentsFormat.format(it)) }
            resultSummary?.takeIf { it.isNotBlank() }
                ?.let { add(labels.resultFormat.format(it)) }
        }.toBlockquote()

        is ToolSummaryMessageUi -> listOf(labels.toolsLineFormat.format(tools.joinToString(", ")))
            .toBlockquote()

        is SystemNoticeMessageUi -> buildList {
            add(labels.notice(code))
            if (code == SystemNoticeCode.RunPerformance) {
                RunPerformanceSummary.fromJson(detail)?.let { summary ->
                    add(summary.details(labels.noticeRunPerformance.any { it.code in 0x4e00..0x9fff }))
                }
            } else detail?.takeIf { it.isNotBlank() }?.let(::add)
        }.toBlockquote()

        else -> null
    }

    private fun List<String>.toBlockquote(): String =
        joinToString(separator = "\n\n")
            .lines()
            .joinToString(separator = "\n") { line -> if (line.isEmpty()) ">" else "> $line" }

    private fun String.format(vararg args: Any): String = String.format(Locale.ROOT, this, *args)

    private val FILENAME_UNSAFE_CHARS = Regex("[\\\\/:*?\"<>|\\p{C}]")
    private val WHITESPACE_RUNS = Regex("\\s+")
    private const val MAX_FILENAME_TITLE_CHARS = 40
    private const val FILENAME_TIMESTAMP_PATTERN = "yyyyMMdd-HHmm"
}
