package net.trilleo.mc.plugins.tritown.content

import net.trilleo.mc.plugins.tritown.combat.Stat

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
                lootingMagicFind = reader.number(
                    listOf("vanilla", "looting-magic-find"),
                    default.vanilla.lootingMagicFind,
                    min = 0.0
                ),
                bowAttack = reader.number(listOf("vanilla", "bow-attack"), default.vanilla.bowAttack, min = 0.0),
                arrowReference = reader.number(
                    listOf("vanilla", "arrow-reference"),
                    default.vanilla.arrowReference,
                    min = 0.1
                ),
            ),
            ranks = Balance.Ranks(
                elite = rank(reader, "elite", default.ranks.elite),
                champion = rank(reader, "champion", default.ranks.champion),
            ),
            affixes = affixes(reader, default.affixes),
            gear = gear(reader, default.gear),
            forge = forge(reader, default.forge),
        )
        return Result(balance, reader.warnings)
    }

    private fun gear(reader: YamlReader, default: GearTuning): GearTuning {
        val root = listOf("gear")
        val roll = reader.range(root + "roll", default.rollMin..default.rollMax, min = 1)
        return GearTuning(
            slotPoints = GearSlot.entries.associateWith { slot ->
                reader.number(root + "slot-points" + slot.name.lowercase(), default.points(slot), min = 0.0)
            },
            stats = Stat.entries.associateWith { stat ->
                val path = root + "stats" + name(stat)
                GearTuning.StatCurve(
                    per100 = reader.number(path + "per-100", default.curve(stat).per100, min = 0.0),
                    growth = reader.number(path + "growth", default.curve(stat).growth, min = 1.0),
                )
            },
            rarity = Rarity.entries.associateWith { rarity ->
                reader.number(root + "rarity" + rarity.name.lowercase(), default.rarityMultiplier(rarity), min = 0.0)
            },
            starBonus = reader.number(root + "star-bonus", default.starBonus, min = 0.0),
            maxStars = reader.integer(root + "max-stars", default.maxStars, min = 0),
            rollMin = roll.first,
            rollMax = roll.last,
            reforgeShare = reader.number(root + "reforge-share", default.reforgeShare, min = 0.0),
            craftOdds = odds(reader, root + "craft-odds", default.craftOdds),
            dropOdds = odds(reader, root + "drop-odds", default.dropOdds),
        )
    }

    /** Rarities with their weights; a section left out keeps the default, and a rarity it leaves out is never picked. */
    private fun odds(reader: YamlReader, path: List<String>, default: Map<Rarity, Double>): Map<Rarity, Double> {
        val names = reader.keys(path)
        if (names.isEmpty()) return default
        return names.mapNotNull { name ->
            val rarity = Rarity.of(name) ?: run {
                reader.warn(path + name, "no rarity is called '$name'; left out")
                return@mapNotNull null
            }
            rarity to reader.number(path + name, 0.0, min = 0.0)
        }.toMap()
    }

    private fun forge(reader: YamlReader, default: ForgeTuning): ForgeTuning {
        val root = listOf("forge")
        val capName = reader.text(root + "refine-cap")
        val cap = capName?.let { name ->
            Rarity.of(name) ?: run {
                reader.warn(root + "refine-cap", "no rarity is called '$name'; using ${default.refineCap.name.lowercase()}")
                null
            }
        } ?: default.refineCap
        return ForgeTuning(
            moneyGrowth = reader.number(root + "money-growth", default.moneyGrowth, min = 1.0),
            upgradeEssence = reader.integer(root + "upgrade" + "essence", default.upgradeEssence, min = 0),
            upgradeMoney = reader.number(root + "upgrade" + "money", default.upgradeMoney, min = 0.0),
            refineEssence = reader.integer(root + "refine" + "essence", default.refineEssence, min = 0),
            refineMoney = reader.number(root + "refine" + "money", default.refineMoney, min = 0.0),
            refineCap = cap,
            reforgeEssence = reader.integer(root + "reforge" + "essence", default.reforgeEssence, min = 0),
            reforgeMoney = reader.number(root + "reforge" + "money", default.reforgeMoney, min = 0.0),
            salvageEssence = reader.integer(root + "salvage-essence", default.salvageEssence, min = 0),
        )
    }

    /** A stat's name as the content files write it: `crit-chance`. */
    fun name(stat: Stat): String = stat.name.lowercase().replace('_', '-')

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
