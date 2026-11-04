package io.github.mangi.eta.agent.browser

import org.junit.Assert.*
import org.junit.Test

class BrowserDocumentStateTest {
    @Test fun replacedDocumentRejectsLateDomAndPaintReceipts() {
        val state = BrowserDocumentState()
        val old = state.navigationStarted()
        assertTrue(state.markDomReady(old))
        val current = state.navigationStarted()
        assertFalse(state.markVisualReady(old))
        assertFalse(state.markDomReady(old))
        assertFalse(state.domReady)
        assertFalse(state.visualReady)
        assertTrue(state.markDomReady(current))
        assertTrue(state.markVisualReady(current))
    }

    @Test fun paintBeforeDomCannotCertifyObservationAndInputInvalidatesPaintOnly() {
        val state = BrowserDocumentState()
        val generation = state.navigationStarted()
        assertFalse(state.markVisualReady(generation))
        state.markDomReady(generation)
        assertTrue(state.markVisualReady(generation))
        state.invalidateVisual()
        assertTrue(state.domReady)
        assertFalse(state.visualReady)
        assertEquals(generation, state.generation)
    }
}
