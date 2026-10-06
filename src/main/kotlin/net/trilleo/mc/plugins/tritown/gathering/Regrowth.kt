package net.trilleo.mc.plugins.tritown.gathering

import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.trilleo.mc.plugins.tritown.Main
import net.trilleo.mc.plugins.tritown.config.GatheringSettings
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.block.Block
import org.bukkit.block.data.Ageable
import org.bukkit.block.data.BlockData
import java.util.*

/**
 * Taking a node out of the world when it is harvested, and putting it back.
 *
 * A harvested node is written down with what it was and what stands in its
 * place, then the stand-in is put down a tick later, once the game has
 * finished breaking the block. A crop is put back as a seedling and grows
 * through its stages as its time runs; anything else waits as its stand-in.
 *
 * Growing back never overwrites a rebuilt spot: if the block is no longer the
 * stand-in TriTown left, the record is dropped and the block left alone.
 */
object Regrowth {

    private const val MAX_COLUMN = 16

    private val random = Random()
    private val growSound = Sound.sound(Key.key("minecraft:block.amethyst_block.chime"), Sound.Source.BLOCK, 0.6f, 1.4f)

    /**
     * Records [block], about to be broken as [node], and puts its stand-in down
     * on the next tick. A column plant takes everything of its kind stacked on
     * top of it along, since the game breaks those too.
     */
    fun deplete(region: ResourceRegion, node: ResourceNode, block: Block) {
        val now = System.currentTimeMillis()
        val regrowAt = now + node.regrowSeconds * 1000L
        val original = block.blockData.clone()
        val standIn = standIn(node, original)

        GatherManager.markDepleted(
            DepletedNode(BlockKey.of(block), region.id, node.id, original.asString, standIn.material.name, now, regrowAt)
        )

        if (node.isColumn) {
            var above = block.getRelative(0, 1, 0)
            var count = 0
            while (above.type == node.block && count++ < MAX_COLUMN) {
                GatherManager.markDepleted(
                    DepletedNode(
                        BlockKey.of(above), region.id, node.id, above.blockData.asString,
                        Material.AIR.name, now, regrowAt,
                    )
                )
                above = above.getRelative(0, 1, 0)
            }
        }

        Bukkit.getScheduler().runTask(Main.instance, Runnable {
            if (block.type.isAir || block.type == node.block) block.setBlockData(standIn, false)
        })
    }

    /**
     * Moves every harvested node on: puts back those that are due, and steps
     * a growing crop on to the stage its time has reached. A node in an
     * unloaded chunk waits for it to load.
     */
    fun tick() {
        val now = System.currentTimeMillis()
        for (node in GatherManager.allDepleted().toList()) {
            if (!node.key.isLoaded()) continue
            if (now >= node.regrowAt) regrow(node, effects = true) else grow(node, now)
        }
    }

    /** Puts back everything [region] has harvested, at once. */
    fun regrowAll(region: ResourceRegion): Int {
        val nodes = GatherManager.depletedIn(region)
        nodes.forEach { regrow(it, effects = false) }
        return nodes.size
    }

    /** How long until the node at [block] is back, in whole seconds, or `null` when it is not growing back. */
    fun secondsLeft(block: Block): Long? = GatherManager.depletedAt(block)?.let { node ->
        ((node.regrowAt - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L) + 1L
    }

    private fun regrow(node: DepletedNode, effects: Boolean) {
        GatherManager.clearDepleted(node.key)
        val block = node.key.block() ?: return
        if (block.type.name != node.placed) return

        val original = runCatching { Bukkit.createBlockData(node.original) }.getOrNull() ?: return
        val rule = GatherManager.get(node.regionId)?.node(node.nodeId)
        val data = rule?.rollRegrowth(random)?.let { material ->
            if (material == original.material) original else material.createBlockData()
        } ?: original

        block.setBlockData(data, false)
        if (effects && GatheringSettings.snapshot.effects && !data.material.isAir) {
            val center = block.location.toCenterLocation()
            block.world.spawnParticle(Particle.HAPPY_VILLAGER, center, 8, 0.35, 0.35, 0.35, 0.0)
            block.world.playSound(growSound, center.x, center.y, center.z)
        }
    }

    /** Steps a crop on to the stage its time has reached, without ever ripening it early. */
    private fun grow(node: DepletedNode, now: Long) {
        val block = node.key.block() ?: return
        if (block.type.name != node.placed) return
        val data = block.blockData as? Ageable ?: return
        if (data.maximumAge <= 0) return

        val stage = (node.progress(now) * data.maximumAge).toInt().coerceIn(0, data.maximumAge - 1)
        if (stage <= data.age) return
        data.age = stage
        block.setBlockData(data, false)
    }

    private fun standIn(node: ResourceNode, original: BlockData): BlockData {
        if (node.growsInStages && original is Ageable) {
            return (original.clone() as Ageable).apply { age = 0 }
        }
        return (node.depleted ?: GatheringSettings.snapshot.depletedFor(node.category)).createBlockData()
    }
}
