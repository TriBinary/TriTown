package net.trilleo.mc.plugins.tritown.economy.storage

/**
 * The economy statistics as they are written to storage.
 *
 * Amounts are minor units of the primary currency throughout, and the maps are
 * keyed by enum name rather than by ordinal so that adding a category later
 * cannot silently re-label the history already on disk.
 *
 * @param buckets one entry per hour that saw any movement
 * @param samples one entry per hour the ledger was measured in
 */
data class StoredPulse(
    val v: Int,
    val currency: String,
    val buckets: List<StoredPulseBucket>,
    val samples: List<StoredPulseSample>,
)

/**
 * What one hour of the economy did.
 *
 * @param hour            hours since the epoch, which is what the bucket is keyed by
 * @param created         money that entered the economy, by [net.trilleo.mc.plugins.tritown.enums.FlowCategory]
 * @param destroyed       money that left it, by category
 * @param createdTo       money that entered, by the kind of account that received it
 * @param destroyedFrom   money that left, by the kind of account it came from
 * @param circulated      money moved between two accounts, counted once per transfer
 * @param adjusted        how far balances were moved by being set outright
 */
data class StoredPulseBucket(
    val hour: Long,
    val created: Map<String, Long>,
    val destroyed: Map<String, Long>,
    val createdTo: Map<String, Long>,
    val destroyedFrom: Map<String, Long>,
    val circulated: Long,
    val adjusted: Long,
    val movements: Long,
    val transfers: Long,
)

/**
 * The state of the ledger at one moment, measured rather than accumulated.
 *
 * The supply is read straight off the accounts, so it is exact however the
 * movements that produced it were attributed.
 *
 * @param hour          hours since the epoch; one sample is kept per hour
 * @param at            when the sample was actually taken
 * @param supply        total held, by account type
 * @param accounts      how many accounts of each type exist
 * @param activeWallets player wallets whose balance changed in the last week
 * @param topShare      the share of player wealth held by the richest tenth, 0 to 1
 * @param gini          inequality across player wallets, 0 (identical) to 1 (one player holds everything)
 */
data class StoredPulseSample(
    val hour: Long,
    val at: Long,
    val supply: Map<String, Long>,
    val accounts: Map<String, Int>,
    val activeWallets: Int,
    val median: Long,
    val mean: Long,
    val richest: Long,
    val topShare: Double,
    val gini: Double,
)
