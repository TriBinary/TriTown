package net.trilleo.mc.plugins.tritown.economy

/**
 * The currencies the economy knows about, loaded from `config.yml` before the
 * ledger opens.
 *
 * Reads are lock-free: [load] swaps in a whole new immutable map, so a reload
 * can never expose a half-built registry to the Vault provider, which Towny
 * calls from its own threads.
 */
object CurrencyRegistry {

    @Volatile
    private var currencies: Map<String, Currency> = emptyMap()

    @Volatile
    private var primaryId: String = ""

    /** Whether [load] has run. */
    val isLoaded: Boolean
        get() = currencies.isNotEmpty()

    /**
     * The currency exposed through Vault, and the default for every command
     * that does not name one.
     */
    val primary: Currency
        get() = currencies[primaryId] ?: error("No currencies have been loaded")

    /**
     * Replaces the registry with [loaded], treating [primary] as the Vault-facing currency.
     *
     * @throws IllegalArgumentException when [loaded] is empty, contains duplicate ids, or does not contain [primary]
     */
    fun load(loaded: List<Currency>, primary: String) {
        require(loaded.isNotEmpty()) { "At least one currency must be configured" }
        val byId = loaded.associateBy { it.id.lowercase() }
        require(byId.size == loaded.size) { "Currency ids must be unique" }
        val key = primary.lowercase()
        require(byId.containsKey(key)) { "Primary currency '$primary' is not among the configured currencies" }

        currencies = byId
        primaryId = key
    }

    /** The currency with [id], or `null` when no such currency is configured. */
    fun get(id: String): Currency? = currencies[id.lowercase()]

    /** The currency with [id], falling back to [primary] when [id] is null or unknown. */
    fun getOrPrimary(id: String?): Currency = id?.let { get(it) } ?: primary

    /** Every configured currency id, primary first. */
    fun ids(): List<String> = currencies.keys.sortedBy { if (it == primaryId) "" else it }

    /** Every configured currency. */
    fun all(): Collection<Currency> = currencies.values

    /** Forgets every currency. Used by tests and on shutdown. */
    fun clear() {
        currencies = emptyMap()
        primaryId = ""
    }
}
