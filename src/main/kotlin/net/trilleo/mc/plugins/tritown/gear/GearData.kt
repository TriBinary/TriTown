package net.trilleo.mc.plugins.tritown.gear

import net.trilleo.mc.plugins.tritown.combat.Stat
import net.trilleo.mc.plugins.tritown.content.Rarity

/**
 * What a piece of gear is: which piece, and what happened to it since it was
 * made. **Never its stats**, which [GearStats] works out from this and the
 * content files every time, so retuning `gear.yml` or `balance.yml` reaches
 * every piece already out there.
 *
 * @param rolls the quality, in percent, each stat rolled when the piece was made
 * @param reforge the id of its reforge, if it has one
 * @param revision the content stamp its tooltip was last drawn with
 */
data class GearData(
    val id: String,
    val rarity: Rarity,
    val stars: Int,
    val rolls: Map<Stat, Int>,
    val reforge: String?,
    val revision: Int,
)
