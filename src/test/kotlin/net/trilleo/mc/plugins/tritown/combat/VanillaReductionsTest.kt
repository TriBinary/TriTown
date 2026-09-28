package net.trilleo.mc.plugins.tritown.combat

import net.trilleo.mc.plugins.tritown.combat.VanillaReductions.Kind
import net.trilleo.mc.plugins.tritown.combat.VanillaReductions.Reduction
import kotlin.test.Test
import kotlin.test.assertEquals

class VanillaReductionsTest {

    @Test
    fun `armor is taken out and the rest keep their share`() {
        // Vanilla: a 10 hit, armor takes 4, Resistance I then takes 20% of the 6 left, Protection 40% of the rest.
        val reductions = listOf(
            Reduction(Kind.ARMOR, -4.0),
            Reduction(Kind.SHARE, -1.2),
            Reduction(Kind.SHARE, -1.92),
            Reduction(Kind.ABSORPTION, 0.0),
        )

        val rescaled = VanillaReductions.rescale(10.0, reductions, base = 5.0, absorption = 0.0)

        assertEquals(0.0, rescaled[0])
        assertEquals(-1.0, rescaled[1], 1e-9)
        assertEquals(-1.6, rescaled[2], 1e-9)
        assertEquals(0.0, rescaled[3], 1e-9)
    }

    @Test
    fun `absorption soaks up what it can hold of the new hit`() {
        val reductions = listOf(Reduction(Kind.ARMOR, -2.0), Reduction(Kind.ABSORPTION, -8.0))

        assertEquals(listOf(0.0, -3.0), VanillaReductions.rescale(10.0, reductions, base = 3.0, absorption = 4.0))
        assertEquals(listOf(0.0, -4.0), VanillaReductions.rescale(10.0, reductions, base = 9.0, absorption = 4.0))
    }

    @Test
    fun `a raised shield still blocks the whole hit`() {
        val reductions = listOf(Reduction(Kind.SHARE, -7.0), Reduction(Kind.ARMOR, 0.0))

        val rescaled = VanillaReductions.rescale(7.0, reductions, base = 0.4, absorption = 0.0)

        assertEquals(-0.4, rescaled[0], 1e-9)
    }

    @Test
    fun `nothing is taken from a hit vanilla had already reduced to nothing`() {
        val reductions = listOf(Reduction(Kind.SHARE, -5.0), Reduction(Kind.SHARE, 0.0))

        assertEquals(listOf(-2.0, 0.0), VanillaReductions.rescale(5.0, reductions, base = 2.0, absorption = 0.0))
    }
}
