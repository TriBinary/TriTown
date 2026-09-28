package net.trilleo.mc.plugins.tritown.commands.mobs

import net.trilleo.mc.plugins.tritown.combat.BalanceSimulator
import net.trilleo.mc.plugins.tritown.combat.Combat
import net.trilleo.mc.plugins.tritown.combat.CombatFormat
import net.trilleo.mc.plugins.tritown.content.ContentRegistry
import net.trilleo.mc.plugins.tritown.gear.ForgeCosts
import net.trilleo.mc.plugins.tritown.mobs.MobSetup
import net.trilleo.mc.plugins.tritown.mobs.MobZones
import net.trilleo.mc.plugins.tritown.mobs.boss.BossSummons
import net.trilleo.mc.plugins.tritown.registration.PluginCommand
import net.trilleo.mc.plugins.tritown.utils.ComponentUtil
import net.trilleo.mc.plugins.tritown.utils.sendPrefixed
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.Bukkit
import org.bukkit.attribute.Attribute
import org.bukkit.command.CommandSender
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player

/**
 * Looking into how dangerous the world is: the mob level where you stand, and
 * what the balance in force makes of each level; and the custom mobs of
 * `bestiary.yml`, which an administrator can call up to see.
 */
class MobCommand : PluginCommand(
    name = "mob",
    description = "Inspect mob levels and the combat balance",
    usage = "/tritown mob <level|balance|bosses|kinds|spawn <kind> [level]>",
    permission = PERMISSION,
) {

    override fun execute(sender: CommandSender, args: Array<out String>): Boolean {
        when (args.firstOrNull()?.lowercase()) {
            LEVEL -> level(sender)
            BALANCE -> balance(sender)
            BOSSES -> bosses(sender)
            KINDS -> kinds(sender)
            SPAWN -> spawn(sender, args)
            else -> sender.sendPrefixed(sender.tr("command.mob.usage"))
        }
        return true
    }

    override fun tabComplete(sender: CommandSender, args: Array<out String>): List<String> {
        val options = when (args.size) {
            1 -> listOf(LEVEL, BALANCE, BOSSES, KINDS, SPAWN)
            2 -> if (args[0].equals(SPAWN, ignoreCase = true)) ContentRegistry.bestiary.kinds.keys.toList() else emptyList()
            else -> emptyList()
        }
        return options.filter { it.startsWith(args.last(), ignoreCase = true) }
    }

    /** Why a mob spawning where the sender stands would be the level it would. */
    private fun level(sender: CommandSender) {
        val player = sender as? Player ?: run {
            sender.sendPrefixed(sender.tr("command.mob.players-only"))
            return
        }
        if (!Combat.isActive(player.world)) {
            player.sendPrefixed(player.tr("command.mob.inactive"))
            return
        }

        val reading = MobZones.read(player.location)
        player.sendPrefixed(player.tr("command.mob.level", "level" to reading.level))
        val reason = when {
            reading.town -> player.tr("command.mob.level-town")
            reading.ring == 0 -> player.tr("command.mob.level-hub")
            else -> player.tr("command.mob.level-ring", "ring" to reading.ring)
        }
        player.sendMessage(ComponentUtil.parse(reason))
        if (reading.night) player.sendMessage(ComponentUtil.parse(player.tr("command.mob.level-night")))
        if (reading.deep) player.sendMessage(ComponentUtil.parse(player.tr("command.mob.level-deep")))
    }

    private fun kinds(sender: CommandSender) {
        val ids = ContentRegistry.bestiary.kinds.keys
        sender.sendPrefixed(sender.tr("command.mob.kinds", "amount" to ids.size, "ids" to ids.joinToString(", ")))
    }

    /**
     * Calls up a custom mob a few blocks in front of the sender, at the level
     * given or the level where they stand. It is not eligible, so it drops
     * nothing beyond vanilla's loot: testing never becomes a way to farm.
     */
    private fun spawn(sender: CommandSender, args: Array<out String>) {
        val player = sender as? Player ?: run {
            sender.sendPrefixed(sender.tr("command.mob.players-only"))
            return
        }
        val id = args.getOrNull(1)?.lowercase() ?: run {
            player.sendPrefixed(player.tr("command.mob.usage"))
            return
        }
        val kind = ContentRegistry.bestiary.kinds[id] ?: run {
            player.sendPrefixed(player.tr("command.mob.unknown-kind", "id" to ComponentUtil.escape(id)))
            return
        }
        val given = args.getOrNull(2)
        val level = if (given == null) kind.boss?.level ?: MobZones.levelAt(player.location) else {
            given.toIntOrNull()?.takeIf { it in 1..MAX_LEVEL } ?: run {
                player.sendPrefixed(player.tr("command.mob.usage"))
                return
            }
        }
        val type = EntityType.entries.firstOrNull { it.name == kind.base } ?: return

        val ahead = player.location.add(player.location.direction.setY(0).normalize().multiply(SPAWN_DISTANCE))
        val at = if (ahead.block.isPassable && ahead.clone().add(0.0, 1.0, 0.0).block.isPassable) ahead else player.location
        val spawned = if (kind.boss != null) BossSummons.spawn(kind, at, level, eligible = false)
        else MobSetup.spawn(at, type, level, kind)
        spawned ?: return
        player.sendPrefixed(
            player.tr("command.mob.spawned", "name" to player.tr(kind.nameKey), "level" to level)
        )
    }

    /**
     * Each boss at its level against a full kit of the tier made for it: how
     * long one player would take to bring it down, and how hard it hits. A
     * boss's kind is measured by making one that never enters the world.
     */
    private fun bosses(sender: CommandSender) {
        val balance = ContentRegistry.balance
        val world = Bukkit.getWorlds().firstOrNull() ?: return
        sender.sendPrefixed(sender.tr("command.mob.bosses-header"))
        ContentRegistry.bestiary.kinds.values.forEach { kind ->
            val boss = kind.boss ?: return@forEach
            val type = EntityType.entries.firstOrNull { it.name == kind.base } ?: return@forEach
            val body = type.entityClass?.let { world.createEntity(world.spawnLocation, it) } as? LivingEntity
                ?: return@forEach
            val foe = BalanceSimulator.Foe(
                health = body.getAttribute(Attribute.MAX_HEALTH)?.baseValue ?: return@forEach,
                hit = body.getAttribute(Attribute.ATTACK_DAMAGE)?.baseValue ?: 0.0,
            )
            val tier = (boss.level + ForgeCosts.LEVELS_PER_TIER - 1) / ForgeCosts.LEVELS_PER_TIER
            val row = BalanceSimulator.bossRow(balance, boss.level, tier, foe, kind.health, kind.damage, kind.defense)
            sender.sendMessage(
                ComponentUtil.parse(
                    sender.tr(
                        "command.mob.bosses-row",
                        "name" to sender.tr(kind.nameKey),
                        "level" to boss.level,
                        "tier" to tier,
                        "health" to CombatFormat.number(row.foeHealth),
                        "hit" to CombatFormat.number(row.foeHit),
                        "hits" to row.hitsToKill,
                        "percent" to CombatFormat.number(row.shareTaken * 100.0),
                    )
                )
            )
        }
    }

    /**
     * A zombie's health and hit at a spread of levels, and how a player fares
     * against it in gear of the tier made for that level.
     */
    private fun balance(sender: CommandSender) {
        val balance = ContentRegistry.balance
        sender.sendPrefixed(sender.tr("command.mob.balance-header"))
        for (level in TABLE_LEVELS) {
            val tier = (level + ForgeCosts.LEVELS_PER_TIER - 1) / ForgeCosts.LEVELS_PER_TIER
            val row = BalanceSimulator.gearRow(balance, level, tier)
            sender.sendMessage(
                ComponentUtil.parse(
                    sender.tr(
                        "command.mob.balance-row",
                        "level" to level,
                        "tier" to tier,
                        "health" to CombatFormat.number(row.foeHealth),
                        "hit" to CombatFormat.number(row.foeHit),
                        "hits" to row.hitsToKill,
                        "percent" to CombatFormat.number(row.shareTaken * 100.0),
                    )
                )
            )
        }
    }

    companion object {
        const val PERMISSION = "tritown.mob.admin"

        private const val LEVEL = "level"
        private const val BALANCE = "balance"
        private const val BOSSES = "bosses"
        private const val KINDS = "kinds"
        private const val SPAWN = "spawn"
        private const val MAX_LEVEL = 999
        private const val SPAWN_DISTANCE = 3.0
        private val TABLE_LEVELS = listOf(1, 5, 10, 15, 20, 25, 30, 40, 50, 60)
    }
}
