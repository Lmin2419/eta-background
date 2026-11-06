package io.github.mangi.eta.agent.voice

import io.github.mangi.eta.ui.model.*
import io.github.mangi.eta.ui.theme.darkEtaColors
import io.github.mangi.eta.ui.theme.lightEtaColors
import org.junit.Assert.*
import org.junit.Test

class EtaVoicePresentationTest {
    @Test fun recentThreeRoundsAreOnlyASliceAndKeepCurrentPartialTurn() {
        val original = (1..5).flatMap { round -> listOf(UserMessageUi("u$round", "Request $round"), AgentMessageUi("a$round", "Answer $round")) } +
            UserMessageUi("u6", "Current request")
        val visible = recentAssistantRounds(original)
        assertEquals(listOf("u4", "a4", "u5", "a5", "u6"), visible.map { it.id })
        assertEquals(11, original.size)
        assertSame(original[6], visible.first())
    }
    @Test fun voicePaletteUsesRaisedSurfaceAndReadableTextInBothModes() {
        listOf(lightEtaColors(), darkEtaColors()).forEach { palette ->
            val voice = etaVoicePanelColors(palette)
            assertEquals(palette.surfaceRaised, voice.content)
            assertEquals(palette.textPrimary, voice.inputPrimary)
            assertEquals(palette.outline, voice.panelStroke)
            assertNotEquals(voice.content, voice.inputPrimary)
        }
    }
}
