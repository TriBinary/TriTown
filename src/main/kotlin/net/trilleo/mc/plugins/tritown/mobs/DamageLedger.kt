package net.trilleo.mc.plugins.tritown.mobs

import java.util.*

/**
 * How much of each wild mob's health players have taken, and who took it.
 *
 * A mob only drops more than vanilla does when players dealt enough of its
 * damage, so a trap, a lava pit or another mob doing the work earns nothing.
 * A tamed pet's damage counts for its owner. Kept in memory: a mob that
 * unloads mid-fight starts over, and forgetting it on death or unload is the
 * listener's job. Main thread only.
 */
class DamageLedger {

    private val credit = HashMap<UUID, HashMap<UUID, Double>>()

    /** Records that [player] took [amount] RPG health off [mob]. */
    fun credit(mob: UUID, player: UUID, amount: Double) {
        if (amount <= 0.0) return
        credit.getOrPut(mob, ::HashMap).merge(player, amount, Double::plus)
    }

    /** How much of a pool of [max] players have dealt to [mob], from 0 to 1. */
    fun share(mob: UUID, max: Double): Double {
        if (max <= 0.0) return 0.0
        return (credit[mob]?.values?.sum() ?: 0.0) / max
    }

    fun forget(mob: UUID) {
        credit -= mob
    }
}
