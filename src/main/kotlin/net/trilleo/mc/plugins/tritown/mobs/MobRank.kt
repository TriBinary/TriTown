package net.trilleo.mc.plugins.tritown.mobs

import net.trilleo.mc.plugins.tritown.content.Balance

/** How much tougher than its kind a mob spawned: most are [NORMAL]. */
enum class MobRank {
    NORMAL, ELITE, CHAMPION;

    /**
     * The translation key of the rank's badge on a nameplate, spelled out so the
     * language test can see it. A normal mob wears none.
     */
    val badgeKey: String?
        get() = when (this) {
            NORMAL -> null
            ELITE -> "mob.rank.elite"
            CHAMPION -> "mob.rank.champion"
        }

    /** What `balance.yml` says this rank is worth, or `null` for [NORMAL], which is worth nothing extra. */
    fun tuning(balance: Balance): Balance.Rank? = when (this) {
        NORMAL -> null
        ELITE -> balance.ranks.elite
        CHAMPION -> balance.ranks.champion
    }

    fun health(balance: Balance): Double = tuning(balance)?.health ?: 1.0

    fun damage(balance: Balance): Double = tuning(balance)?.damage ?: 1.0
}
