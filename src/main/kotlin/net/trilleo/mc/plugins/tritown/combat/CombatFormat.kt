package net.trilleo.mc.plugins.tritown.combat

import java.util.*
import kotlin.math.ceil
import kotlin.math.roundToLong

/** How combat numbers are written: whole, with thousands grouped, the way they read at a glance. */
object CombatFormat {

    fun number(value: Double): String = String.format(Locale.ROOT, "%,d", value.roundToLong())

    /** Health still left, rounded up, so anything alive never reads as 0. */
    fun health(value: Double): String = String.format(Locale.ROOT, "%,d", ceil(value).toLong())

    fun stat(stat: Stat, value: Double): String = if (stat.percent) "${number(value)}%" else number(value)
}
