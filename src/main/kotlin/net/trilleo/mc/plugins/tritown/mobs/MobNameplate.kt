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
 * The name a levelled mob wears: its level, its rank and affixes, its kind and
 * its RPG health.
 *
 * A name is shared by everyone who sees the mob, so its frame is in the
 * server's configured language. The kind is written as the game's own
 * translation key, which each client fills in in its own language.
 *
 * A normal mob's shows when a player looks straight at it, the way a named
 * mob's does; a ranked mob's always shows, so nobody walks into a champion
 * unwarned. Setting it does not make the mob persistent: only a name tag does
 * that, and a mob a player names keeps their name and loses the nameplate.
 */
object MobNameplate {

    private val pending = HashSet<UUID>()

    fun update(entity: LivingEntity) {
        if (!entity.isValid) return
        val profile = MobProfiles.of(entity)
        if (!profile.nameplate) return

        val text = Lang.tr(
            null,
            "mob.nameplate",
            "level" to profile.level,
            "badge" to (profile.rank.badgeKey?.let { Lang.tr(null, it) } ?: ""),
            "affixes" to profile.affixes.sortedBy { it.ordinal }.joinToString("") { affix ->
                Lang.tr(null, "mob.affix-name", "name" to Lang.tr(null, affix.key))
            },
            "name" to "<lang:${entity.type.translationKey()}>",
            "health" to CombatFormat.health(CombatHealth.current(entity)),
            "max" to CombatFormat.number(CombatHealth.max(entity)),
        )
        entity.customName(ComponentUtil.parse(text))
        entity.isCustomNameVisible = profile.rank != MobRank.NORMAL
    }

    /** [update] on the next tick, once the hit or heal in progress has landed. Asked for twice, it runs once. */
    fun updateLater(entity: LivingEntity) {
        if (!MobProfiles.of(entity).nameplate || !pending.add(entity.uniqueId)) return
        Bukkit.getScheduler().runTask(Main.instance, Runnable {
            pending.remove(entity.uniqueId)
            update(entity)
        })
    }
}
