package net.trilleo.mc.plugins.tritown.economy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MoneyTest {

    @Test
    fun `converts whole and fractional amounts to minor units`() {
        assertEquals(10000L, Money.ofDouble(100.0, 2).minor)
        assertEquals(1L, Money.ofDouble(0.01, 2).minor)
        assertEquals(0L, Money.ofDouble(0.0, 2).minor)
        assertEquals(100L, Money.ofDouble(100.0, 0).minor)
        assertEquals(123456L, Money.ofDouble(123.456, 3).minor)
    }

    @Test
    fun `round trips through double`() {
        for (amount in listOf(0.0, 0.01, 1.5, 100.0, 1234.56, 999999.99)) {
            assertEquals(amount, Money.ofDouble(amount, 2).toDouble(2), "round trip of $amount")
        }
    }

    @Test
    fun `does not drift the way repeated double addition does`() {
        val tenth = Money.ofDouble(0.1, 2)
        val fifth = Money.ofDouble(0.2, 2)
        assertEquals("0.30", tenth.plusExact(fifth).toPlainString(2))

        var total = Money.ZERO
        repeat(10) { total = total.plusExact(tenth) }
        assertEquals("1.00", total.toPlainString(2))
        assertEquals(1.0, total.toDouble(2))
    }

    @Test
    fun `rounds half away from zero at the scale boundary`() {
        assertEquals(1L, Money.ofDouble(0.005, 2).minor)
        assertEquals(0L, Money.ofDouble(0.004, 2).minor)
        assertEquals(2L, Money.ofDouble(0.015, 2).minor)
        assertEquals(-1L, Money.ofDouble(-0.005, 2).minor)
    }

    @Test
    fun `rejects amounts that are not finite`() {
        assertFailsWith<ArithmeticException> { Money.ofDouble(Double.NaN, 2) }
        assertFailsWith<ArithmeticException> { Money.ofDouble(Double.POSITIVE_INFINITY, 2) }
        assertFailsWith<ArithmeticException> { Money.ofDouble(Double.NEGATIVE_INFINITY, 2) }
    }

    @Test
    fun `rejects amounts that do not fit in a long`() {
        assertFailsWith<ArithmeticException> { Money.ofDouble(Double.MAX_VALUE, 2) }
    }

    @Test
    fun `reports overflow rather than wrapping`() {
        val huge = Money(Long.MAX_VALUE)
        assertFailsWith<ArithmeticException> { huge.plusExact(Money(1L)) }
        assertFailsWith<ArithmeticException> { Money(Long.MIN_VALUE).minusExact(Money(1L)) }
    }

    @Test
    fun `compares and reports sign`() {
        assertTrue(Money(5L) > Money(1L))
        assertTrue(Money(-1L).isNegative)
        assertTrue(Money(1L).isPositive)
        assertTrue(Money.ZERO.isZero)
        assertEquals(Money(7L), Money(-7L).abs())
    }
}
