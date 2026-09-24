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
        val reader = Reader(root)
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
        )
        return Result(balance, reader.warnings)
    }

    private class Reader(private val root: Map<*, *>) {

        val warnings = mutableListOf<String>()

        fun number(path: List<String>, default: Double, min: Double): Double {
            val raw = find(path) ?: return default
            val value = (raw as? Number)?.toDouble()
            if (value == null || value.isNaN() || value.isInfinite() || value < min) {
                warnings += "${path.joinToString(".")} must be a number of at least $min, not '$raw'; using $default"
                return default
            }
            return value
        }

        private fun find(path: List<String>): Any? {
            var node: Any? = root
            for (segment in path) node = (node as? Map<*, *>)?.get(segment) ?: return null
            return node
        }
    }
}
