package io.github.mangi.eta.agent.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalContextSourcesTest {
    @Test fun supportedSourcesUseStableDocumentIdsAndRequiredVisibilityProtection() {
        assertEquals(setOf("photos", "notes", "recordings", "calendar", "calendar_todos", "todos", "memories", "bills", "collections", "events", "notifications", "files", "app_files", "flights", "hotels", "trains"), PersonalContextSources.ids)
        for (source in PersonalContextSources.all) {
            assertEquals(listOf("docID"), source.field("id")?.physicalCandidates)
            assertTrue(source.probeRequired.any { it.logicalName == "id" })
            assertTrue(source.probeRequired.any { it.physicalCandidates == listOf("valid") })
            assertTrue(source.fixedFilters.contains(PersonalContextFixedFilter("_valid", PersonalContextFilterOperator.EQUALS, 1)))
            assertTrue(source.fields.size <= 16)
        }
    }

    @Test fun recycledAndEncryptedRecordsCannotLoseTheirProtectionOnSchemaDrift() {
        val notes = PersonalContextSources.find("notes")!!
        assertTrue(notes.fixedFilters.contains(PersonalContextFixedFilter("_recycled", PersonalContextFilterOperator.EQUALS, 0)))
        assertTrue(notes.fixedFilters.contains(PersonalContextFixedFilter("_encrypted", PersonalContextFilterOperator.EQUALS, 0)))
        for (id in listOf("memories", "bills")) {
            val source = PersonalContextSources.find(id)!!
            assertTrue(source.fixedFilters.contains(PersonalContextFixedFilter("_recycled", PersonalContextFilterOperator.NULL_OR_EQUALS, 0)))
        }
        for (source in listOf(notes, PersonalContextSources.find("memories")!!, PersonalContextSources.find("bills")!!)) {
            for (filter in source.fixedFilters) {
                val field = source.field(filter.fieldName)!!
                assertTrue(field.required)
                assertFalse(field.exposed)
            }
        }
    }

    @Test fun photoReferencesOnlyDescribeImagesAndUseVerifiedCaptionAliasOrder() {
        val photos = PersonalContextSources.find("photos")!!
        assertEquals(listOf("caption_content0Com", "caption_contentCom"), photos.field("caption")?.physicalCandidates)
        assertEquals(listOf("media_idCom"), photos.field("origin_id")?.physicalCandidates)
        assertTrue(photos.probeRequired.any { it.logicalName == "origin_id" })
        assertEquals(listOf("typeCom"), photos.field("_media_type")?.physicalCandidates)
        assertTrue(photos.fixedFilters.contains(PersonalContextFixedFilter("_media_type", PersonalContextFilterOperator.EQUALS, 1)))
    }

    @Test fun businessTimesUseSourceUnitsAndDoNotFallbackToIndexTimestamps() {
        val photos = PersonalContextSources.find("photos")!!
        assertEquals(PersonalContextTimeUnit.SECONDS, photos.timeUnit)
        val todos = PersonalContextSources.find("calendar_todos")!!
        assertEquals(listOf("start_timeCom"), todos.field(todos.timeField!!)?.physicalCandidates)
        assertTrue(todos.timeUnit!!.requiresNumericCast)
        val memories = PersonalContextSources.find("memories")!!
        assertEquals(listOf("createdTimeCom"), memories.field(memories.timeField!!)?.physicalCandidates)
        val bills = PersonalContextSources.find("bills")!!
        assertEquals(listOf("transactionTimeCom"), bills.field(bills.timeField!!)?.physicalCandidates)
        val collections = PersonalContextSources.find("collections")!!
        assertNull(collections.timeField)
        assertNull(collections.timeUnit)
        assertEquals("indexed_time", collections.sortField)
    }
}
