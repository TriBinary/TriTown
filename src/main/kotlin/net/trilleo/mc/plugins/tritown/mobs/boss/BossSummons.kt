package net.trilleo.mc.plugins.tritown.mobs.boss

import net.kyori.adventure.title.Title
import net.trilleo.mc.plugins.tritown.Main
import net.trilleo.mc.plugins.tritown.combat.Combat
import net.trilleo.mc.plugins.tritown.config.MobSettings
import net.trilleo.mc.plugins.tritown.content.BossDef
import net.trilleo.mc.plugins.tritown.content.ContentItems
import net.trilleo.mc.plugins.tritown.content.ContentRegistry
import net.trilleo.mc.plugins.tritown.content.MobKindDef
import net.trilleo.mc.plugins.tritown.mobs.*
import net.trilleo.mc.plugins.tritown.utils.ComponentUtil
import net.trilleo.mc.plugins.tritown.utils.InventoryUtil
import net.trilleo.mc.plugins.tritown.utils.sendPrefixed
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Mob
import org.bukkit.entity.Player
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason
import org.bukkit.scheduler.BukkitRunnable
import kotlin.math.ceil

/**
 * Summoning a boss with its sigil.
 *
 * Everything that can refuse is asked first — bosses switched on, combat on in
 * the world, the wilds and not a town, the place the boss asks for, room for
 * it, no other boss or summoning close by — and only then is one sigil taken
 * from the player's hand. A short ritual follows, and the boss rises a few
 * blocks in front of them, at its own level, eligible for loot like any wild
 * mob. If it cannot rise after all, the sigil is handed back.
 */
object BossSummons {

    private val rituals = mutableListOf<Location>()

    fun summon(player: Player, kind: MobKindDef) {
        val boss = kind.boss ?: return
        val spot = spotFor(player)
        refusal(kind, boss, spot)?.let { key ->
            player.sendPrefixed(
                player.tr(
                    "common.error",
                    "message" to player.tr(key, "name" to BossFights.name(player, kind))
                )
            )
            return
        }
        if (!takeSigil(player, boss.sigil)) return
        ritual(player, kind, boss, spot)
    }

    /** Why [kind] cannot be summoned at [spot], as a translation key, or `null` if it can. */
    private fun refusal(kind: MobKindDef, boss: BossDef, spot: Location): String? {
        val world = spot.world
        val crowd = boss.arena * CROWD
        return when {
            !MobSettings.snapshot.bosses -> "boss.refused.disabled"
            !Combat.isActive(world) -> "boss.refused.inactive"
            MobZones.read(spot).town -> "boss.refused.town"
            !placeFits(boss, spot) -> "boss.refused.place"
            BossFights.near(
                spot,
                crowd
            ) || rituals.any { it.world == world && it.distanceSquared(spot) <= crowd * crowd } ->
                "boss.refused.crowded"

            !roomFor(kind, spot, boss.place.water) -> "boss.refused.room"
            else -> null
        }
    }

    private fun placeFits(boss: BossDef, spot: Location): Boolean {
        val place = boss.place
        val world = spot.world
        if (place.worlds.isNotEmpty() && world.environment.name.lowercase() !in place.worlds && world.name.lowercase() !in place.worlds) {
            return false
        }
        if (place.water && spot.block.type != Material.WATER) return false
        return place.maxY == null || spot.blockY <= place.maxY
    }

    /** Whether a boss of [kind] would fit at [spot]: tall enough a space, on the ground unless it rises from water. */
    private fun roomFor(kind: MobKindDef, spot: Location, water: Boolean): Boolean {
        val type = type(kind) ?: return false
        val body = type.entityClass?.let { spot.world.createEntity(spot, it) } ?: return false
        val height = ceil(body.height * kind.scale).toInt().coerceAtLeast(1)
        val feet = spot.block
        if ((0 until height).any { !feet.getRelative(0, it, 0).isPassable }) return false
        return water || !feet.getRelative(0, -1, 0).isPassable
    }

    /** A few blocks in front of [player] if there is space there, or where they stand. */
    private fun spotFor(player: Player): Location {
        val facing = player.location.direction.setY(0)
        if (facing.lengthSquared() < MIN_FACING) return player.location
        val ahead = player.location.add(facing.normalize().multiply(AHEAD)).toBlockLocation()
        return if (ahead.block.isPassable && ahead.clone().add(0.0, 1.0, 0.0).block.isPassable) ahead.add(0.5, 0.0, 0.5)
        else player.location
    }

    private fun takeSigil(player: Player, sigil: String): Boolean {
        val hand = player.inventory.itemInMainHand
        if (ContentItems.idOf(hand) != sigil) return false
        val rest = hand.clone().apply { amount -= 1 }
        player.inventory.setItemInMainHand(if (rest.amount > 0) rest else null)
        return true
    }

    private fun ritual(player: Player, kind: MobKindDef, boss: BossDef, spot: Location) {
        rituals += spot
        spot.world.playSound(spot, Sound.BLOCK_END_PORTAL_SPAWN, 0.8f, 1.2f)
        player.sendPrefixed(player.tr("boss.ritual", "name" to BossFights.name(player, kind)))

        val ticks = ContentRegistry.bestiary.rules.ritualSeconds * TICKS
        object : BukkitRunnable() {
            var elapsed = 0

            override fun run() {
                if (elapsed < ticks) {
                    val rise = elapsed.toDouble() / ticks.coerceAtLeast(1) * RITUAL_HEIGHT
                    spot.world.spawnParticle(
                        Particle.SOUL_FIRE_FLAME,
                        spot.clone().add(0.0, rise, 0.0),
                        8,
                        0.4,
                        0.1,
                        0.4,
                        0.01
                    )
                    spot.world.spawnParticle(Particle.SMOKE, spot, 4, 0.8, 0.05, 0.8, 0.01)
                    elapsed++
                    return
                }
                cancel()
                rituals -= spot
                rise(player, kind, boss, spot)
            }
        }.runTaskTimer(Main.instance, 0L, 1L)
    }

    private fun rise(player: Player, kind: MobKindDef, boss: BossDef, spot: Location) {
        val mob = spawn(kind, spot, boss.level, eligible = true)
        if (mob == null) {
            if (player.isOnline) InventoryUtil.give(player, listOfNotNull(ContentItems.create(boss.sigil)))
            return
        }
        (mob as? Mob)?.target = player.takeIf { it.isOnline && it.world == spot.world }

        spot.world.strikeLightningEffect(spot)
        spot.world.playSound(spot, Sound.ENTITY_WITHER_SPAWN, 1f, 1f)
        val summoner = ComponentUtil.escape(player.name)
        BossFights.announce(spot) { viewer ->
            viewer.tr("boss.summoned", "player" to summoner, "name" to BossFights.name(viewer, kind))
        }
        val arena = boss.arena * boss.arena
        spot.world.players.filter { it.location.distanceSquared(spot) <= arena }.forEach { viewer ->
            viewer.showTitle(
                Title.title(
                    ComponentUtil.parse(BossFights.name(viewer, kind)),
                    ComponentUtil.parse(viewer.tr("boss.subtitle", "level" to boss.level)),
                )
            )
        }
    }

    /**
     * The boss itself, glowing and never despawning while its fight lasts.
     * Its profile and home are set before it is added to the world, so
     * everything that sees it arrive already knows it for a boss. One an
     * administrator calls up is not [eligible], and drops nothing.
     */
    fun spawn(kind: MobKindDef, at: Location, level: Int, eligible: Boolean): LivingEntity? {
        val type = type(kind) ?: return null
        val profile = MobProfile(
            level = level,
            rank = MobRank.NORMAL,
            affixes = kind.affixes,
            nameplate = MobSettings.snapshot.nameplates,
            eligible = eligible,
            kind = kind.id,
        )
        val entity = at.world.spawnEntity(at, type, SpawnReason.CUSTOM) { spawned ->
            val living = spawned as? LivingEntity ?: return@spawnEntity
            MobProfiles.assign(living, profile)
            BossFights.setHome(living, at)
            living.removeWhenFarAway = false
            living.isGlowing = true
        }
        val mob = entity as? LivingEntity ?: run {
            entity.remove()
            return null
        }
        MobSetup.settle(mob, profile)
        return mob
    }

    private fun type(kind: MobKindDef): EntityType? = EntityType.entries.firstOrNull { it.name == kind.base }

    private const val TICKS = 20
    private const val AHEAD = 4.0
    private const val RITUAL_HEIGHT = 3.0
    private const val MIN_FACING = 1e-6

    /** How many arenas' widths apart two bosses must be. */
    private const val CROWD = 2.0
}
