package net.trilleo.mc.plugins.tritown.gathering

import net.trilleo.mc.plugins.tritown.Main
import net.trilleo.mc.plugins.tritown.protection.ItemOwnership
import net.trilleo.mc.plugins.tritown.protection.Protection
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.Bukkit
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.event.block.BlockBreakEvent
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * Breaking a block inside a resource region.
 *
 * The decision is made in [check], before Towny looks at the break, and the
 * harvest itself in [complete], once every plugin has had its say. Between the
 * two the approved break is remembered here, so nothing that was refused — or
 * that some other plugin cancelled — is ever counted, dropped or depleted.
 */
object Harvest {

    /** What [check] made of a break. */
    sealed interface Outcome {
        data class Allowed(val node: ResourceNode) : Outcome
        data class Refused(val refusal: GatherAccess.Refusal) : Outcome
        data class Regrowing(val seconds: Long) : Outcome
        data object Unripe : Outcome
        data object Protected : Outcome
    }

    private data class Pending(val key: BlockKey, val regionId: String, val nodeId: String, val tick: Int)

    private val pending = ConcurrentHashMap<UUID, Pending>()

    /** What breaking [block] in [region] means for [player]. */
    fun check(player: Player, region: ResourceRegion, block: Block): Outcome {
        val regrowing = Regrowth.secondsLeft(block)
        val node = region.nodeFor(block.type)
        if (regrowing == null && node == null) return Outcome.Protected
        GatherAccess.refusal(player, region, block.location)?.let { return Outcome.Refused(it) }
        if (regrowing != null) return Outcome.Regrowing(regrowing)
        if (node == null || !node.isRipe(block)) return Outcome.Unripe
        return Outcome.Allowed(node)
    }

    /** Tells [player] why a break was refused. */
    fun explain(player: Player, region: ResourceRegion, outcome: Outcome) {
        when (outcome) {
            is Outcome.Refused -> GatherAccess.hint(player, region, outcome.refusal)
            is Outcome.Regrowing -> Protection.hint(player, player.tr("gathering.hint.regrowing", "seconds" to outcome.seconds))
            Outcome.Unripe -> Protection.hint(player, player.tr("gathering.hint.unripe"))
            Outcome.Protected -> Protection.hint(player, player.tr("gathering.hint.protected"))
            is Outcome.Allowed -> Unit
        }
    }

    /** Remembers that [player] was cleared to harvest [block] as [node] this tick. */
    fun approve(player: Player, region: ResourceRegion, node: ResourceNode, block: Block) {
        pending[player.uniqueId] = Pending(BlockKey.of(block), region.id, node.id, Bukkit.getCurrentTick())
    }

    /**
     * Harvests the block [event] breaks, if it was approved: swaps vanilla's
     * drops for the node's when it says so, rolls the extra drops, and leaves
     * a stand-in to grow back.
     */
    fun complete(event: BlockBreakEvent) {
        val player = event.player
        val approved = pending.remove(player.uniqueId) ?: return
        val block = event.block
        if (approved.tick != Bukkit.getCurrentTick() || approved.key != BlockKey.of(block)) return

        val region = GatherManager.get(approved.regionId) ?: return
        val node = region.node(approved.nodeId) ?: return

        if (!node.vanillaDrops) event.isDropItems = false
        val extras = node.drops.filter { Random.nextDouble(100.0) < it.chance }.map { it.item.clone() }
        if (extras.isNotEmpty()) {
            val at = block.location.toCenterLocation()
            Bukkit.getScheduler().runTask(Main.instance, Runnable {
                if (player.isOnline) extras.forEach { ItemOwnership.dropFor(player, it, at) }
            })
        }

        Regrowth.deplete(region, node, block)
        GatherStats.add(player, node.category)
    }

    fun forget(player: Player) {
        pending.remove(player.uniqueId)
    }
}
