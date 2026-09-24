package net.trilleo.mc.plugins.tritown.mobs

import net.trilleo.mc.plugins.tritown.Main
import net.trilleo.mc.plugins.tritown.combat.CombatFormat
import net.trilleo.mc.plugins.tritown.combat.CombatHealth
import net.trilleo.mc.plugins.tritown.utils.ComponentUtil
import net.trilleo.mc.plugins.tritown.utils.Lang
import org.bukkit.Bukkit
import org.bukkit.entity.LivingEntity
import java.util.*

/**
 * The name a levelled mob wears: its level, its kind and its RPG health.
 *
 * A name is shared by everyone who sees the mob, so its frame is in the
 * server's configured language. The kind is written as the game's own
 * translation key, which each client fills in in its own language.
 *
 * It shows when a player looks straight at the mob, the way a named mob's
 * does. Setting it does not make the mob persistent: only a name tag does
 * that, and a mob a player names keeps their name and loses the nameplate.
 */
object MobNameplate {

    private val pending = HashSet<UUID>()

    fun update(entity: LivingEntity) {
        if (!entity.isValid || !MobProfiles.hasNameplate(entity)) return
        val text = Lang.tr(
            null,
            "mob.nameplate",
            "level" to MobProfiles.level(entity),
            "name" to "<lang:${entity.type.translationKey()}>",
            "health" to CombatFormat.health(CombatHealth.current(entity)),
            "max" to CombatFormat.number(CombatHealth.max(entity)),
        )
        entity.customName(ComponentUtil.parse(text))
    }

    /** [update] on the next tick, once the hit or heal in progress has landed. Asked for twice, it runs once. */
    fun updateLater(entity: LivingEntity) {
        if (!MobProfiles.hasNameplate(entity) || !pending.add(entity.uniqueId)) return
        Bukkit.getScheduler().runTask(Main.instance, Runnable {
            pending.remove(entity.uniqueId)
            update(entity)
        })
    }
}
