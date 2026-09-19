package net.trilleo.mc.plugins.tritown.shops

import net.trilleo.mc.plugins.tritown.enums.TownyRequirement
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Works out what a buyer actually pays.
 *
 * Kept free of Bukkit and Towny so the arithmetic can be tested on its own; the
 * caller reads the player's standing out of Towny and passes it in.
 */
object ShopPricing {

    /**
     * The single best discount [standing] earns, as a fraction between 0 and 1.
     *
     * Discounts do not stack: a mayor whose nation also gets one pays the better
     * of the two, not both. Stacking them would make a small change to one rate
     * move prices no owner intended to touch.
     */
    fun discount(rates: Map<TownyRequirement, Double>, standing: Set<TownyRequirement>): Double =
        standing.mapNotNull { rates[it] }.maxOrNull()?.coerceIn(0.0, 1.0) ?: 0.0

    /**
     * [money] with [discount] taken off, rounded to [scale] digits.
     *
     * Rounded down, so a discount is never worth less than it says.
     */
    fun apply(money: Double, discount: Double, scale: Int): Double {
        if (money <= 0.0 || discount <= 0.0) return money
        return BigDecimal.valueOf(money)
            .multiply(BigDecimal.ONE.subtract(BigDecimal.valueOf(discount)))
            .setScale(scale, RoundingMode.DOWN)
            .toDouble()
    }

    /** [money] rounded to [scale] digits, for a price the owner typed or a total built from one. */
    fun round(money: Double, scale: Int): Double =
        BigDecimal.valueOf(money).setScale(scale, RoundingMode.HALF_UP).toDouble()
}
