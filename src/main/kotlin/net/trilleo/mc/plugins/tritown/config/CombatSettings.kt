package net.trilleo.mc.plugins.tritown.config

/**
 * An immutable snapshot of the `combat` block of `config.yml`.
 *
 * @param disabledWorlds worlds, by lower-case name, where every hit stays vanilla and mobs are not levelled
 * @param actionBar whether players see their health and Defense above the hotbar
 * @param damageIndicators whether hits on mobs float their damage up from the mob
 * @param indicatorLimit the most damage indicators that may exist at once, across the server
 */
data class CombatSettings(
    val enabled: Boolean,
    val disabledWorlds: Set<String>,
    val actionBar: Boolean,
    val damageIndicators: Boolean,
    val indicatorLimit: Int,
) {

    companion object {

        @Volatile
        private var current: CombatSettings? = null

        val snapshot: CombatSettings
            get() = current ?: error("Combat settings have not been loaded yet")

        val isLoaded: Boolean
            get() = current != null

        fun load(config: PluginConfig): CombatSettings = read(config).also { current = it }

        private fun read(config: PluginConfig): CombatSettings = CombatSettings(
            enabled = config.getBoolean("combat.enabled", true),
            disabledWorlds = config.getStringList("combat.disabled-worlds").map { it.lowercase() }.toSet(),
            actionBar = config.getBoolean("combat.hud.action-bar", true),
            damageIndicators = config.getBoolean("combat.hud.damage-indicators", true),
            indicatorLimit = config.getInt("combat.hud.indicator-limit", 64).coerceAtLeast(0),
        )
    }
}
