package io.github.mangi.eta.agent.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalScreenBufferTest {

    @Test
    fun plainTextWritesFromHomeAndTracksCursor() {
        val buffer = TerminalScreenBuffer(cols = 10, rows = 3)
        buffer.process("hello")
        assertEquals("hello", buffer.dump()[0])
        assertEquals(0, buffer.cursorRow)
        assertEquals(5, buffer.cursorCol)
    }

    @Test
    fun deferredWrapMovesToNextLineOnOverflow() {
        val buffer = TerminalScreenBuffer(cols = 5, rows = 3)
        buffer.process("abcde")
        assertEquals(0, buffer.cursorRow)
        assertEquals(4, buffer.cursorCol)
        buffer.process("f")
        assertEquals(listOf("abcde", "f", ""), buffer.dump())
        assertEquals(1, buffer.cursorRow)
        assertEquals(1, buffer.cursorCol)
    }

    @Test
    fun linefeedAtBottomScrollsIntoScrollback() {
        val buffer = TerminalScreenBuffer(cols = 10, rows = 3)
        buffer.process("one\r\ntwo\r\nthree\r\nfour\r\nfive")
        assertEquals(listOf("three", "four", "five"), buffer.dump())
        val all = buffer.lines()
        assertEquals(5, all.size)
        assertEquals("one", all[0].snapshot().joinToString("") { it.text }.trimEnd())
        assertEquals("two", all[1].snapshot().joinToString("") { it.text }.trimEnd())
    }

    @Test
    fun carriageReturnAndLinefeedAreIndependent() {
        val buffer = TerminalScreenBuffer(cols = 10, rows = 3)
        buffer.process("ab\rcd")
        assertEquals("cd", buffer.dump()[0])
    }

    @Test
    fun cupPositionsCursor() {
        val buffer = TerminalScreenBuffer(cols = 10, rows = 5)
        buffer.process("\u001B[2;3HX")
        assertEquals("", buffer.dump()[0])
        assertEquals("  X", buffer.dump()[1])
    }

    @Test
    fun eraseInLineAndDisplay() {
        val buffer = TerminalScreenBuffer(cols = 10, rows = 3)
        buffer.process("aaaaaaaaaabbbbbbbbbbcccccccccc")
        buffer.process("\u001B[1;5H\u001B[K")
        assertEquals("aaaa", buffer.dump()[0])
        buffer.process("\u001B[2J")
        assertEquals(listOf("", "", ""), buffer.dump())
    }

    @Test
    fun sgrColorsAttachToCellsAndReset() {
        val buffer = TerminalScreenBuffer(cols = 10, rows = 3)
        buffer.process("\u001B[31mred\u001B[0mplain")
        val line = buffer.screenLine(0).snapshot()
        assertEquals(AnsiSgr.STANDARD[1], line[0].style.fg)
        assertEquals(AnsiSgr.STANDARD[1], line[2].style.fg)
        assertEquals(SgrStyle.PLAIN, line[3].style)
    }

    @Test
    fun eraseInheritsBackgroundColor() {
        val buffer = TerminalScreenBuffer(cols = 10, rows = 3)
        buffer.process("\u001B[41m\u001B[2J")
        val line = buffer.screenLine(0).snapshot()
        assertEquals(AnsiSgr.STANDARD[1], line[0].style.bg)
    }

    @Test
    fun altScreenSwitchesAndRestores() {
        val buffer = TerminalScreenBuffer(cols = 10, rows = 3)
        buffer.process("main")
        buffer.process("\u001B[?1049h")
        assertEquals(listOf("", "", ""), buffer.dump())
        buffer.process("alt")
        assertEquals("alt", buffer.dump()[0])
        buffer.process("\u001B[?1049l")
        assertEquals("main", buffer.dump()[0])
    }

    @Test
    fun scrollRegionScrollsOnlyInsideRegion() {
        val buffer = TerminalScreenBuffer(cols = 10, rows = 4)
        buffer.process("\u001B[2;3r")
        buffer.process("\u001B[1;1Hheader")
        buffer.process("\u001B[2;1Ha\r\nb\r\nc\r\nd")
        val dump = buffer.dump()
        assertEquals("header", dump[0])
        assertEquals("c", dump[1])
        assertEquals("d", dump[2])
    }

    @Test
    fun wideCharOccupiesTwoCells() {
        val buffer = TerminalScreenBuffer(cols = 10, rows = 3)
        buffer.process("中x")
        val line = buffer.screenLine(0).snapshot()
        assertEquals("中", line[0].text)
        assertTrue(line[1].continuation)
        assertEquals("x", line[2].text)
        assertEquals(3, buffer.cursorCol)
    }

    @Test
    fun cursorVisibilityToggles() {
        val buffer = TerminalScreenBuffer(cols = 10, rows = 3)
        buffer.process("\u001B[?25l")
        assertFalse(buffer.cursorVisible)
        buffer.process("\u001B[?25h")
        assertTrue(buffer.cursorVisible)
    }

    @Test
    fun deleteAndInsertCharsShiftLineContent() {
        val buffer = TerminalScreenBuffer(cols = 10, rows = 3)
        buffer.process("abcdef")
        buffer.process("\u001B[1;2H\u001B[2P")
        assertEquals("adef", buffer.dump()[0])
        buffer.process("\u001B[1;1H\u001B[2@")
        assertEquals("  adef", buffer.dump()[0])
    }

    @Test
    fun versionBumpsOnProcess() {
        val buffer = TerminalScreenBuffer(cols = 10, rows = 3)
        val before = buffer.version
        buffer.process("x")
        assertTrue(buffer.version > before)
    }

    @Test
    fun escapeSequenceSplitAcrossChunksIsResumed() {
        val buffer = TerminalScreenBuffer(cols = 10, rows = 3)
        buffer.process("ab\u001B[")
        buffer.process("2;3")
        buffer.process("HX")
        assertEquals(listOf("ab", "  X", ""), buffer.dump())
    }

    @Test
    fun utf8SplitAcrossChunksDecodesOnce() {
        val buffer = TerminalScreenBuffer(cols = 10, rows = 3)
        val bytes = "中文".toByteArray(Charsets.UTF_8)
        buffer.feed(bytes.copyOfRange(0, 2))
        buffer.feed(bytes.copyOfRange(2, 4))
        buffer.feed(bytes.copyOfRange(4, bytes.size))
        assertEquals("中文", buffer.dump()[0])
        assertEquals(4, buffer.cursorCol)
    }

    @Test
    fun prefixedCsiSequencesLeaveNoResidue() {
        val buffer = TerminalScreenBuffer(cols = 20, rows = 3)
        buffer.process("\u001B[>4;1m\u001B[>1u\u001B[<u\u001B[?u\u001B[ q\u001B[=0cok")
        assertEquals("ok", buffer.dump()[0])
    }

    @Test
    fun controlStringsAreSkippedAcrossChunks() {
        val buffer = TerminalScreenBuffer(cols = 20, rows = 3)
        buffer.process("a\u001B]0;window ti")
        buffer.process("tle\u0007b\u001BPq#0;2\u001B")
        buffer.process("\\c")
        assertEquals("abc", buffer.dump()[0])
    }

    @Test
    fun cursorPositionAndDeviceAttributesAreAnswered() {
        val buffer = TerminalScreenBuffer(cols = 20, rows = 5)
        buffer.process("\u001B[3;7H\u001B[6n\u001B[c\u001B[>c\u001B[5n\u001B[18t")
        assertEquals(
            "\u001B[3;7R\u001B[?62;22c\u001B[>1;10;0c\u001B[0n\u001B[8;5;20t",
            buffer.drainResponses(),
        )
        assertEquals(null, buffer.drainResponses())
    }

    @Test
    fun keyAndPasteModesAreTrackedAndReported() {
        val buffer = TerminalScreenBuffer(cols = 20, rows = 3)
        buffer.process("\u001B[?1;2004;2026h")
        assertTrue(buffer.applicationCursorKeys)
        assertTrue(buffer.bracketedPaste)
        assertTrue(buffer.synchronizedOutput)
        buffer.process("\u001B[?2026l\u001B[?2026\$p\u001B[?9999\$p")
        assertFalse(buffer.synchronizedOutput)
        assertEquals("\u001B[?2026;2\$y\u001B[?9999;0\$y", buffer.drainResponses())
    }

    @Test
    fun decSpecialGraphicsDrawsBoxCharacters() {
        val buffer = TerminalScreenBuffer(cols = 10, rows = 3)
        buffer.process("\u001B(0lqqk\u001B(Bx")
        assertEquals("┌──┐x", buffer.dump()[0])
    }

    @Test
    fun repeatRepeatsLastPrintedCharacter() {
        val buffer = TerminalScreenBuffer(cols = 10, rows = 3)
        buffer.process("-\u001B[4b")
        assertEquals("-----", buffer.dump()[0])
    }

    @Test
    fun combiningMarkJoinsPreviousCell() {
        val buffer = TerminalScreenBuffer(cols = 10, rows = 3)
        buffer.process("e\u0301x")
        assertEquals("e\u0301", buffer.screenLine(0).snapshot()[0].text)
        assertEquals(2, buffer.cursorCol)
    }

    @Test
    fun shrinkingDropsBlankRowsBeforeHistory() {
        val buffer = TerminalScreenBuffer(cols = 10, rows = 5)
        buffer.process("one\r\ntwo")
        buffer.resize(newCols = 10, newRows = 3)
        assertEquals(listOf("one", "two", ""), buffer.dump())
        assertEquals(1, buffer.cursorRow)
    }

    @Test
    fun shrinkThenGrowRestoresRowsFromHistory() {
        val buffer = TerminalScreenBuffer(cols = 10, rows = 4)
        buffer.process("a\r\nb\r\nc\r\nd")
        buffer.resize(newCols = 10, newRows = 2)
        assertEquals(listOf("c", "d"), buffer.dump())
        assertEquals(1, buffer.cursorRow)
        buffer.resize(newCols = 10, newRows = 4)
        assertEquals(listOf("a", "b", "c", "d"), buffer.dump())
        assertEquals(3, buffer.cursorRow)
    }

    @Test
    fun narrowingClampsCursorAndClearsSplitWideChar() {
        val buffer = TerminalScreenBuffer(cols = 6, rows = 2)
        buffer.process("abc中")
        buffer.resize(newCols = 4, newRows = 2)
        assertEquals("abc", buffer.dump()[0])
        assertEquals(3, buffer.cursorCol)
        buffer.process("\u001B[18t")
        assertEquals("\u001B[8;2;4t", buffer.drainResponses())
    }

    @Test
    fun inverseAndColonSubparametersParse() {
        val style = AnsiSgr.apply("7;4:3;38:2::1:2:3", SgrStyle.PLAIN)
        assertTrue(style.inverse)
        assertTrue(style.underline)
        assertFalse(style.italic)
        assertEquals(0xFF010203, style.fg)
        assertFalse(AnsiSgr.apply("27;24", style).inverse)
    }

    @Test
    fun defaultColorQueriesAnswerWithThemeAndOriginalTerminator() {
        val buffer = TerminalScreenBuffer(cols = 10, rows = 3)
        buffer.defaultForeground = 0xFF1A2B3C
        buffer.defaultBackground = 0xFFFFFFFF
        buffer.process("\u001B]11;?\u0007\u001B]10;?\u001B")
        buffer.process("\\x")
        assertEquals(
            "\u001B]11;rgb:ffff/ffff/ffff\u0007\u001B]10;rgb:1a1a/2b2b/3c3c\u001B\\",
            buffer.drainResponses(),
        )
        assertEquals("x", buffer.dump()[0])
    }

    @Test
    fun inPlaceRewriteProducesDistinctRenderSnapshot() {
        val buffer = TerminalScreenBuffer(cols = 10, rows = 3)
        buffer.process("old")
        val before = buffer.renderLines()
        // 整屏清除后在原位重写：行对象不变，光标也回到同一位置。
        buffer.process("\u001B[2J\u001B[Hnew")
        val after = buffer.renderLines()

        assertFalse(before == after)
        assertEquals("old", before[0].cells.joinToString("") { it.text }.trimEnd())
        assertEquals("new", after[0].cells.joinToString("") { it.text }.trimEnd())
        assertTrue(after[1] === buffer.renderLines()[1])
    }
}
