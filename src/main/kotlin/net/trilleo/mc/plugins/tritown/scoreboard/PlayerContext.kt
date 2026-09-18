package net.trilleo.mc.plugins.tritown.scoreboard

import com.palmergames.bukkit.towny.`object`.Nation
import com.palmergames.bukkit.towny.`object`.Resident
import com.palmergames.bukkit.towny.`object`.Town
import com.palmergames.bukkit.towny.`object`.TownBlock
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.entity.Player

/**
 * Everything the sidebar knows about a player at one moment: who they are to
 * Towny, and whose land they are standing on.
 *
 * Built fresh for every render by [ContextResolver] and never cached, so a
 * board can never show a town that has since been deleted. A board picks itself
 * by testing a [BoardCondition] against this, and placeholders read their
 * values out of it.
 *
 * @param town      the player's own town, or `null` when they have none
 * @param nation    the player's own nation, or `null`
 * @param townBlock the claim the player is standing in; `null` means wilderness
 * @param plotTown  the town that owns [townBlock], which may not be [town]
 * @param plotOwner the resident who owns [townBlock], or `null` when unowned
 */
data class PlayerContext(
    val player: Player,
    val resident: Resident?,
    val town: Town?,
    val nation: Nation?,
    val townBlock: TownBlock?,
    val plotTown: Town?,
    val plotOwner: Resident?,
) {

    /** Whether the player is standing outside every town's claims. */
    val isWilderness: Boolean
        get() = townBlock == null

    /** Whether the player is standing in their own town's claims. */
    val isInOwnTown: Boolean
        get() = plotTown != null && plotTown == town

    /** Whether the player owns the plot they are standing in. */
    val isInOwnPlot: Boolean
        get() = plotOwner != null && plotOwner == resident

    /** Whether the player is standing in claims belonging to a town that is not theirs. */
    val isInOtherTown: Boolean
        get() = plotTown != null && plotTown != town

    /** Whether the plot's town belongs to a nation allied with the player's. */
    val isInAllyTown: Boolean
        get() = plotNation?.let { nation != null && it != nation && nation.allies.contains(it) } == true

    /** Whether the plot's town belongs to a nation at war with the player's. */
    val isInEnemyTown: Boolean
        get() = plotNation?.let { nation != null && nation.enemies.contains(it) } == true

    private val plotNation: Nation?
        get() = plotTown?.nationOrNull

    /** What a placeholder shows when the value it reads does not exist, in the viewer's language. */
    fun none(): String = player.tr("common.none")
}
