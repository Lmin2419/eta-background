package io.github.mangi.eta.ui.model

/**
 * 模型搜索的统一匹配规则：按非字母数字切分为多个词，每个词都要在某个字段中按顺序出现。
 *
 * 字段同样去掉分隔符再比较，让 `v4flash` 能命中 `DeepSeek V4.1 Flash`，
 * 也让 `deepseek-v3` 与 `deepseek v3` 得到相同结果。
 */
internal class ModelSearchQuery(query: String) {
    private val tokens = query.lowercase().split(separators).filter(String::isNotBlank)

    val isEmpty: Boolean
        get() = tokens.isEmpty()

    fun matches(vararg fields: String): Boolean {
        if (tokens.isEmpty()) return true
        val normalized = fields.map { field -> field.lowercase().filter(Char::isLetterOrDigit) }
        return tokens.all { token -> normalized.any { field -> field.containsCharactersInOrder(token) } }
    }

    private companion object {
        val separators = Regex("""[^\p{L}\p{N}]+""")
    }
}

private fun String.containsCharactersInOrder(query: String): Boolean {
    var queryIndex = 0
    for (character in this) {
        if (character == query[queryIndex]) {
            queryIndex++
            if (queryIndex == query.length) return true
        }
    }
    return false
}
