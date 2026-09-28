package net.trilleo.mc.plugins.tritown.combat

/**
 * Carries vanilla's damage reductions over to a hit whose size TriTown has
 * decided, with armor taken out.
 *
 * A player's worn armor is already their Defense, which the RPG hit has been
 * through, so vanilla's armor reduction would count it twice. Everything else
 * vanilla reduces a hit by keeps its share: invulnerability frames, a helmet
 * under a falling anvil, a raised shield, Resistance, and Protection and its
 * kin. Each of those takes the same fraction of what reaches it as it took of
 * vanilla's own hit, and absorption soaks up what it can hold.
 *
 * The event's own recalculation cannot do this: it would re-run the armor it
 * is asked to leave out.
 */
object VanillaReductions {

    enum class Kind { SHARE, ARMOR, ABSORPTION }

    class Reduction(val kind: Kind, val value: Double)

    /**
     * The values [reductions] should take for a hit of [base] instead of
     * [originalBase]. Reductions are negative, and come in the order the
     * server applies them, each one working on what the ones before left.
     *
     * @param absorption the absorption the target has to spend
     */
    fun rescale(originalBase: Double, reductions: List<Reduction>, base: Double, absorption: Double): List<Double> {
        var reachedBefore = originalBase
        var reachesNow = base
        return reductions.map { reduction ->
            val value = when (reduction.kind) {
                Kind.ARMOR -> 0.0
                Kind.ABSORPTION -> -minOf(absorption.coerceAtLeast(0.0), reachesNow.coerceAtLeast(0.0))
                Kind.SHARE -> if (reachedBefore > 0.0) reduction.value / reachedBefore * reachesNow else 0.0
            }
            reachedBefore += reduction.value
            reachesNow += value
            value
        }
    }
}
