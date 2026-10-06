package net.trilleo.mc.plugins.tritown.gathering

import net.kyori.adventure.title.Title
import net.trilleo.mc.plugins.tritown.config.GatheringSettings
import net.trilleo.mc.plugins.tritown.utils.ComponentUtil
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Tells a player when they walk into a resource region: its name, what can be
 * gathered there, and whether they may.
 *
 * Checked once a second rather than on every step, which is quick enough for
 * someone walking in and costs nothing while nobody moves.
 */
object GatherNotices {

    private val inside = ConcurrentHashMap<UUID, String>()
    private val times = Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(1800), Duration.ofMillis(500))

    fun tick() {
        val titles = GatheringSettings.snapshot.entryTitles
        for (player in Bukkit.getOnlinePlayers()) {
            val region = GatherManager.at(player.location)
            val previous = inside[player.uniqueId]
            if (region?.id == previous) continue

            if (region == null) inside.remove(player.uniqueId) else inside[player.uniqueId] = region.id
            if (region != null && titles) announce(player, region)
        }
    }

    fun forget(player: Player) {
        inside.remove(player.uniqueId)
    }

    private fun announce(player: Player, region: ResourceRegion) {
        val refusal = GatherAccess.refusal(player, region, player.location)
        val subtitle = if (refusal == null) {
            val categories = region.categories().joinToString(player.tr("gathering.list-separator")) { player.tr(it.key) }
            player.tr("gathering.enter.open", "categories" to categories.ifEmpty { player.tr("common.none") })
        } else {
            GatherAccess.message(player, region, refusal)
        }

        player.showTitle(
            Title.title(
                ComponentUtil.parse(player.tr("gathering.enter.title", "name" to region.name)),
                ComponentUtil.parse(subtitle),
                times,
            )
        )
    }
}
