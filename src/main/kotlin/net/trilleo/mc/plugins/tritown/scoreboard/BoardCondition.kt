package net.trilleo.mc.plugins.tritown.scoreboard

import net.trilleo.mc.plugins.tritown.utils.TownyUtil

/**
 * When a board applies.
 *
 * Each board in `config.yml` names one of these. The sidebar shows the
 * highest-priority board whose condition matches, which is what lets the same
 * player see different information in their own town, in someone else's
 * claims, and out in the wild.
 */
enum class BoardCondition(val id: String) {

    /** Always applies; use it as the lowest-priority fallback. */
    ALWAYS("always"),

    /** The player has no town. */
    NO_TOWN("no-town"),

    /** The player belongs to a town. */
    HAS_TOWN("has-town"),

    /** The player's town belongs to no nation, or they have no town. */
    NO_NATION("no-nation"),

    /** The player belongs to a nation. */
    HAS_NATION("has-nation"),

    /** The player is standing outside every town's claims. */
    IN_WILDERNESS("in-wilderness"),

    /** The player is standing in their own town's claims. */
    IN_OWN_TOWN("in-own-town"),

    /** The player is standing in a plot they own. */
    IN_OWN_PLOT("in-own-plot"),

    /** The player is standing in another town's claims. */
    IN_OTHER_TOWN("in-other-town"),

    /** The player is standing in a town allied with their nation. */
    IN_ALLY_TOWN("in-ally-town"),

    /** The player is standing in a town their nation is at war with. */
    IN_ENEMY_TOWN("in-enemy-town"),

    /** The player's town is ruined, bankrupt, conquered, overclaimed, or cannot pay its upkeep. */
    TOWN_HAS_WARNING("town-has-warning");

    /** Whether this condition holds for [context]. */
    fun matches(context: PlayerContext): Boolean = when (this) {
        ALWAYS -> true
        NO_TOWN -> context.town == null
        HAS_TOWN -> context.town != null
        NO_NATION -> context.nation == null
        HAS_NATION -> context.nation != null
        IN_WILDERNESS -> context.isWilderness
        IN_OWN_TOWN -> context.isInOwnTown
        IN_OWN_PLOT -> context.isInOwnPlot
        IN_OTHER_TOWN -> context.isInOtherTown
        IN_ALLY_TOWN -> context.isInAllyTown
        IN_ENEMY_TOWN -> context.isInEnemyTown
        TOWN_HAS_WARNING -> context.town?.let {
            it.isRuined || it.isBankrupt || it.isConquered || it.isOverClaimed || TownyUtil.cannotAffordUpkeep(it)
        } == true
    }

    companion object {

        /** The condition written as [id] in `config.yml`, or `null` when no condition has that name. */
        fun parse(id: String): BoardCondition? = entries.firstOrNull { it.id.equals(id.trim(), ignoreCase = true) }

        /** Every condition name, for tab completion and error messages. */
        fun ids(): List<String> = entries.map { it.id }
    }
}
