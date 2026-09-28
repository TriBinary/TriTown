package net.trilleo.mc.plugins.tritown.mobs

import net.trilleo.mc.plugins.tritown.config.MobSettings
import net.trilleo.mc.plugins.tritown.content.MobKindDef
import org.bukkit.Location
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason

/** Setting a mob up once TriTown has decided what it is. */
object MobSetup {

    /** Everything a mob with a fresh [profile] needs besides the profile itself. */
    fun settle(mob: LivingEntity, profile: MobProfile) {
        MobKinds.def(profile)?.let { MobKinds.dress(mob, it) }
        if (profile.isActive) {
            AffixEffects.onSpawn(mob, profile)
            ActiveMobs.track(mob)
        }
        if (profile.nameplate) MobNameplate.updateLater(mob)
    }

    /**
     * Spawns a mob of [type] at [level] that TriTown calls up itself — a
     * summoner's minion, or one an administrator asks for — as [kind] if one is
     * given, with the affixes the kind always has. It is unranked, and never
     * eligible: it drops nothing beyond vanilla's loot.
     */
    fun spawn(at: Location, type: EntityType, level: Int, kind: MobKindDef?): LivingEntity? {
        val profile = MobProfile(
            level = level,
            rank = MobRank.NORMAL,
            affixes = kind?.affixes.orEmpty(),
            nameplate = MobSettings.snapshot.nameplates,
            eligible = false,
            kind = kind?.id,
        )
        val entity = at.world.spawnEntity(at, type, SpawnReason.CUSTOM) { spawned ->
            (spawned as? LivingEntity)?.let { MobProfiles.assign(it, profile) }
        }
        val mob = entity as? LivingEntity ?: run {
            entity.remove()
            return null
        }
        settle(mob, profile)
        return mob
    }
}
