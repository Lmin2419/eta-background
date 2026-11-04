package io.github.mangi.eta.agent.browser

/** Renderer callbacks may arrive after a newer document has started. */
internal class BrowserDocumentState {
    @Volatile var generation: Long = 0L
        private set
    @Volatile private var readyGeneration = -1L
    @Volatile private var visualGeneration = -1L

    val domReady: Boolean get() = readyGeneration == generation
    val visualReady: Boolean get() = visualGeneration == generation

    @Synchronized fun navigationStarted(): Long {
        generation++
        readyGeneration = -1L
        visualGeneration = -1L
        return generation
    }

    @Synchronized fun markDomReady(observedGeneration: Long): Boolean {
        if (observedGeneration != generation) return false
        readyGeneration = observedGeneration
        return true
    }

    @Synchronized fun markVisualReady(observedGeneration: Long): Boolean {
        if (observedGeneration != generation || !domReady) return false
        visualGeneration = observedGeneration
        return true
    }

    @Synchronized fun invalidateVisual() { visualGeneration = -1L }
}
