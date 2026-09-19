package net.trilleo.mc.plugins.tritown.shops

import net.trilleo.mc.plugins.tritown.enums.TownyRequirement
import kotlin.test.Test
import kotlin.test.assertEquals

/** Discounts pick the best match rather than adding up, and never round a price up. */
class ShopPricingTest {

    private val rates = mapOf(
        TownyRequirement.HAS_TOWN to 0.05,
        TownyRequirement.HAS_NATION to 0.10,
        TownyRequirement.IS_MAYOR to 0.20,
    )

    @Test
    fun `no standing earns no discount`() {
        assertEquals(0.0, ShopPricing.discount(rates, setOf(TownyRequirement.NONE)))
    }

    @Test
    fun `a matching standing earns its discount`() {
        assertEquals(0.05, ShopPricing.discount(rates, setOf(TownyRequirement.NONE, TownyRequirement.HAS_TOWN)))
    }

    @Test
    fun `several standings earn the best one, not the sum`() {
        val standing = setOf(TownyRequirement.HAS_TOWN, TownyRequirement.HAS_NATION, TownyRequirement.IS_MAYOR)

        assertEquals(0.20, ShopPricing.discount(rates, standing))
    }

    @Test
    fun `a standing with no rate set earns nothing`() {
        assertEquals(0.0, ShopPricing.discount(rates, setOf(TownyRequirement.IS_KING)))
    }

    @Test
    fun `a discount comes off the price`() {
        assertEquals(80.0, ShopPricing.apply(100.0, 0.20, 2))
    }

    @Test
    fun `a discounted price rounds down, so the discount is never worth less than it says`() {
        assertEquals(6.66, ShopPricing.apply(9.99, 1.0 / 3.0, 2))
    }

    @Test
    fun `no discount leaves the price alone`() {
        assertEquals(9.99, ShopPricing.apply(9.99, 0.0, 2))
    }

    @Test
    fun `a free entry stays free`() {
        assertEquals(0.0, ShopPricing.apply(0.0, 0.5, 2))
    }

    @Test
    fun `a total is rounded to the currency's scale`() {
        assertEquals(10.01, ShopPricing.round(10.005, 2))
        assertEquals(10.0, ShopPricing.round(10.004, 2))
    }
}
