package net.trilleo.mc.plugins.tritown.mobs

import net.trilleo.mc.plugins.tritown.combat.CombatHealth
import net.trilleo.mc.plugins.tritown.combat.PlayerStats
import net.trilleo.mc.plugins.tritown.combat.Stat
import net.trilleo.mc.plugins.tritown.content.ContentItems
import net.trilleo.mc.plugins.tritown.content.ContentRegistry
import net.trilleo.mc.plugins.tritown.content.LootEntry
import net.trilleo.mc.plugins.tritown.content.Rarity
import net.trilleo.mc.plugins.tritown.gear.ForgeCosts
import net.trilleo.mc.plugins.tritown.gear.Gear
import net.trilleo.mc.plugins.tritown.gear.GearStats
import org.bukkit.Bukkit
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.entity.Tameable
import org.bukkit.inventory.ItemStack
import java.util.*
import kotlin.random.Random

/**
 * What a wild mob drops beyond vanilla's loot, and who earned it.
 *
 * A mob only drops anything extra when all of these hold:
 *
 * 1. it spawned in the wild ([MobProfile.eligible]);
 * 2. a player killed it;
 * 3. players, their pets included, dealt at least `player-share` of its health
 *    between them, as the [DamageLedger] recorded it;
 * 4. it is at least the loot table's `min-level`.
 *
 * What it drops is rolled by [LootRoller] against `mobs.yml`, with the
 * killer's Magic Find: materials and essence, and now and then — a champion,
 * mostly — a finished piece of gear of the tier its level is made for. A
 * custom mob drops its own loot from `bestiary.yml` besides. A boss drops
 * nothing here: [bossRewards] rolls its loot once for each player who earned a
 * share of it. The
 * drops go into the death event, so item protection hands them to the killer
 * like the rest of the mob's loot.
 */
object MobLoot {

    private val ledger = DamageLedger()

    /** Credits whoever is behind [attacker] with the [dealt] RPG damage it took off [victim]. */
    fun credit(victim: LivingEntity, attacker: LivingEntity?, dealt: Double) {
        val player = creditor(attacker) ?: return
        if (!MobProfiles.of(victim).eligible) return
        ledger.credit(victim.uniqueId, player, dealt.coerceAtMost(CombatHealth.current(victim)))
    }

    /** What [mob], which has just died, drops on top of vanilla's loot. Its ledger is settled either way. */
    fun dropsFor(mob: LivingEntity): List<ItemStack> {
        val share = ledger.share(mob.uniqueId, CombatHealth.max(mob))
        ledger.forget(mob.uniqueId)

        val profile = MobProfiles.of(mob)
        val killer = mob.killer ?: return emptyList()
        val table = ContentRegistry.loot
        if (!profile.eligible || share * 100.0 < table.rules.playerShare) return emptyList()

        val kind = MobKinds.def(profile)
        if (kind?.boss != null) return emptyList()
        val odds = ContentRegistry.balance.gear.dropOdds
        val magicFind = PlayerStats.sheet(killer)[Stat.MAGIC_FIND]
        val drops = LootRoller.roll(table, mob.type.name, profile.rank, profile.level, magicFind, Random)
            .mapNotNull { drop -> ContentItems.create(drop.item, drop.amount) }
        val gear = if (LootRoller.dropsGear(table, profile.rank, profile.level, magicFind, Random)) gearFor(
            profile.level,
            odds
        ) else null
        val own = kind?.let {
            LootRoller.rollEntries(table, it.loot, profile.level, magicFind, Random)
                .mapNotNull { won -> stack(won, odds) }
        }.orEmpty()
        return drops + listOfNotNull(gear) + own
    }

    /**
     * What each player who earned a share of [boss], which has just died, is
     * given: its loot rolled for them alone, with their own Magic Find, and
     * now and then a finished piece of its tier. A share takes
     * `contributor-share` percent of its health, and players between them must
     * have dealt `player-share` of it, as for any mob. Its ledger is settled
     * either way. Only players still online are given anything.
     */
    fun bossRewards(boss: LivingEntity): Spoils {
        val shares = ledger.shares(boss.uniqueId, CombatHealth.max(boss))
        ledger.forget(boss.uniqueId)

        val profile = MobProfiles.of(boss)
        val kind = MobKinds.def(profile)
        val table = ContentRegistry.loot
        val rules = ContentRegistry.bestiary.rules
        val (earned, short) = shares.keys.partition { shares.getValue(it) * 100.0 >= rules.contributorShare }
        if (kind == null || !profile.eligible || boss.killer == null || shares.values.sum() * 100.0 < table.rules.playerShare) {
            return Spoils(emptyMap(), emptySet())
        }

        val rewards = earned.mapNotNull { id ->
            val player = Bukkit.getPlayer(id) ?: return@mapNotNull null
            val magicFind = PlayerStats.sheet(player)[Stat.MAGIC_FIND]
            val own = LootRoller.rollEntries(table, kind.loot, profile.level, magicFind, Random)
                .mapNotNull { stack(it, rules.gearOdds) }
            val gear = if (LootRoller.chance(table, rules.gearChance, magicFind, Random)) gearFor(
                profile.level,
                rules.gearOdds
            ) else null
            id to own + listOfNotNull(gear)
        }.toMap()
        return Spoils(rewards, short.toSet())
    }

    /**
     * A boss's loot: what each player who earned a share is given, and who
     * took part without earning one.
     */
    class Spoils(val rewards: Map<UUID, List<ItemStack>>, val shortOfShare: Set<UUID>)

    /**
     * A random piece of the tier made for [level], at a rarity drawn from
     * [odds] — or of the highest tier below it, where the content has no piece
     * of that tier. Only a piece the Forge can craft drops this way: one with
     * no recipe is a custom mob's own, and only its loot drops it.
     */
    private fun gearFor(level: Int, odds: Map<Rarity, Double>): ItemStack? {
        val tier = (level + ForgeCosts.LEVELS_PER_TIER - 1) / ForgeCosts.LEVELS_PER_TIER
        val pieces = ContentRegistry.gear.gear.values.filter { it.tier <= tier && it.recipe != null }
        val best = pieces.maxOfOrNull { it.tier } ?: return null
        val def = pieces.filter { it.tier == best }.random()
        return Gear.create(def, GearStats.pick(odds, Random))
    }

    /** What a line of a custom mob's own loot that came up is, as an item: gear at a rarity from [odds]. */
    private fun stack(won: LootRoller.Won, odds: Map<Rarity, Double>): ItemStack? = when (val entry = won.entry) {
        is LootEntry.Item -> ContentItems.create(entry.id, won.amount)
        is LootEntry.Gear -> ContentRegistry.gear.gear[entry.id]?.let { def ->
            Gear.create(
                def,
                GearStats.pick(odds, Random)
            )
        }
    }

    fun forget(mob: UUID) = ledger.forget(mob)

    /** The player a hit counts for: the attacker, or the owner of a tamed pet. */
    private fun creditor(attacker: LivingEntity?): UUID? = when (attacker) {
        is Player -> attacker.uniqueId
        is Tameable -> attacker.ownerUniqueId?.takeIf { attacker.isTamed }
        else -> null
    }
}
