package net.trilleo.mc.plugins.tritown.enums

/** How TriTown decides whether to supply the server's Vault economy. */
enum class ProviderMode {

    /**
     * Supply the economy unless one of the plugins under `economy.provider.defer-to`
     * is installed.
     *
     * The check is on what is *installed* rather than on what has already
     * registered with Vault. TriTown has to register during `onLoad`, which is
     * the only point early enough for Towny to find it, and at that moment no
     * economy plugin has registered anything yet — they all do it when they
     * enable. As a second line of defence this mode registers at a low service
     * priority, so any other provider still wins.
     */
    AUTO,

    /** Always supply the economy, at the highest service priority. */
    INTERNAL,

    /** Never supply the economy; use whichever provider another plugin registers. */
    EXTERNAL;

    companion object {
        /** Parses [value], falling back to [AUTO] for anything unrecognised. */
        fun parse(value: String): ProviderMode =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: AUTO
    }
}
