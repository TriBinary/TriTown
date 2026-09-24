package net.trilleo.mc.plugins.tritown.content

/**
 * Reads [Balance] out of the parsed YAML of `balance.yml`.
 *
 * Plain Kotlin over the map SnakeYAML produces, so the bundled file can be
 * checked in a test. A value that is missing takes the bundled default
 * silently; one that is present but unusable takes it too, and is named in
 * [Result.warnings] so the owner hears about the typo instead of wondering why
 * the change did nothing.
 */
object BalanceParser {

    class Result(val balance: Balance, val warnings: List<String>)

    fun parse(root: Map<*, *>): Result {
        val reader = YamlReader(root)
        val default = Balance.DEFAULT

        val balance = Balance(
            lens = reader.number(listOf("lens"), default.lens, min = 1.0),
            player = Balance.PlayerBase(
                health = reader.number(listOf("player", "health"), default.player.health, min = 1.0),
                critChance = reader.number(listOf("player", "crit-chance"), default.player.critChance, min = 0.0),
                critDamage = reader.number(listOf("player", "crit-damage"), default.player.critDamage, min = 0.0),
            ),
            jumpCritChance = reader.number(listOf("jump-crit-chance"), default.jumpCritChance, min = 0.0),
            effects = Balance.EffectBonuses(
                strengthPerLevel = reader.number(
                    listOf("effects", "strength-per-level"),
                    default.effects.strengthPerLevel,
                    min = 0.0
                ),
                weaknessPerLevel = reader.number(
                    listOf("effects", "weakness-per-level"),
                    default.effects.weaknessPerLevel,
                    min = 0.0
                ),
            ),
            mobs = Balance.MobCurves(
                healthGrowth = reader.number(listOf("mobs", "health-growth"), default.mobs.healthGrowth, min = 1.0),
                damageGrowth = reader.number(listOf("mobs", "damage-growth"), default.mobs.damageGrowth, min = 1.0),
            ),
            vanilla = Balance.VanillaMapping(
                armorPoint = reader.number(listOf("vanilla", "armor-point"), default.vanilla.armorPoint, min = 0.0),
                toughnessPoint = reader.number(
                    listOf("vanilla", "toughness-point"),
                    default.vanilla.toughnessPoint,
                    min = 0.0
                ),
            ),
            ranks = Balance.Ranks(
                elite = rank(reader, "elite", default.ranks.elite),
                champion = rank(reader, "champion", default.ranks.champion),
            ),
            affixes = affixes(reader, default.affixes),
        )
        return Result(balance, reader.warnings)
    }

    private fun rank(reader: YamlReader, name: String, default: Balance.Rank): Balance.Rank {
        val path = listOf("ranks", name)
        val minAffixes = reader.integer(path + "min-affixes", default.minAffixes, min = 0)
        return Balance.Rank(
            chance = reader.number(path + "chance", default.chance, min = 0.0),
            minLevel = reader.integer(path + "min-level", default.minLevel, min = 1),
            health = reader.number(path + "health", default.health, min = 1.0),
            damage = reader.number(path + "damage", default.damage, min = 1.0),
            minAffixes = minAffixes,
            maxAffixes = reader.integer(path + "max-affixes", default.maxAffixes, min = minAffixes),
        )
    }

    private fun affixes(reader: YamlReader, default: Balance.AffixTuning): Balance.AffixTuning {
        fun number(name: String, value: Double) = reader.number(listOf("affixes", name), value, min = 0.0)
        fun integer(name: String, value: Int) = reader.integer(listOf("affixes", name), value, min = 0)

        return Balance.AffixTuning(
            armoredDefense = number("armored-defense", default.armoredDefense),
            frenziedSpeed = number("frenzied-speed", default.frenziedSpeed),
            vampiricHeal = number("vampiric-heal", default.vampiricHeal),
            enragedBelow = number("enraged-below", default.enragedBelow),
            enragedDamage = number("enraged-damage", default.enragedDamage),
            moltenSeconds = integer("molten-seconds", default.moltenSeconds),
            frostboundSeconds = integer("frostbound-seconds", default.frostboundSeconds),
            venomousSeconds = integer("venomous-seconds", default.venomousSeconds),
            volatilePower = number("volatile-power", default.volatilePower),
            volatileDelayTicks = integer("volatile-delay-ticks", default.volatileDelayTicks),
            summonerBelow = number("summoner-below", default.summonerBelow),
            summonerMinions = integer("summoner-minions", default.summonerMinions),
            blinkingRange = number("blinking-range", default.blinkingRange),
            blinkingCooldownTicks = integer("blinking-cooldown-ticks", default.blinkingCooldownTicks),
        )
    }
}
