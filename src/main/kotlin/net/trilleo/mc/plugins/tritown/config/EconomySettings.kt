package net.trilleo.mc.plugins.tritown.config

import net.trilleo.mc.plugins.tritown.economy.Currency
import net.trilleo.mc.plugins.tritown.economy.LedgerLimits
import net.trilleo.mc.plugins.tritown.economy.Money
import net.trilleo.mc.plugins.tritown.enums.ProviderMode

/**
 * An immutable snapshot of the `economy` block of `config.yml`.
 *
 * The economy is read from Towny's threads, so settings are never read field by
 * field from a live `FileConfiguration`. A reload builds a whole new snapshot
 * and swaps it in, which means no caller can ever observe a half-applied
 * configuration.
 */
data class EconomySettings(
    val enabled: Boolean,
    val providerMode: ProviderMode,
    val deferTo: List<String>,
    val currencies: List<Currency>,
    val primaryCurrencyId: String,
    val startingBalance: Double,
    val balanceCap: Double,
    val minimumPayment: Double,
    val allowNegativeBalances: Boolean,
    val storageType: String,
    val flushIntervalSeconds: Long,
    val allowRescale: Boolean,
) {

    /** The bounds the ledger should enforce, with the balance cap converted per currency. */
    fun ledgerLimits(): LedgerLimits = LedgerLimits(
        capByCurrency = if (balanceCap <= 0.0) {
            emptyMap()
        } else {
            currencies.associate { it.id to it.of(balanceCap).minor }
        },
        allowNegativeBalances = allowNegativeBalances,
    )

    /** [startingBalance] in the primary currency's minor units. */
    fun startingBalance(currency: Currency): Money = currency.of(startingBalance)

    companion object {

        /** Economy plugins `auto` mode stands aside for when they are installed. */
        private val DEFAULT_DEFER_TO = listOf(
            "Essentials", "EssentialsX", "CMI", "XConomy", "GemsEconomy", "iConomy", "BOSEconomy", "EconomyAPI",
        )

        @Volatile
        private var current: EconomySettings? = null

        /** The settings in force. */
        val snapshot: EconomySettings
            get() = current ?: error("Economy settings have not been loaded yet")

        /** Whether [load] has run. */
        val isLoaded: Boolean
            get() = current != null

        /** Reads the `economy` block from [config] and makes it the current snapshot. */
        fun load(config: PluginConfig): EconomySettings = read(config).also { current = it }

        private fun read(config: PluginConfig): EconomySettings {
            val digits = config.getInt("economy.currency.fractional-digits", 2).coerceIn(0, 6)
            val currency = Currency(
                id = config.getString("economy.currency.id", "dollar"),
                singular = config.getString("economy.currency.singular", "Dollar"),
                plural = config.getString("economy.currency.plural", "Dollars"),
                symbol = config.getString("economy.currency.symbol", "$"),
                fractionalDigits = digits,
                plainFormat = config.getString("economy.currency.format", "%symbol%%amount%"),
                richFormat = config.getString("economy.currency.rich-format", "<gold>%symbol%%amount%</gold>"),
            )

            val deferTo = config.getStringList("economy.provider.defer-to")
                .ifEmpty { DEFAULT_DEFER_TO }

            return EconomySettings(
                enabled = config.getBoolean("economy.enabled", true),
                providerMode = ProviderMode.parse(config.getString("economy.provider.mode", "auto")),
                deferTo = deferTo,
                currencies = listOf(currency),
                primaryCurrencyId = currency.id,
                startingBalance = config.getDouble("economy.starting-balance", 100.0),
                balanceCap = config.getDouble("economy.balance-cap", 1_000_000_000.0),
                minimumPayment = config.getDouble("economy.minimum-payment", 0.01),
                allowNegativeBalances = config.getBoolean("economy.allow-negative-balances", false),
                storageType = config.getString("economy.storage.type", "json").lowercase(),
                flushIntervalSeconds = config.getLong("economy.storage.flush-interval", 60L).coerceAtLeast(5L),
                allowRescale = config.getBoolean("economy.storage.allow-rescale", false),
            )
        }
    }
}
