package io.github.mangi.eta.agent.context

import java.math.BigDecimal
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalContextBillSummaryTest {
    @Test
    fun decimalAmountsRemainExact() {
        val result = aggregate(bill("0.1"), bill("0.2"))

        assertTrue(result.getBoolean("ok"))
        assertEquals("CNY", result.getString("currency"))
        assertEquals("yuan", result.getString("amount_unit"))
        assertEquals("0.3", result.getJSONObject("totals").getJSONObject("expenses").getString("amount"))
        assertEquals(2, result.getInt("matched_records"))
    }

    @Test
    fun originalTypesAndTransferCategoryStayInSeparateBuckets() {
        val result = aggregate(
            bill("12.5", type = "expenses", category = "food"),
            bill("100", type = "income", category = "salary"),
            bill("30", type = "expenses", category = "transfer"),
            bill("40", type = "income", category = "transfer"),
            bill("2", type = "other", category = "misc"),
            bill("3", type = null, category = null),
        )
        val totals = result.getJSONObject("totals")

        assertBucket(totals, "expenses", 1, "12.5")
        assertBucket(totals, "income", 1, "100")
        assertBucket(totals, "transfers", 2, "70")
        assertBucket(totals, "unclassified", 2, "5")
        assertEquals(6, totals.getInt("count"))
        assertTrue(result.getBoolean("complete_for_query"))
    }

    @Test
    fun negativeAmountsAndRefundStatusDoNotChangeOriginalClassification() {
        val result = aggregate(
            bill("10").plus("transaction_status" to "refunded"),
            bill("-2").plus("transaction_status" to "refunded"),
            bill("-3", type = "income"),
            bill("-4", type = "refund"),
        )
        val totals = result.getJSONObject("totals")

        assertBucket(totals, "expenses", 2, "8")
        assertBucket(totals, "income", 1, "-3")
        assertBucket(totals, "unclassified", 1, "-4")
        assertFalse(result.has("net_amount"))
    }

    @Test
    fun recordLimitDoesNotReturnAPartialTotal() {
        val maximum = DmpQueryResult.Success(List(1_000) { bill("1") })
        val accepted = PersonalContextBillSummary.aggregate(maximum)
        assertTrue(accepted.getBoolean("ok"))
        assertBucket(accepted.getJSONObject("totals"), "expenses", 1_000, "1000")

        val rejected = PersonalContextBillSummary.aggregate(DmpQueryResult.Success(maximum.rows + bill("500")))
        assertFailureWithoutTotals("PERSONAL_CONTEXT_AGGREGATE_LIMIT", rejected)
    }

    @Test
    fun invalidAmountAnywhereRejectsTheEntireAggregation() {
        listOf<String?>(null, "", "NaN", "1,200", "￥2", "1e-9", "1000000000000000001").forEach { invalid ->
            val result = aggregate(bill("25"), bill(invalid))
            assertFailureWithoutTotals("PERSONAL_CONTEXT_INVALID_AMOUNT", result)
        }
    }

    @Test
    fun truncatedFieldsRejectTotalsEvenWhenAmountLooksValid() {
        listOf("amount", "category", "transaction_type").forEach { field ->
            val result = PersonalContextBillSummary.aggregate(DmpQueryResult.Success(
                rows = listOf(bill("25")), truncatedFields = setOf(field),
            ))
            assertFailureWithoutTotals("PERSONAL_CONTEXT_AGGREGATE_INCOMPLETE", result)
        }
    }

    @Test
    fun categoriesPastTheDisplayLimitArePreservedInOtherTotals() {
        val result = PersonalContextBillSummary.aggregate(DmpQueryResult.Success(
            rows = (1..22).map { value -> bill(value.toString(), category = "category_${value.toString().padStart(2, '0')}") },
        ))
        val categories = result.getJSONArray("categories")
        val other = result.getJSONObject("other_categories")
        val totals = result.getJSONObject("totals")

        assertTrue(result.getBoolean("ok"))
        assertTrue(result.getBoolean("categories_truncated"))
        assertEquals(20, categories.length())
        assertEquals(2, result.getInt("remaining_category_count"))
        assertBucket(other, "expenses", 2, "43")
        assertBucket(totals, "expenses", 22, "253")
        val visibleAmount = (0 until categories.length()).fold(BigDecimal.ZERO) { sum, index ->
            sum + categories.getJSONObject(index).getJSONObject("expenses").getString("amount").toBigDecimal()
        }
        assertEquals(0, visibleAmount.add(other.getJSONObject("expenses").getString("amount").toBigDecimal())
            .compareTo(totals.getJSONObject("expenses").getString("amount").toBigDecimal()))
    }

    @Test
    fun emptyMatchingSetReturnsExplicitZeroBuckets() {
        val result = PersonalContextBillSummary.aggregate(DmpQueryResult.Success(emptyList()))
        assertTrue(result.getBoolean("ok"))
        assertEquals(0, result.getInt("matched_records"))
        listOf("expenses", "income", "transfers", "unclassified").forEach { bucket ->
            assertBucket(result.getJSONObject("totals"), bucket, 0, "0")
        }
    }

    private fun bill(amount: String?, type: String? = "expenses", category: String? = "food"): Map<String, String?> =
        mapOf("amount" to amount, "transaction_type" to type, "category" to category)

    private fun aggregate(vararg rows: Map<String, String?>): JSONObject =
        PersonalContextBillSummary.aggregate(DmpQueryResult.Success(rows.toList()))

    private fun assertBucket(parent: JSONObject, name: String, count: Int, amount: String) {
        val bucket = parent.getJSONObject(name)
        assertEquals("$name count", count, bucket.getInt("count"))
        assertEquals("$name amount", amount, bucket.getString("amount"))
    }

    private fun assertFailureWithoutTotals(code: String, result: JSONObject) {
        assertFalse(result.getBoolean("ok"))
        assertEquals(code, result.getString("code"))
        assertFalse(result.has("totals"))
        assertFalse(result.has("categories"))
        assertFalse(result.has("other_categories"))
    }
}
