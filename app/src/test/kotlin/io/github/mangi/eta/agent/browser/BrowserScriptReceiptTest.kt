package io.github.mangi.eta.agent.browser

import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicLong
import org.junit.Assert.*
import org.junit.Test

class BrowserScriptReceiptTest {
    @Test fun documentReplacementEndsMissingReceiptWithoutReplayOrWaitingEightSeconds() {
        val future = CompletableFuture<String>()
        val generation = AtomicLong(1L)
        var polls = 0
        val failure = assertThrows(BrowserScriptReceipt.Failure::class.java) {
            BrowserScriptReceipt.await(future, 8_000, { 1L }, generation::get) {
                polls++
                generation.set(2L)
            }
        }
        assertEquals(BrowserScriptReceipt.Reason.DOCUMENT_CHANGED, failure.reason)
        assertEquals(1, polls)
        assertTrue(future.isCancelled)
    }

    @Test fun lateReceiptFromOldDocumentIsNotSuccess() {
        val failure = assertThrows(BrowserScriptReceipt.Failure::class.java) {
            BrowserScriptReceipt.await(CompletableFuture.completedFuture("submitted"), 1_000, { 1L }, { 2L }) {}
        }
        assertEquals(BrowserScriptReceipt.Reason.DOCUMENT_CHANGED, failure.reason)
    }

    @Test fun contextChangeAfterReceiptBeforeConsumptionStillCannotConfirmMutation() {
        val generation = AtomicLong(1L)
        var polls = 0
        val failure = assertThrows(BrowserScriptReceipt.Failure::class.java) {
            BrowserScriptReceipt.await(CompletableFuture.completedFuture("submitted"), 1_000, { 1L }, generation::get) {
                if (++polls == 2) generation.set(2L)
            }
        }
        assertEquals(BrowserScriptReceipt.Reason.DOCUMENT_CHANGED, failure.reason)
    }

    @Test fun cancellationPropagatesAndCancelsPendingCallbackBeforeFurtherWork() {
        val future = CompletableFuture<String>()
        val cancellation = CancellationException("cancelled fixture")
        val result = assertThrows(CancellationException::class.java) {
            BrowserScriptReceipt.await(future, 8_000, { 1L }, { 1L }) { throw cancellation }
        }
        assertSame(cancellation, result)
        assertTrue(future.isCancelled)
    }

    @Test fun unavailableReceiptHasBoundedTimeoutAndLateCallbackIsIgnored() {
        val future = CompletableFuture<String>()
        val failure = assertThrows(BrowserScriptReceipt.Failure::class.java) {
            BrowserScriptReceipt.await(future, 1, { 1L }, { 1L }) {}
        }
        assertEquals(BrowserScriptReceipt.Reason.TIMEOUT, failure.reason)
        assertTrue(future.isCancelled)
        assertFalse(future.complete("late"))
    }

    @Test fun callbackFailureAndNormalCallbackRemainDistinct() {
        val future = CompletableFuture<String>()
        val cause = IllegalStateException("fixture")
        future.completeExceptionally(cause)
        assertSame(cause, assertThrows(IllegalStateException::class.java) {
            BrowserScriptReceipt.await(future, 1_000, { 1L }, { 1L }) {}
        })
        assertEquals("received", BrowserScriptReceipt.await(CompletableFuture.completedFuture("received"), 1_000, { 1L }, { 1L }) {})
    }
}
