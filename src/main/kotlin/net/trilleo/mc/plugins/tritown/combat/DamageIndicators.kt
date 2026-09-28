package net.trilleo.mc.plugins.tritown.combat

import net.trilleo.mc.plugins.tritown.Main
import net.trilleo.mc.plugins.tritown.config.CombatSettings
import net.trilleo.mc.plugins.tritown.utils.ComponentUtil
import net.trilleo.mc.plugins.tritown.utils.Lang
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.entity.Display
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.TextDisplay
import org.bukkit.util.Vector
import java.util.concurrent.ThreadLocalRandom

/**
 * The damage a hit did, floating up from the mob it hit.
 *
 * Each is a text display that lives for under a second. It is never saved, so
 * one left behind by a crash or a chunk unloading mid-float is simply gone,
 * and [clearAll] takes the rest down as TriTown stops. At most
 * `combat.hud.indicator-limit` exist at once, so a crowd of players farming a
 * crowd of mobs cannot flood the server with them.
 */
object DamageIndicators {

    private const val LIFETIME = 16L
    private const val RISE_DELAY = 1L
    private const val RISE_TICKS = 12
    private const val RISE = 0.7

    private val live = HashSet<TextDisplay>()

    fun show(victim: LivingEntity, damage: Double, crit: Boolean) {
        val settings = CombatSettings.snapshot
        if (!settings.damageIndicators || live.size >= settings.indicatorLimit) return

        val random = ThreadLocalRandom.current()
        val offset = Vector(random.nextDouble(-0.5, 0.5), random.nextDouble(0.0, 0.4), random.nextDouble(-0.5, 0.5))
        val location = victim.location.add(0.0, victim.height * 0.75, 0.0).add(offset)
        val text = Lang.tr(
            null,
            if (crit) "combat.indicator.crit" else "combat.indicator.hit",
            "amount" to CombatFormat.number(damage),
        )

        val display = victim.world.spawn(location, TextDisplay::class.java) {
            it.text(ComponentUtil.parse(text))
            it.billboard = Display.Billboard.CENTER
            it.backgroundColor = Color.fromARGB(0, 0, 0, 0)
            it.isShadowed = true
            it.isPersistent = false
            it.teleportDuration = RISE_TICKS
        }
        live += display

        val scheduler = Bukkit.getScheduler()
        scheduler.runTaskLater(Main.instance, Runnable {
            if (display.isValid) display.teleport(display.location.add(0.0, RISE, 0.0))
        }, RISE_DELAY)
        scheduler.runTaskLater(Main.instance, Runnable { remove(display) }, LIFETIME)
    }

    fun clearAll() {
        live.toList().forEach(::remove)
    }

    private fun remove(display: TextDisplay) {
        live -= display
        display.remove()
    }
}
