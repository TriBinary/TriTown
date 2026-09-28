package net.trilleo.mc.plugins.tritown.mobs

import org.bukkit.NamespacedKey
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType

/**
 * What TriTown decided about a mob when it spawned.
 *
 * @param eligible whether the mob spawned the way a wild one does, so it may
 *   drop more than vanilla gives: a summoner's minions, for one, may not
 * @param kind the id of the custom mob it is in `bestiary.yml`, or `null` for
 *   an ordinary one. Only the id is kept: what the kind is worth is read from
 *   the file every time, so a retune reaches every one already out there
 */
data class MobProfile(
    val level: Int,
    val rank: MobRank,
    val affixes: Set<Affix>,
    val nameplate: Boolean,
    val eligible: Boolean,
    val kind: String? = null,
) {

    fun has(affix: Affix): Boolean = affix in affixes

    /** Whether the mob takes a turn in the mob task: it has a rank, a kind, or an affix to show. */
    val isActive: Boolean
        get() = rank != MobRank.NORMAL || kind != null || affixes.isNotEmpty()

    companion object {
        /** A mob TriTown never touched: level 1, which is to say vanilla. */
        val VANILLA =
            MobProfile(level = 1, rank = MobRank.NORMAL, affixes = emptySet(), nameplate = false, eligible = false)
    }
}

/**
 * Reads and writes a mob's [MobProfile], kept in its own persistent data under
 * `tritown:mob`.
 *
 * The game saves it with the entity, so a mob keeps its level and rank through
 * chunk unloads and restarts without any file of TriTown's. Only a mob that
 * spawned the way a wild one does gets a profile. One with none — from a
 * spawner, an egg, a command, or from before TriTown was installed — is
 * [MobProfile.VANILLA].
 */
object MobProfiles {

    private val PROFILE = NamespacedKey("tritown", "mob")
    private val LEVEL = NamespacedKey("tritown", "level")
    private val RANK = NamespacedKey("tritown", "rank")
    private val AFFIXES = NamespacedKey("tritown", "affixes")
    private val NAMEPLATE = NamespacedKey("tritown", "nameplate")
    private val ELIGIBLE = NamespacedKey("tritown", "eligible")
    private val KIND = NamespacedKey("tritown", "kind")

    fun of(entity: LivingEntity): MobProfile {
        if (entity is Player) return MobProfile.VANILLA
        val data = container(entity) ?: return MobProfile.VANILLA
        return MobProfile(
            level = data.get(LEVEL, PersistentDataType.INTEGER)?.coerceAtLeast(1) ?: 1,
            rank = data.get(RANK, PersistentDataType.STRING)
                ?.let { name -> MobRank.entries.firstOrNull { it.name == name } } ?: MobRank.NORMAL,
            affixes = data.get(AFFIXES, PersistentDataType.LIST.strings()).orEmpty()
                .mapNotNull { name -> Affix.entries.firstOrNull { it.name == name } }
                .toSet(),
            nameplate = data.get(NAMEPLATE, PersistentDataType.BOOLEAN) == true,
            eligible = data.get(ELIGIBLE, PersistentDataType.BOOLEAN) == true,
            kind = data.get(KIND, PersistentDataType.STRING),
        )
    }

    fun level(entity: LivingEntity): Int = of(entity).level

    fun assign(entity: LivingEntity, profile: MobProfile) {
        val data = entity.persistentDataContainer.adapterContext.newPersistentDataContainer()
        data.set(LEVEL, PersistentDataType.INTEGER, profile.level)
        data.set(RANK, PersistentDataType.STRING, profile.rank.name)
        data.set(AFFIXES, PersistentDataType.LIST.strings(), profile.affixes.map { it.name })
        data.set(NAMEPLATE, PersistentDataType.BOOLEAN, profile.nameplate)
        data.set(ELIGIBLE, PersistentDataType.BOOLEAN, profile.eligible)
        profile.kind?.let { data.set(KIND, PersistentDataType.STRING, it) }
        entity.persistentDataContainer.set(PROFILE, PersistentDataType.TAG_CONTAINER, data)
    }

    /** Stops drawing a nameplate on the mob, because a player has named it. */
    fun dropNameplate(entity: LivingEntity) {
        if (container(entity) == null) return
        assign(entity, of(entity).copy(nameplate = false))
    }

    private fun container(entity: LivingEntity): PersistentDataContainer? =
        entity.persistentDataContainer.get(PROFILE, PersistentDataType.TAG_CONTAINER)
}
