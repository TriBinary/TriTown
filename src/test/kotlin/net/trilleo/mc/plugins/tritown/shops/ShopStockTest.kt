package net.trilleo.mc.plugins.tritown.shops

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A limited supply has to refill on time, and never past what it started with. */
class ShopStockTest {

    private val minute = 60_000L

    @Test
    fun `first look sets the clock rather than restocking`() {
        val stock = ShopStock(max = 10, restockSeconds = 60L, remaining = 4, lastRestock = 0L)

        stock.restock(START)

        assertEquals(4, stock.remaining)
        assertEquals(START, stock.lastRestock)
    }

    @Test
    fun `nothing refills before the period is up`() {
        val stock = ShopStock(max = 10, restockSeconds = 60L, remaining = 2, lastRestock = START)

        assertEquals(2, stock.available(START + minute - 1))
    }

    @Test
    fun `the period turning over fills it back up`() {
        val stock = ShopStock(max = 10, restockSeconds = 60L, remaining = 2, lastRestock = START)

        assertEquals(10, stock.available(START + minute))
    }

    @Test
    fun `a long absence fills it once, not once per period`() {
        val stock = ShopStock(max = 10, restockSeconds = 60L, remaining = 0, lastRestock = START)

        assertEquals(10, stock.available(START + 100 * minute))
    }

    @Test
    fun `the refill time advances by whole periods so it does not drift`() {
        val stock = ShopStock(max = 10, restockSeconds = 60L, remaining = 0, lastRestock = START)

        stock.restock(START + 3 * minute + 30_000L)

        assertEquals(START + 3 * minute, stock.lastRestock)
    }

    @Test
    fun `a supply that never restocks stays where it is`() {
        val stock = ShopStock(max = 10, restockSeconds = 0L, remaining = 1, lastRestock = START)

        assertEquals(1, stock.available(START + 1000 * minute))
    }

    @Test
    fun `taking more than is there takes nothing`() {
        val stock = ShopStock(max = 10, restockSeconds = 0L, remaining = 3, lastRestock = START)

        assertFalse(stock.take(4, START))
        assertEquals(3, stock.remaining)
    }

    @Test
    fun `taking what is there succeeds`() {
        val stock = ShopStock(max = 10, restockSeconds = 0L, remaining = 3, lastRestock = START)

        assertTrue(stock.take(3, START))
        assertEquals(0, stock.remaining)
    }

    @Test
    fun `putting stock back never goes above the maximum`() {
        val stock = ShopStock(max = 10, restockSeconds = 0L, remaining = 9, lastRestock = START)

        stock.restore(5)

        assertEquals(10, stock.remaining)
    }

    private companion object {
        /** An arbitrary fixed instant, so no test depends on the wall clock. */
        const val START = 1_700_000_000_000L
    }
}
