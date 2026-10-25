package io.github.mangi.eta.agent.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalKeyEncoderTest {
    @Test
    fun cursorKeysFollowApplicationCursorMode() {
        assertEquals("\u001B[A", TerminalKeyEncoder.key(TerminalKey.UP))
        assertEquals("\u001BOA", TerminalKeyEncoder.key(TerminalKey.UP, applicationCursorKeys = true))
        assertEquals("\u001B[H", TerminalKeyEncoder.key(TerminalKey.HOME))
    }

    @Test
    fun modifiersUseXtermParameterEvenInApplicationMode() {
        val ctrl = TerminalModifiers(ctrl = true)
        assertEquals("\u001B[1;5D", TerminalKeyEncoder.key(TerminalKey.LEFT, ctrl, applicationCursorKeys = true))
        assertEquals("\u001B[5;3~", TerminalKeyEncoder.key(TerminalKey.PAGE_UP, TerminalModifiers(alt = true)))
        assertEquals("\u001B[1;2P", TerminalKeyEncoder.key(TerminalKey.F1, TerminalModifiers(shift = true)))
        assertEquals("\u001B[Z", TerminalKeyEncoder.key(TerminalKey.TAB, TerminalModifiers(shift = true)))
    }

    @Test
    fun editingKeysMatchXterm() {
        assertEquals("\r", TerminalKeyEncoder.key(TerminalKey.ENTER))
        assertEquals("\u007F", TerminalKeyEncoder.key(TerminalKey.BACKSPACE))
        assertEquals("\u001B\u007F", TerminalKeyEncoder.key(TerminalKey.BACKSPACE, TerminalModifiers(alt = true)))
        assertEquals("\u001B[3~", TerminalKeyEncoder.key(TerminalKey.DELETE))
        assertEquals("\u001B[24~", TerminalKeyEncoder.key(TerminalKey.F12))
    }

    @Test
    fun ctrlAndAltApplyToFirstCharacterOnly() {
        assertEquals("\u0003", TerminalKeyEncoder.text("c", TerminalModifiers(ctrl = true)))
        assertEquals("\u0003at", TerminalKeyEncoder.text("Cat", TerminalModifiers(ctrl = true)))
        assertEquals("\u001Bb", TerminalKeyEncoder.text("b", TerminalModifiers(alt = true)))
        assertEquals("\u001B\u0018", TerminalKeyEncoder.text("x", TerminalModifiers(ctrl = true, alt = true)))
        assertEquals("中", TerminalKeyEncoder.text("中", TerminalModifiers(ctrl = true)))
    }

    @Test
    fun committedNewlinesBecomeCarriageReturns() {
        assertEquals("ls\r", TerminalKeyEncoder.text("ls\n"))
        assertEquals("a\rb", TerminalKeyEncoder.text("a\r\nb"))
    }

    @Test
    fun bracketedPasteWrapsAndStripsForgedTerminator() {
        assertEquals("a\rb", TerminalKeyEncoder.paste("a\nb", bracketed = false))
        assertEquals(
            "\u001B[200~echo hi\rrm\u001B[201~",
            TerminalKeyEncoder.paste("echo hi\n\u001B[201~rm", bracketed = true),
        )
    }
}
