package net.trilleo.mc.plugins.tritown.registration

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The slot arithmetic behind every framed menu.
 *
 * Worth pinning down here rather than in game: an off-by-one puts an item under
 * the border where nothing can click it, and that looks like a menu that
 * ignores you rather than like a bug.
 */
class GUIFrameTest {

    @Test
    fun `a six-row menu keeps the four inner rows`() {
        val slots = GUIFrame.contentSlots(6)

        assertEquals(28, slots.size)
        assertEquals(10, slots.first())
        assertEquals(43, slots.last())
    }

    @Test
    fun `content is in reading order, so a list fills left to right`() {
        assertEquals(listOf(10, 11, 12, 13, 14, 15, 16, 19), GUIFrame.contentSlots(6).take(8))
    }

    @Test
    fun `the border rows and columns are never content`() {
        val slots = GUIFrame.contentSlots(6).toSet()

        val topRow = 0..8
        val bottomRow = 45..53
        val leftColumn = (0 until 6).map { it * 9 }
        val rightColumn = (0 until 6).map { it * 9 + 8 }

        assertTrue((topRow + bottomRow + leftColumn + rightColumn).none { it in slots })
    }

    @Test
    fun `a three-row menu keeps its single inner row`() {
        assertEquals((10..16).toList(), GUIFrame.contentSlots(3))
    }

    @Test
    fun `a menu too short to have an inside holds nothing`() {
        assertEquals(emptyList(), GUIFrame.contentSlots(2))
        assertEquals(emptyList(), GUIFrame.contentSlots(1))
    }
}
