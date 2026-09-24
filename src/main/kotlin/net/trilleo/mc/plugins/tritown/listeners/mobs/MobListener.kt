package net.trilleo.mc.plugins.tritown.listeners.mobs

import io.papermc.paper.event.player.PlayerNameEntityEvent
import net.trilleo.mc.plugins.tritown.combat.Combat
import net.trilleo.mc.plugins.tritown.config.MobSettings
import net.trilleo.mc.plugins.tritown.mobs.MobNameplate
import net.trilleo.mc.plugins.tritown.mobs.MobProfiles
import net.trilleo.mc.plugins.tritown.mobs.MobZones
import org.bukkit.entity.Enemy
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.CreatureSpawnEvent
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.EntityRegainHealthEvent
import org.bukkit.event.entity.EntityTransformEvent
import kotlin.math.roundToInt

/**
 * Gives a hostile mob its level as it spawns, and keeps it with the mob.
 *
 * Only mobs that spawn the way wild ones do are levelled — naturally, as
 * reinforcements, on patrol, or riding or being ridden. Everything else stays
 * level 1: spawners, trial spawners, eggs, commands, raids, and anything a
 * player built. So vanilla farms keep working, and only the wild gets harder
 * the further out it is. What a mob turns into — a slime's children included —
 * takes the mob's own level, whatever it was.
 */
class MobListener : Listener {

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    fun onSpawn(event: CreatureSpawnEvent) {
        val entity = event.entity
        if (entity !is Enemy || event.spawnReason !in WILD || !Combat.isActive(entity.world)) return

        val nameplate = MobSettings.snapshot.nameplates
        MobProfiles.assign(entity, MobZones.levelAt(event.location), nameplate)
        if (nameplate) MobNameplate.updateLater(entity)
    }

    /** A zombie that drowns, a slime that splits: what it becomes is the same mob, at the same level. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    fun onTransform(event: EntityTransformEvent) {
        val from = event.entity as? LivingEntity ?: return
        event.transformedEntities.filterIsInstance<LivingEntity>().forEach { into ->
            MobProfiles.copy(from, into)
            MobNameplate.updateLater(into)
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    fun onName(event: PlayerNameEntityEvent) = MobProfiles.dropNameplate(event.entity)

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    fun onHeal(event: EntityRegainHealthEvent) {
        val entity = event.entity as? LivingEntity ?: return
        if (entity !is Player) MobNameplate.updateLater(entity)
    }

    @EventHandler(ignoreCancelled = true)
    fun onDeath(event: EntityDeathEvent) {
        val entity = event.entity
        if (entity is Player) return
        val level = MobProfiles.level(entity)
        if (level <= 1) return
        event.droppedExp = (event.droppedExp * (1.0 + MobSettings.snapshot.xpPerLevel * (level - 1))).roundToInt()
    }

    private companion object {
        val WILD = setOf(
            SpawnReason.NATURAL,
            SpawnReason.REINFORCEMENTS,
            SpawnReason.PATROL,
            SpawnReason.JOCKEY,
            SpawnReason.MOUNT,
        )
    }
}
