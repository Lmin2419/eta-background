package io.github.mangi.eta.agent.context

import java.math.BigDecimal
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/** 汇总完整的有界查询结果；金额保持十进制精度，不推断退款、冲正或重复账单。 */
internal object PersonalContextBillSummary {
    const val MAX_RECORDS = 1_000
    private const val MAX_CATEGORIES = 20
    private val maximumAmount = BigDecimal("1000000000000000000")

    fun aggregate(result: DmpQueryResult.Success): JSONObject {
        if (result.rows.size > MAX_RECORDS) {
            return failure("PERSONAL_CONTEXT_AGGREGATE_LIMIT", "匹配账单超过 1000 条，请缩小时间或关键词范围；未返回部分总额")
        }
        if (result.truncatedFields.isNotEmpty()) {
            return failure("PERSONAL_CONTEXT_AGGREGATE_INCOMPLETE", "账单字段被截断，未生成汇总")
        }
        val total = Totals()
        val categories = linkedMapOf<String, Totals>()
        for (row in result.rows) {
            val amount = try {
                row["amount"]?.let(::BigDecimal)
            } catch (_: NumberFormatException) {
                null
            }
            if (amount == null || amount.scale() !in -18..8 || amount.abs() > maximumAmount) {
                return failure("PERSONAL_CONTEXT_INVALID_AMOUNT", "账单包含无法确认的金额，未生成汇总")
            }
            val category = row["category"]?.trim().orEmpty().ifEmpty { "未分类" }
            val type = row["transaction_type"]?.trim()?.lowercase(Locale.ROOT)
            val bucket = when {
                category.equals("transfer", ignoreCase = true) -> Bucket.TRANSFER
                type == PersonalContextSources.BILL_EXPENSES -> Bucket.EXPENSE
                type == PersonalContextSources.BILL_INCOME -> Bucket.INCOME
                else -> Bucket.UNCLASSIFIED
            }
            total.add(bucket, amount)
            categories.getOrPut(category) { Totals() }.add(bucket, amount)
        }
        val ordered = categories.entries.sortedWith(compareByDescending<Map.Entry<String, Totals>> { it.value.count }.thenBy { it.key })
        val remainder = Totals()
        ordered.drop(MAX_CATEGORIES).forEach { remainder.add(it.value) }
        return JSONObject()
            .put("ok", true)
            .put("currency", PersonalContextSources.BILL_CURRENCY)
            .put("amount_unit", PersonalContextSources.BILL_AMOUNT_UNIT)
            .put("matched_records", result.rows.size)
            .put("complete_for_query", true)
            .put("totals", total.json())
            .put("categories", JSONArray(ordered.take(MAX_CATEGORIES).map { it.value.json().put("category", it.key) }))
            .put("categories_truncated", ordered.size > MAX_CATEGORIES)
            .put("remaining_category_count", (ordered.size - MAX_CATEGORIES).coerceAtLeast(0))
            .put("other_categories", remainder.json())
            .put("accounting_basis", "按索引中记录的原始金额和类型汇总；转账与未知类型单列，不推断退款净额或交易状态，不代表完整银行账本")
    }

    private enum class Bucket(val key: String) {
        EXPENSE("expenses"), INCOME("income"), TRANSFER("transfers"), UNCLASSIFIED("unclassified")
    }

    private class Totals {
        var count = 0
            private set
        private val amounts = Bucket.entries.associateWith { BigDecimal.ZERO }.toMutableMap()
        private val counts = Bucket.entries.associateWith { 0 }.toMutableMap()

        fun add(bucket: Bucket, amount: BigDecimal) {
            amounts[bucket] = amounts.getValue(bucket).add(amount)
            counts[bucket] = counts.getValue(bucket) + 1
            count += 1
        }

        fun add(other: Totals) {
            Bucket.entries.forEach { bucket ->
                amounts[bucket] = amounts.getValue(bucket).add(other.amounts.getValue(bucket))
                counts[bucket] = counts.getValue(bucket) + other.counts.getValue(bucket)
            }
            count += other.count
        }

        fun json(): JSONObject = JSONObject().put("count", count).also { json ->
            Bucket.entries.forEach { bucket ->
                json.put(bucket.key, JSONObject()
                    .put("count", counts.getValue(bucket))
                    .put("amount", amounts.getValue(bucket).stripTrailingZeros().toPlainString()))
            }
        }
    }

    private fun failure(code: String, message: String) = JSONObject()
        .put("ok", false).put("code", code).put("message", message)
}
