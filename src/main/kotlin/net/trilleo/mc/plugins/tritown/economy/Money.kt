package net.trilleo.mc.plugins.tritown.economy

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * An amount of money held as a whole number of minor units — cents, for a
 * currency with two fractional digits.
 *
 * Balances are stored this way so that repeated arithmetic can never drift the
 * way `Double` addition does. Conversion to and from `Double` happens exactly
 * once, at the edge where Vault hands over or expects one.
 */
@JvmInline
value class Money(val minor: Long) : Comparable<Money> {

    val isZero: Boolean get() = minor == 0L
    val isPositive: Boolean get() = minor > 0L
    val isNegative: Boolean get() = minor < 0L

    /** Converts back to a `Double` at [scale] fractional digits. */
    fun toDouble(scale: Int): Double = BigDecimal.valueOf(minor, scale).toDouble()

    /** Renders the amount as a plain decimal string at [scale] fractional digits, without grouping. */
    fun toPlainString(scale: Int): String = BigDecimal.valueOf(minor, scale).toPlainString()

    /** @throws ArithmeticException when the sum overflows a [Long]. */
    fun plusExact(other: Money): Money = Money(Math.addExact(minor, other.minor))

    /** @throws ArithmeticException when the difference overflows a [Long]. */
    fun minusExact(other: Money): Money = Money(Math.subtractExact(minor, other.minor))

    fun abs(): Money = if (minor < 0L) Money(-minor) else this

    override fun compareTo(other: Money): Int = minor.compareTo(other.minor)

    companion object {

        val ZERO = Money(0L)

        /**
         * Converts [amount] to minor units at [scale] fractional digits,
         * rounding half away from zero.
         *
         * `BigDecimal.valueOf` is used rather than the `BigDecimal(Double)`
         * constructor so that `0.1` is read as `0.1` and not as its binary
         * expansion.
         *
         * @throws ArithmeticException when [amount] is NaN or infinite, or when
         *   the result does not fit in a [Long].
         */
        fun ofDouble(amount: Double, scale: Int): Money {
            if (!amount.isFinite()) throw ArithmeticException("Amount is not a finite number: $amount")
            return Money(
                BigDecimal.valueOf(amount)
                    .setScale(scale, RoundingMode.HALF_UP)
                    .movePointRight(scale)
                    .longValueExact()
            )
        }
    }
}
