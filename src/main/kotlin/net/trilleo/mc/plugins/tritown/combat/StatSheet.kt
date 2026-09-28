package net.trilleo.mc.plugins.tritown.combat

/**
 * A value for every [Stat], unset ones being zero.
 *
 * Immutable, so a player's sheet can be cached and read from anywhere without
 * a copy.
 */
class StatSheet private constructor(private val values: DoubleArray) {

    operator fun get(stat: Stat): Double = values[stat.ordinal]

    operator fun plus(other: StatSheet): StatSheet =
        StatSheet(DoubleArray(values.size) { values[it] + other.values[it] })

    /** Whether every stat is zero, so a source with nothing to add can be left out of a breakdown. */
    val isEmpty: Boolean
        get() = values.all { it == 0.0 }

    override fun equals(other: Any?): Boolean = other is StatSheet && values.contentEquals(other.values)

    override fun hashCode(): Int = values.contentHashCode()

    override fun toString(): String =
        Stat.entries.filter { this[it] != 0.0 }.joinToString(prefix = "StatSheet(", postfix = ")") { "$it=${this[it]}" }

    companion object {

        val EMPTY = StatSheet(DoubleArray(Stat.entries.size))

        fun of(vararg entries: Pair<Stat, Double>): StatSheet {
            val values = DoubleArray(Stat.entries.size)
            entries.forEach { (stat, value) -> values[stat.ordinal] += value }
            return StatSheet(values)
        }
    }
}
