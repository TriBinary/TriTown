package net.trilleo.mc.plugins.tritown.shops

import net.trilleo.mc.plugins.tritown.enums.TownyRequirement

/**
 * Who may use a shop, or one entry inside it.
 *
 * [permission] is written by whoever set the shop up, so it cannot be registered
 * at startup the way a command's nodes are — it is checked as it stands and
 * defined in the server's permissions plugin.
 *
 * @param permission     a node the player must hold, or `null` for no check
 * @param towny          what the player's standing in Towny must be
 * @param hideWhenLocked whether a player who fails the check sees the entry greyed out or not at all
 */
data class ShopGate(
    val permission: String? = null,
    val towny: TownyRequirement = TownyRequirement.NONE,
    val hideWhenLocked: Boolean = false,
) {

    /** Whether this gate checks anything at all. */
    val isOpen: Boolean get() = permission.isNullOrBlank() && towny == TownyRequirement.NONE

    companion object {
        /** A gate that lets everybody through. */
        val OPEN = ShopGate()
    }
}
