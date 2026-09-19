package net.trilleo.mc.plugins.tritown.shops

import com.palmergames.bukkit.towny.TownyAPI
import net.trilleo.mc.plugins.tritown.enums.TownyRequirement
import org.bukkit.entity.Player

/**
 * Decides whether a player may use a shop or one of its entries.
 *
 * Towny is read here and nowhere else in the feature, and it is read fresh on
 * every check: a player who joins a town mid-session sees the town's prices
 * without relogging, and nothing TriTown holds can fall out of step with Towny.
 */
object ShopAccess {

    /**
     * Every Towny requirement [player] currently satisfies.
     *
     * Read once per menu render rather than once per entry, because a shop can
     * hold dozens of entries and they all ask the same questions.
     */
    fun standing(player: Player): Set<TownyRequirement> {
        val standing = linkedSetOf(TownyRequirement.NONE)
        val resident = TownyAPI.getInstance().getResident(player)

        if (resident == null || !resident.hasTown()) {
            standing += TownyRequirement.NO_TOWN
            return standing
        }

        standing += TownyRequirement.HAS_TOWN
        if (resident.isMayor) standing += TownyRequirement.IS_MAYOR
        if (resident.hasNation()) {
            standing += TownyRequirement.HAS_NATION
            if (resident.isKing) standing += TownyRequirement.IS_KING
        }

        return standing
    }

    /** Whether [gate] lets [player] through, given the [standing] already read for them. */
    fun allows(player: Player, gate: ShopGate, standing: Set<TownyRequirement>): Boolean {
        val permission = gate.permission
        if (!permission.isNullOrBlank() && !player.hasPermission(permission)) return false
        return gate.towny in standing
    }

    /** Whether [player] may open [shop] at all. */
    fun canOpen(player: Player, shop: ShopDefinition): Boolean =
        allows(player, shop.gate, standing(player))

    /**
     * Why [gate] refused, as a translation key, or `null` when it did not.
     *
     * The permission side is deliberately vague: a player has no use for the
     * node's name, and printing it advertises the server's permission layout.
     */
    fun refusalKey(player: Player, gate: ShopGate, standing: Set<TownyRequirement>): String? {
        val permission = gate.permission
        if (!permission.isNullOrBlank() && !player.hasPermission(permission)) return "gui.shop.locked-permission"
        if (gate.towny in standing) return null

        return when (gate.towny) {
            TownyRequirement.NONE -> null
            TownyRequirement.HAS_TOWN -> "gui.shop.locked-has-town"
            TownyRequirement.NO_TOWN -> "gui.shop.locked-no-town"
            TownyRequirement.HAS_NATION -> "gui.shop.locked-has-nation"
            TownyRequirement.IS_MAYOR -> "gui.shop.locked-is-mayor"
            TownyRequirement.IS_KING -> "gui.shop.locked-is-king"
        }
    }
}
