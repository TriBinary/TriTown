package net.trilleo.mc.plugins.tritown.listeners.mobs

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent
import io.papermc.paper.event.player.PlayerNameEntityEvent
import net.trilleo.mc.plugins.tritown.combat.Combat
import net.trilleo.mc.plugins.tritown.config.MobSettings
import net.trilleo.mc.plugins.tritown.content.ContentRegistry
import net.trilleo.mc.plugins.tritown.mobs.*
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
import kotlin.random.Random

/**
 * Gives a hostile mob its level, and perhaps a rank, as it spawns, and keeps
 * them with the mob.
 *
 * Only mobs that spawn the way wild ones do are levelled — naturally, as
 * reinforcements, on patrol, or riding or being ridden. Everything else stays
 * level 1: spawners, trial spawners, eggs, commands, raids, and anything a
 * player built. So vanilla farms keep working, and only the wild gets harder
 * the further out it is. What a mob turns into takes the mob's own level,
 * whatever it was.
 */
class MobListener : Listener {

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    fun onSpawn(event: CreatureSpawnEvent) {
        val entity = event.entity
        if (entity !is Enemy || event.spawnReason !in WILD || !Combat.isActive(entity.world)) return

        val balance = ContentRegistry.balance
        val settings = MobSettings.snapshot
        val level = MobZones.levelAt(event.location)
        val rank = if (entity.type in settings.rankExempt) MobRank.NORMAL else MobRoll.rank(level, balance, Random)
        val profile = MobProfile(level, rank, MobRoll.affixes(rank, balance, Random).toSet(), settings.nameplates)

        MobProfiles.assign(entity, profile)
        settle(entity, profile)
    }

    /**
     * A zombie that drowns, a skeleton that freezes: what it becomes is the
     * same mob, at the same level and rank. A slime's children keep its level
     * but not its rank, or one champion would split into a crowd of them.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    fun onTransform(event: EntityTransformEvent) {
        val from = event.entity as? LivingEntity ?: return
        val profile = MobProfiles.of(from).takeIf { it != MobProfile.VANILLA } ?: return
        val inherited = if (event.transformReason == EntityTransformEvent.TransformReason.SPLIT) {
            profile.copy(rank = MobRank.NORMAL, affixes = emptySet())
        } else profile

        event.transformedEntities.filterIsInstance<LivingEntity>().forEach { into ->
            MobProfiles.assign(into, inherited)
            settle(into, inherited)
        }
    }

    /** A ranked mob coming back with its chunk rejoins the affix task. */
    @EventHandler
    fun onLoad(event: EntityAddToWorldEvent) {
        val entity = event.entity as? LivingEntity ?: return
        if (entity is Enemy && MobProfiles.of(entity).rank != MobRank.NORMAL) RankedMobs.track(entity)
    }

    @EventHandler
    fun onUnload(event: EntityRemoveFromWorldEvent) {
        RankedMobs.untrack(event.entity.uniqueId)
        AffixEffects.forget(event.entity.uniqueId)
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
        val profile = MobProfiles.of(entity)
        AffixEffects.onDeath(entity, profile)
        if (profile.level > 1) {
            event.droppedExp =
                (event.droppedExp * (1.0 + MobSettings.snapshot.xpPerLevel * (profile.level - 1))).roundToInt()
        }
    }

    /** Everything a mob with a fresh [profile] needs besides the profile itself. */
    private fun settle(mob: LivingEntity, profile: MobProfile) {
        if (profile.rank != MobRank.NORMAL) {
            AffixEffects.onSpawn(mob, profile)
            RankedMobs.track(mob)
        }
        if (profile.nameplate) MobNameplate.updateLater(mob)
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
