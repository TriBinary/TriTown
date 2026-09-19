package net.trilleo.mc.plugins.tritown.shops

import net.trilleo.mc.plugins.tritown.enums.LimitPeriod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * A per-player limit has to reset for everybody at once, and a lifetime limit
 * must never reset at all.
 */
class ShopLimitTest {

    private val day = 24L * 60L * 60L * 1000L

    /** An arbitrary instant snapped to the start of a week, so both windows begin here. */
    private val start = 1_700_000_000_000L / (7L * day) * (7L * day)

    @Test
    fun `a lifetime limit has a single window`() {
        val limit = ShopLimit(5, LimitPeriod.NONE)

        assertEquals(limit.windowAt(0L), limit.windowAt(10_000L * day))
    }

    @Test
    fun `a daily limit changes window at the day boundary`() {
        val limit = ShopLimit(5, LimitPeriod.DAILY)

        assertEquals(limit.windowAt(start), limit.windowAt(start + day - 1))
        assertNotEquals(limit.windowAt(start), limit.windowAt(start + day))
    }

    @Test
    fun `a weekly limit changes window at the week boundary`() {
        val limit = ShopLimit(5, LimitPeriod.WEEKLY)

        assertEquals(limit.windowAt(start), limit.windowAt(start + 7 * day - 1))
        assertNotEquals(limit.windowAt(start), limit.windowAt(start + 7 * day))
    }

    @Test
    fun `what is spent this window counts against the limit`() {
        val limit = ShopLimit(5, LimitPeriod.DAILY)

        assertEquals(2, limit.remaining(used = 3, usedWindow = limit.windowAt(start), epochMillis = start))
    }

    @Test
    fun `a count from an earlier window is spent and ignored`() {
        val limit = ShopLimit(5, LimitPeriod.DAILY)

        assertEquals(5, limit.remaining(used = 5, usedWindow = limit.windowAt(start), epochMillis = start + day))
    }

    @Test
    fun `a lifetime limit never gives the allowance back`() {
        val limit = ShopLimit(5, LimitPeriod.NONE)

        assertEquals(0, limit.remaining(used = 5, usedWindow = 0L, epochMillis = start + 1000 * day))
    }

    @Test
    fun `buying past the limit does not report a negative allowance`() {
        val limit = ShopLimit(5, LimitPeriod.DAILY)

        assertEquals(0, limit.remaining(used = 9, usedWindow = limit.windowAt(start), epochMillis = start))
    }
}
