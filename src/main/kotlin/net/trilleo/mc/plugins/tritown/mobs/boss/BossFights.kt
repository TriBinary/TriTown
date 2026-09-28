package net.trilleo.mc.plugins.tritown.mobs.boss

import net.kyori.adventure.bossbar.BossBar
import net.trilleo.mc.plugins.tritown.combat.CombatHealth
import net.trilleo.mc.plugins.tritown.config.MobSettings
import net.trilleo.mc.plugins.tritown.content.BossDef
import net.trilleo.mc.plugins.tritown.content.ContentRegistry
import net.trilleo.mc.plugins.tritown.content.MobKindDef
import net.trilleo.mc.plugins.tritown.gear.GearText
import net.trilleo.mc.plugins.tritown.mobs.AffixEffects
import net.trilleo.mc.plugins.tritown.mobs.BestiaryRecords
import net.trilleo.mc.plugins.tritown.mobs.MobKinds
import net.trilleo.mc.plugins.tritown.mobs.MobLoot
import net.trilleo.mc.plugins.tritown.mobs.MobNameplate
import net.trilleo.mc.plugins.tritown.mobs.MobProfiles
import net.trilleo.mc.plugins.tritown.mobs.MobSetup
import net.trilleo.mc.plugins.tritown.protection.ItemOwnership
import net.trilleo.mc.plugins.tritown.utils.ComponentUtil
import net.trilleo.mc.plugins.tritown.utils.InventoryUtil
import net.trilleo.mc.plugins.tritown.utils.sendPrefixed
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Mob
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import java.util.*

/**
 * Every boss fight going on in a loaded chunk, and the only thing that runs
 * one.
 *
 * A boss is held to an **arena**: `arena` blocks around where it was summoned,
 * its home, which it keeps in its own persistent data. Twice a second, from
 * `MobTurnTask`, each fight:
 *
 * - shows a boss bar to every player near the arena, in their own language;
 * - takes a boss pulled more than `arena-margin` past its arena back home;
 * - turns a boss to the nearest player in its arena when it has no one to
 *   fight there, which keeps even a spider or an enderman on the attack;
 * - enters any phase its health has fallen into: announced once, its minions
 *   called at once, and its affixes written into the boss's profile;
 * - sends a boss that has had nobody in its arena for `idle-seconds` away.
 *
 * What a fight remembers — which phases it has announced, who sees its bar —
 * is kept in memory, and taken up again as the boss loads back in: phases it
 * has already passed are not announced again.
 */
object BossFights {

    private val HOME = NamespacedKey("tritown", "boss-home")

    private class Fight(val boss: LivingEntity) {
        val bars = HashMap<UUID, BossBar>()
        val entered = HashSet<Int>()
        var idleSince: Int? = null
    }

    private val fights = HashMap<UUID, Fight>()

    fun isBoss(mob: LivingEntity): Boolean = boss(mob) != null

    /** Where a boss fights: [at] from now on. Set before it is added to the world. */
    fun setHome(boss: LivingEntity, at: Location) {
        boss.persistentDataContainer.set(HOME, PersistentDataType.LIST.doubles(), listOf(at.x, at.y, at.z))
    }

    /** Takes up the fight around [boss] as it comes into the world: summoned, spawned by a command, or loaded back in. */
    fun adopt(boss: LivingEntity) {
        val def = boss(boss) ?: return
        if (home(boss) == null) setHome(boss, boss.location)
        val fight = Fight(boss)
        def.phases.forEachIndexed { index, phase -> if (CombatHealth.fraction(boss) * 100.0 < phase.below) fight.entered += index }
        fights.put(boss.uniqueId, fight)?.let(::hideBars)
    }

    /** Lets go of a boss leaving the world, unloaded or dead: nobody is shown its bar any more. */
    fun release(boss: UUID) {
        fights.remove(boss)?.let(::hideBars)
    }

    /** Whether a boss is fighting within [radius] of [at]. */
    fun near(at: Location, radius: Double): Boolean = fights.values.any { fight ->
        fight.boss.isValid && fight.boss.world == at.world && fight.boss.location.distanceSquared(at) <= radius * radius
    }

    /** Every fight's twice-a-second turn. */
    fun tick() {
        val now = Bukkit.getCurrentTick()
        val rules = ContentRegistry.bestiary.rules
        for (fight in fights.values.toList()) {
            val boss = fight.boss
            val profile = MobProfiles.of(boss)
            val kind = MobKinds.def(profile)
            val def = kind?.boss
            if (!boss.isValid || kind == null || def == null) {
                release(boss.uniqueId)
                continue
            }
            val home = home(boss) ?: boss.location
            val arena = fighters(home, def.arena)

            showBars(fight, kind, def, home)
            if (boss.world != home.world || boss.location.distance(home) > def.arena + rules.arenaMargin) takeHome(boss, home)
            retarget(boss, arena)
            enterPhases(fight, kind, def)

            if (arena.isNotEmpty()) {
                fight.idleSince = null
            } else if (now - (fight.idleSince ?: now.also { fight.idleSince = it }) >= rules.idleSeconds * TICKS) {
                depart(fight, kind, home)
            }
        }
    }

    /**
     * [boss] has died: its bar goes, its fall is announced, and every player
     * who earned a share is given theirs — where it fell if they are near, or
     * straight into their inventory if not. Nothing drops for anyone else.
     * Everyone who took part counts it slain in their bestiary.
     */
    fun defeated(boss: LivingEntity) {
        release(boss.uniqueId)
        val kind = MobKinds.def(MobProfiles.of(boss)) ?: return
        val spoils = MobLoot.bossRewards(boss)
        val at = boss.location
        val range = ContentRegistry.bestiary.rules.lootRange

        val victors = spoils.rewards.keys.mapNotNull { Bukkit.getPlayer(it)?.name }.map(ComponentUtil::escape)
        at.world.playSound(at, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f)
        announce(at) { viewer ->
            if (victors.isEmpty()) viewer.tr("boss.defeated-alone", "name" to name(viewer, kind))
            else viewer.tr("boss.defeated", "name" to name(viewer, kind), "players" to victors.joinToString(", "))
        }

        spoils.rewards.forEach { (id, items) ->
            val player = Bukkit.getPlayer(id) ?: return@forEach
            if (player.world == at.world && player.location.distance(at) <= range) {
                items.forEach { ItemOwnership.dropFor(player, it, at) }
            } else InventoryUtil.give(player, items)
            player.sendPrefixed(reward(player, items))
            BestiaryRecords.credit(player, kind.id, items)
        }
        spoils.shortOfShare.forEach { id ->
            val player = Bukkit.getPlayer(id) ?: return@forEach
            player.sendPrefixed(player.tr("boss.no-share"))
            BestiaryRecords.credit(player, kind.id, emptyList())
        }
    }

    /** Tells everyone within `announce-range` of [at] what [line] says to them. */
    fun announce(at: Location, line: (Player) -> String) {
        val range = MobSettings.snapshot.announceRange
        at.world.players
            .filter { it.location.distanceSquared(at) <= range * range }
            .forEach { it.sendPrefixed(line(it)) }
    }

    /** A boss's name as chat shows it to [viewer]. */
    fun name(viewer: Player, kind: MobKindDef): String = viewer.tr("boss.name", "name" to viewer.tr(kind.nameKey))

    private fun boss(mob: LivingEntity): BossDef? = MobKinds.def(MobProfiles.of(mob))?.boss

    private fun home(boss: LivingEntity): Location? {
        val point = boss.persistentDataContainer.get(HOME, PersistentDataType.LIST.doubles()) ?: return null
        if (point.size != 3) return null
        return Location(boss.world, point[0], point[1], point[2])
    }

    /** The players in the arena around [home] who can fight: in survival or adventure. */
    private fun fighters(home: Location, radius: Double): List<Player> = home.world.players.filter { player ->
        (player.gameMode == GameMode.SURVIVAL || player.gameMode == GameMode.ADVENTURE) && !player.isDead &&
                player.location.distanceSquared(home) <= radius * radius
    }

    /** The players near enough to [home] to see its boss's bar and hear its phases. */
    private fun watchers(home: Location, def: BossDef): List<Player> {
        val reach = def.arena + ContentRegistry.bestiary.rules.arenaMargin + BAR_REACH
        return home.world.players.filter { it.location.distanceSquared(home) <= reach * reach }
    }

    private fun showBars(fight: Fight, kind: MobKindDef, def: BossDef, home: Location) {
        val viewers = watchers(home, def)
        val progress = CombatHealth.fraction(fight.boss).toFloat()
        val color = BossBar.Color.NAMES.value(def.bar) ?: BossBar.Color.RED

        viewers.forEach { viewer ->
            val bar = fight.bars.getOrPut(viewer.uniqueId) {
                BossBar.bossBar(
                    ComponentUtil.parse(viewer.tr("boss.bar", "name" to viewer.tr(kind.nameKey), "level" to def.level)),
                    progress,
                    color,
                    BossBar.Overlay.NOTCHED_10,
                )
            }
            bar.progress(progress)
            viewer.showBossBar(bar)
        }
        val seen = viewers.map { it.uniqueId }.toSet()
        fight.bars.keys.filter { it !in seen }.forEach { id ->
            val bar = fight.bars.remove(id) ?: return@forEach
            Bukkit.getPlayer(id)?.hideBossBar(bar)
        }
    }

    private fun hideBars(fight: Fight) {
        fight.bars.forEach { (id, bar) -> Bukkit.getPlayer(id)?.hideBossBar(bar) }
        fight.bars.clear()
    }

    private fun takeHome(boss: LivingEntity, home: Location) {
        boss.world.spawnParticle(Particle.REVERSE_PORTAL, boss.location.add(0.0, 1.0, 0.0), 40, 0.5, 1.0, 0.5, 0.05)
        MobSetup.teleport(boss, home)
        boss.world.playSound(home, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.6f)
    }

    private fun retarget(boss: LivingEntity, arena: List<Player>) {
        val mob = boss as? Mob ?: return
        if ((mob.target as? Player)?.let { it in arena } == true) return
        mob.target = arena.minByOrNull { it.location.distanceSquared(boss.location) } ?: return
    }

    private fun enterPhases(fight: Fight, kind: MobKindDef, def: BossDef) {
        val boss = fight.boss
        val health = CombatHealth.fraction(boss) * 100.0
        def.phases.forEachIndexed { index, phase ->
            if (health >= phase.below || !fight.entered.add(index)) return@forEachIndexed

            watchers(home(boss) ?: boss.location, def).forEach { it.sendPrefixed(it.tr(kind.phaseKey(index), "name" to name(it, kind))) }
            boss.world.playSound(boss.location, Sound.ENTITY_WITHER_SPAWN, 0.6f, 1.2f)

            val profile = MobProfiles.of(boss)
            if (phase.affixes.isNotEmpty()) {
                val stronger = profile.copy(affixes = profile.affixes + phase.affixes)
                MobProfiles.assign(boss, stronger)
                AffixEffects.onSpawn(boss, stronger)
                MobNameplate.updateLater(boss)
            }
            if (phase.summon > 0) MobSetup.minions(boss, profile, phase.summon, (boss as? Mob)?.target)
        }
    }

    private fun depart(fight: Fight, kind: MobKindDef, home: Location) {
        val boss = fight.boss
        release(boss.uniqueId)
        announce(home) { viewer -> viewer.tr("boss.departed", "name" to name(viewer, kind)) }
        boss.world.spawnParticle(Particle.SOUL, boss.location.add(0.0, 1.0, 0.0), 60, 0.6, 1.2, 0.6, 0.05)
        boss.world.playSound(boss.location, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.5f)
        MobLoot.forget(boss.uniqueId)
        boss.remove()
    }

    private fun reward(player: Player, items: List<ItemStack>): String {
        val names = items.mapNotNull { GearText.stack(player, it) }
        return if (names.isEmpty()) player.tr("boss.reward-empty") else player.tr("boss.reward", "items" to names.joinToString(", "))
    }

    private const val TICKS = 20

    /** How far past the arena's edge a player still sees its bar and hears its phases. */
    private const val BAR_REACH = 16.0
}
