package net.trilleo.mc.plugins.tritown.listeners.gathering

import net.trilleo.mc.plugins.tritown.gathering.*
import net.trilleo.mc.plugins.tritown.protection.Protection
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.Material
import org.bukkit.Tag
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.*
import org.bukkit.event.entity.EntityChangeBlockEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.player.PlayerBucketEmptyEvent
import org.bukkit.event.player.PlayerBucketFillEvent
import org.bukkit.event.player.PlayerHarvestBlockEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.world.StructureGrowEvent

/**
 * Harvesting inside resource regions, and keeping everything else in them as
 * the administrator built it.
 *
 * Only a ripe node may be broken, and only by the town's residents. Nothing may
 * be placed, poured, tilled, stripped or trampled, and the world itself is
 * held still: nothing grows, spreads, decays, burns, melts or explodes there,
 * because TriTown does the growing. An administrator in building mode is left
 * to Towny alone.
 */
class GatherBlockListener : Listener {

    // ── Breaking ────────────────────────────────────────────────────────

    /**
     * Decides a break before Towny does. An approved harvest leaves a permit
     * so a town that denies its residents the right to destroy still lets
     * them gather; anything else is refused here and never reaches Towny.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onBreak(event: BlockBreakEvent) {
        val block = event.block
        val region = GatherManager.at(block) ?: return
        val player = event.player

        if (GatherEditors.isBuilding(player)) {
            GatherManager.clearDepleted(BlockKey.of(block))
            return
        }

        when (val outcome = Harvest.check(player, region, block)) {
            is Harvest.Outcome.Allowed -> {
                Harvest.approve(player, region, outcome.node, block)
                GatherPermits.grant(player, block.location)
            }

            else -> {
                event.isCancelled = true
                Harvest.explain(player, region, outcome)
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onHarvest(event: BlockBreakEvent) {
        Harvest.complete(event)
    }

    // ── Changing the region ─────────────────────────────────────────────

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onPlace(event: BlockPlaceEvent) {
        if (refuses(event.player, event.block)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onMultiPlace(event: BlockMultiPlaceEvent) {
        if (event.replacedBlockStates.any { refuses(event.player, it.block) }) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onEmpty(event: PlayerBucketEmptyEvent) {
        if (refuses(event.player, event.block)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onFill(event: PlayerBucketFillEvent) {
        if (refuses(event.player, event.block)) event.isCancelled = true
    }

    /** Berries picked by hand would come back on the plant's own schedule, outside the region's. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onPick(event: PlayerHarvestBlockEvent) {
        if (refuses(event.player, event.harvestedBlock)) event.isCancelled = true
    }

    /** Trampling farmland, and tools that reshape a block: stripping, tilling, paths, wax and fire. */
    @EventHandler(priority = EventPriority.LOWEST)
    fun onInteract(event: PlayerInteractEvent) {
        val block = event.clickedBlock ?: return
        val reshapes = when (event.action) {
            Action.PHYSICAL -> block.type == Material.FARMLAND
            Action.RIGHT_CLICK_BLOCK -> event.item?.type?.let(::reshapes) == true
            else -> false
        }
        if (reshapes && refuses(event.player, block, hint = event.action != Action.PHYSICAL)) {
            event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY)
            event.setUseItemInHand(org.bukkit.event.Event.Result.DENY)
        }
    }

    // ── The world ───────────────────────────────────────────────────────

    /** Mobs trampling crops, sheep eating grass, endermen lifting blocks, sand falling. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onEntityChange(event: EntityChangeBlockEvent) {
        val player = event.entity as? Player
        if (player != null && GatherEditors.isBuilding(player)) return
        if (GatherManager.at(event.block) != null) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onGrow(event: BlockGrowEvent) = still(event, event.block)

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onFertilize(event: BlockFertilizeEvent) = still(event, event.block)

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onSpread(event: BlockSpreadEvent) = still(event, event.block)

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onFade(event: BlockFadeEvent) = still(event, event.block)

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onForm(event: BlockFormEvent) = still(event, event.block)

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onDecay(event: LeavesDecayEvent) = still(event, event.block)

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onBurn(event: BlockBurnEvent) = still(event, event.block)

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onIgnite(event: BlockIgniteEvent) {
        val player = event.player
        if (player != null && GatherEditors.isBuilding(player)) return
        still(event, event.block)
    }

    /** Water or lava running in from outside would wash crops away and make obsidian of nodes. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onFlow(event: BlockFromToEvent) {
        if (GatherManager.at(event.toBlock) != null && GatherManager.at(event.block) == null) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onStructure(event: StructureGrowEvent) {
        event.blocks.removeIf { GatherManager.at(it.location) != null }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onBlockExplode(event: BlockExplodeEvent) {
        event.blockList().removeIf { GatherManager.at(it) != null }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onEntityExplode(event: EntityExplodeEvent) {
        event.blockList().removeIf { GatherManager.at(it) != null }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onPistonExtend(event: BlockPistonExtendEvent) {
        if (pistonTouches(event.block, event.blocks, event.direction)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onPistonRetract(event: BlockPistonRetractEvent) {
        if (pistonTouches(event.block, event.blocks, event.direction)) event.isCancelled = true
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    /** Whether [player] may not change [block], telling them so when [hint] is set. */
    private fun refuses(player: Player, block: Block, hint: Boolean = true): Boolean {
        if (GatherManager.at(block) == null || GatherEditors.isBuilding(player)) return false
        if (hint) Protection.hint(player, player.tr("gathering.hint.protected"))
        return true
    }

    private fun still(event: org.bukkit.event.Cancellable, block: Block) {
        if (GatherManager.at(block) != null) event.isCancelled = true
    }

    private fun pistonTouches(piston: Block, moved: List<Block>, direction: org.bukkit.block.BlockFace): Boolean =
        GatherManager.at(piston) != null ||
                moved.any { GatherManager.at(it) != null || GatherManager.at(it.getRelative(direction)) != null }

    private fun reshapes(item: Material): Boolean =
        Tag.ITEMS_AXES.isTagged(item) || Tag.ITEMS_SHOVELS.isTagged(item) || Tag.ITEMS_HOES.isTagged(item) ||
                item in RESHAPING

    private companion object {
        val RESHAPING = setOf(Material.BONE_MEAL, Material.HONEYCOMB, Material.FLINT_AND_STEEL, Material.FIRE_CHARGE)
    }
}
