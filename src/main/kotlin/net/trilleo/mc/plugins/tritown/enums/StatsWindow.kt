package net.trilleo.mc.plugins.tritown.enums

/**
 * How far back the admin panel's figures reach.
 *
 * The window is a property of who is looking rather than of the data: the
 * statistics are kept hour by hour, and a window is simply how many of those
 * hours are added up.
 */
enum class StatsWindow(val hours: Int) {

    DAY(24),
    WEEK(24 * 7),
    MONTH(24 * 30),

    /** Everything still on record, however far back the retention reaches. */
    ALL(0);

    /** The translation key naming this window, spelled out so the language test can see it. */
    val key: String
        get() = when (this) {
            DAY -> "gui.admin-economy.window-day"
            WEEK -> "gui.admin-economy.window-week"
            MONTH -> "gui.admin-economy.window-month"
            ALL -> "gui.admin-economy.window-all"
        }

    /** The next window in the cycle, so a click can step through them. */
    fun next(): StatsWindow = entries[(ordinal + 1) % entries.size]

    /** The previous window in the cycle. */
    fun previous(): StatsWindow = entries[(ordinal + entries.size - 1) % entries.size]
}
