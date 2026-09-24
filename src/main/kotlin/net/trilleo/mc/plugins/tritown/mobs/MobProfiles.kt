package net.trilleo.mc.plugins.tritown.mobs

import org.bukkit.NamespacedKey
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType

/**
 * What TriTown decided about a mob when it spawned, kept in the mob's own
 * persistent data under `tritown:mob`.
 *
 * The game saves it with the entity, so a mob keeps its level through chunk
 * unloads and restarts without any file of TriTown's. Only a mob that spawned
 * the way a wild one does gets a profile. One with none — from a spawner, an
 * egg, a command, or from before TriTown was installed — is level 1, which is
 * to say vanilla.
 */
object MobProfiles {

    private val PROFILE = NamespacedKey("tritown", "mob")
    private val LEVEL = NamespacedKey("tritown", "level")
    private val NAMEPLATE = NamespacedKey("tritown", "nameplate")

    fun level(entity: LivingEntity): Int {
        if (entity is Player) return 1
        return profile(entity)?.get(LEVEL, PersistentDataType.INTEGER)?.coerceAtLeast(1) ?: 1
    }

    /** Whether the mob's name is TriTown's nameplate, rather than a name a player gave it or none at all. */
    fun hasNameplate(entity: LivingEntity): Boolean =
        profile(entity)?.get(NAMEPLATE, PersistentDataType.BOOLEAN) == true

    fun assign(entity: LivingEntity, level: Int, nameplate: Boolean) {
        val profile = entity.persistentDataContainer.adapterContext.newPersistentDataContainer()
        profile.set(LEVEL, PersistentDataType.INTEGER, level)
        profile.set(NAMEPLATE, PersistentDataType.BOOLEAN, nameplate)
        entity.persistentDataContainer.set(PROFILE, PersistentDataType.TAG_CONTAINER, profile)
    }

    /** Hands [from]'s profile to what it turned into: a zombie drowning, a slime splitting. */
    fun copy(from: LivingEntity, to: LivingEntity) {
        val profile = profile(from) ?: return
        to.persistentDataContainer.set(PROFILE, PersistentDataType.TAG_CONTAINER, profile)
    }

    /** Stops drawing a nameplate on the mob, because a player has named it. */
    fun dropNameplate(entity: LivingEntity) {
        val profile = profile(entity) ?: return
        profile.set(NAMEPLATE, PersistentDataType.BOOLEAN, false)
        entity.persistentDataContainer.set(PROFILE, PersistentDataType.TAG_CONTAINER, profile)
    }

    private fun profile(entity: LivingEntity): PersistentDataContainer? =
        entity.persistentDataContainer.get(PROFILE, PersistentDataType.TAG_CONTAINER)
}
