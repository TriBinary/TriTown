package net.trilleo.mc.plugins.tritown.mobs

import org.bukkit.entity.LivingEntity
import java.util.*

/**
 * Every mob in a loaded chunk that takes a turn in the mob task — one with a
 * rank, a custom kind or an affix ([MobProfile.isActive]) — so `MobTurnTask` can
 * give each its turn without walking every entity in every world.
 *
 * Filled as such mobs spawn or load back in, emptied as they unload or die;
 * `MobListener` keeps it in step. Main thread only.
 */
object ActiveMobs {

    private val mobs = HashMap<UUID, LivingEntity>()

    fun track(mob: LivingEntity) {
        mobs[mob.uniqueId] = mob
    }

    fun untrack(mob: UUID) {
        mobs -= mob
    }

    fun all(): List<LivingEntity> = mobs.values.toList()
}
