package net.trilleo.mc.plugins.tritown.enums

/**
 * What a TriTown economy account represents.
 *
 * Towny addresses town, nation and NPC bank accounts through the same Vault
 * player-account methods real players use, passing a synthetic offline player
 * whose name carries a configured prefix. The type is resolved once from that
 * name when the account is created, so the rest of the economy never has to
 * ask Towny again.
 */
enum class AccountType {

    /** A real player's wallet. Only these appear on the balance leaderboard. */
    PLAYER,

    /** A Towny town's bank, addressed by Towny as `<town prefix><name>`. */
    TOWN,

    /** A Towny nation's bank, addressed by Towny as `<nation prefix><name>`. */
    NATION,

    /** An NPC resident's wallet, addressed by Towny as `<NPC prefix><name>`. */
    NPC,

    /** Towny's closed-economy server account, which absorbs money that would otherwise vanish. */
    SERVER,

    /**
     * An account whose owner has not been identified yet.
     *
     * Towny hands out a synthetic offline player for offline residents too, so
     * a wallet created before its owner has joined is indistinguishable from a
     * stranger's. It is promoted to [PLAYER] the first time that UUID joins.
     */
    UNKNOWN;

    /** Whether this is a Towny town or nation bank. */
    val isGovernment: Boolean
        get() = this == TOWN || this == NATION
}
