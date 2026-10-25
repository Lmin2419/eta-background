package io.github.mangi.eta.agent.voice

import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.parser.MarkdownParser

internal object SpeechText {
    fun readable(markdown: String): String {
        val root = MarkdownParser(GFMFlavourDescriptor())
            .buildMarkdownTreeFromString(markdown)
        return buildString {
            fun appendNode(node: ASTNode) {
                when (node.type) {
                    MarkdownElementTypes.CODE_FENCE,
                    MarkdownElementTypes.CODE_BLOCK,
                    MarkdownElementTypes.IMAGE,
                    MarkdownElementTypes.LINK_DEFINITION,
                    MarkdownElementTypes.HTML_BLOCK,
                    MarkdownTokenTypes.HTML_TAG,
                    MarkdownTokenTypes.HORIZONTAL_RULE,
                    MarkdownTokenTypes.ATX_HEADER,
                    MarkdownTokenTypes.SETEXT_1,
                    MarkdownTokenTypes.SETEXT_2,
                    MarkdownTokenTypes.LIST_BULLET,
                    MarkdownTokenTypes.LIST_NUMBER,
                    MarkdownTokenTypes.BLOCK_QUOTE,
                    MarkdownTokenTypes.BACKTICK,
                    MarkdownTokenTypes.EMPH,
                    GFMTokenTypes.TILDE,
                    GFMTokenTypes.TABLE_SEPARATOR,
                    GFMTokenTypes.CHECK_BOX -> Unit
                    MarkdownElementTypes.INLINE_LINK,
                    MarkdownElementTypes.FULL_REFERENCE_LINK,
                    MarkdownElementTypes.SHORT_REFERENCE_LINK -> {
                        val label = node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_TEXT }
                            ?: node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_LABEL }
                        label?.children?.filterNot { it.type == MarkdownTokenTypes.LBRACKET ||
                            it.type == MarkdownTokenTypes.RBRACKET }?.forEach(::appendNode)
                    }
                    else -> if (node.children.isEmpty()) append(markdown, node.startOffset, node.endOffset)
                        else node.children.forEach(::appendNode)
                }
                if (node.type == GFMTokenTypes.CELL) append(' ')
            }
            appendNode(root)
        }.replace(Regex("[ \t]+"), " ").replace(Regex("\n{3,}"), "\n\n").trim()
    }

    fun chunks(text: String, limit: Int = 500): List<String> {
        require(limit >= 2)
        val result = mutableListOf<String>()
        var remaining = text.trim()
        while (remaining.isNotEmpty()) {
            var end = minOf(limit, remaining.length)
            if (end < remaining.length) {
                val boundary = remaining.take(end).indexOfLast { it in "。！？.!?；;\n" }
                if (boundary >= end / 2) end = boundary + 1
                if (remaining[end - 1].isHighSurrogate()) end--
            }
            result += remaining.substring(0, end)
            remaining = remaining.substring(end).trimStart()
        }
        return result
    }
}
