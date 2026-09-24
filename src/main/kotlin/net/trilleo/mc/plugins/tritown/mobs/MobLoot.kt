package net.trilleo.mc.plugins.tritown.mobs

import net.trilleo.mc.plugins.tritown.combat.CombatHealth
import net.trilleo.mc.plugins.tritown.combat.PlayerStats
import net.trilleo.mc.plugins.tritown.combat.Stat
import net.trilleo.mc.plugins.tritown.content.ContentItems
import net.trilleo.mc.plugins.tritown.content.ContentRegistry
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
 * killer's Magic Find. The drops go into the death event, so item protection
 * hands them to the killer like the rest of the mob's loot.
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

        val magicFind = PlayerStats.sheet(killer)[Stat.MAGIC_FIND]
        return LootRoller.roll(table, mob.type.name, profile.rank, profile.level, magicFind, Random)
            .flatMap { drop -> listOfNotNull(ContentItems.create(drop.item, drop.amount)) }
    }

    fun forget(mob: UUID) = ledger.forget(mob)

    /** The player a hit counts for: the attacker, or the owner of a tamed pet. */
    private fun creditor(attacker: LivingEntity?): UUID? = when (attacker) {
        is Player -> attacker.uniqueId
        is Tameable -> attacker.ownerUniqueId?.takeIf { attacker.isTamed }
        else -> null
    }
}
