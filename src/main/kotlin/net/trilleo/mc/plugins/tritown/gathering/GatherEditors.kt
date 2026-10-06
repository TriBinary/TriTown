package net.trilleo.mc.plugins.tritown.gathering

import net.trilleo.mc.plugins.tritown.Main
import net.trilleo.mc.plugins.tritown.utils.Lang
import net.trilleo.mc.plugins.tritown.utils.itemStack
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * What administrators setting regions up are part-way through: the corners
 * they have picked with the wand, and whether they are building.
 *
 * Building lifts a region's protection for that administrator alone, so a
 * mine can be dug out or a farm laid without anything counting as a harvest.
 * Towny's own rules still apply to them.
 */
object GatherEditors {

    /** Setting regions up: the wand, building mode and every `/tritown gather` action. */
    const val ADMIN_PERMISSION = "tritown.gather.admin"

    private val WAND = NamespacedKey("tritown", "gather-wand")
    private const val OUTLINE_STEP = 1.0
    private const val OUTLINE_MAX_POINTS = 3000
    private const val OUTLINE_PERIOD = 10L

    class Selection(var first: Location? = null, var second: Location? = null) {
        fun area(): Cuboid? {
            val a = first ?: return null
            val b = second ?: return null
            if (a.world != b.world) return null
            return Cuboid.of(a, b)
        }
    }

    private val selections = ConcurrentHashMap<UUID, Selection>()
    private val building = ConcurrentHashMap.newKeySet<UUID>()

    fun selection(player: Player): Selection = selections.getOrPut(player.uniqueId) { Selection() }

    fun isBuilding(player: Player): Boolean = player.uniqueId in building

    /** Turns building on or off for [player], and returns whether it is now on. */
    fun toggleBuilding(player: Player): Boolean =
        if (building.remove(player.uniqueId)) false else building.add(player.uniqueId)

    fun forget(player: Player) {
        selections.remove(player.uniqueId)
        building.remove(player.uniqueId)
    }

    fun wand(): ItemStack = itemStack(Material.BLAZE_ROD) {
        name(Lang.item("gathering.wand.name"))
        lore(Lang.item("gathering.wand.lore"))
        pdc(WAND, PersistentDataType.BYTE, 1.toByte())
    }

    fun isWand(item: ItemStack?): Boolean =
        item != null && !item.type.isAir && item.persistentDataContainer.has(WAND, PersistentDataType.BYTE)

    /**
     * Traces the edges of [area] in particles that only [player] sees, for
     * [seconds]. A very large box is traced more sparsely rather than not at all.
     */
    fun outline(player: Player, area: Cuboid, seconds: Int, color: Color = Color.LIME) {
        val world = area.bukkitWorld() ?: return
        val points = edges(area)
        val stride = (points.size / OUTLINE_MAX_POINTS + 1)
        val dust = Particle.DustOptions(color, 1.2f)
        val runs = (seconds * 20L / OUTLINE_PERIOD).toInt()

        var done = 0
        lateinit var task: org.bukkit.scheduler.BukkitTask
        task = Bukkit.getScheduler().runTaskTimer(Main.instance, Runnable {
            if (!player.isOnline || player.world != world || done++ >= runs) {
                task.cancel()
                return@Runnable
            }
            for (i in points.indices step stride) {
                val (x, y, z) = points[i]
                player.spawnParticle(Particle.DUST, x, y, z, 1, 0.0, 0.0, 0.0, 0.0, dust)
            }
        }, 0L, OUTLINE_PERIOD)
    }

    private fun edges(area: Cuboid): List<Triple<Double, Double, Double>> {
        val x0 = area.minX.toDouble()
        val y0 = area.minY.toDouble()
        val z0 = area.minZ.toDouble()
        val x1 = area.maxX + 1.0
        val y1 = area.maxY + 1.0
        val z1 = area.maxZ + 1.0

        fun line(from: Triple<Double, Double, Double>, to: Triple<Double, Double, Double>) = buildList {
            val length = maxOf(to.first - from.first, to.second - from.second, to.third - from.third)
            var t = 0.0
            while (t <= length) {
                val f = if (length == 0.0) 0.0 else t / length
                add(
                    Triple(
                        from.first + (to.first - from.first) * f,
                        from.second + (to.second - from.second) * f,
                        from.third + (to.third - from.third) * f,
                    )
                )
                t += OUTLINE_STEP
            }
        }

        val corners = listOf(x0, x1).flatMap { x -> listOf(y0, y1).flatMap { y -> listOf(z0, z1).map { z -> Triple(x, y, z) } } }
        return buildList {
            for (a in corners) for (b in corners) {
                val differ = listOf(a.first != b.first, a.second != b.second, a.third != b.third).count { it }
                if (differ == 1 && (a.first <= b.first && a.second <= b.second && a.third <= b.third)) addAll(line(a, b))
            }
        }
    }
}
