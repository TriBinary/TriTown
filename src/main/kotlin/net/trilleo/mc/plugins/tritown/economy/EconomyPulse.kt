package net.trilleo.mc.plugins.tritown.economy

import net.trilleo.mc.plugins.tritown.economy.storage.JsonPulseStorage
import net.trilleo.mc.plugins.tritown.economy.storage.StoredPulse
import net.trilleo.mc.plugins.tritown.economy.storage.StoredPulseBucket
import net.trilleo.mc.plugins.tritown.economy.storage.StoredPulseSample
import net.trilleo.mc.plugins.tritown.economy.storage.StorageSchema
import net.trilleo.mc.plugins.tritown.enums.AccountType
import net.trilleo.mc.plugins.tritown.enums.FlowCategory
import net.trilleo.mc.plugins.tritown.enums.TransactionType
import java.util.EnumMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.LongAdder

/**
 * What the economy as a whole is doing: where money comes from, where it goes,
 * and what is left holding it.
 *
 * Two different things are kept, because they answer different questions.
 * *Buckets* accumulate movements hour by hour as they happen, which is the only
 * way to know what created and destroyed currency. *Samples* measure the ledger
 * outright on a timer, which is the only way to know the supply exactly —
 * adding movements up would drift the moment anything moved money without
 * TriTown seeing it.
 *
 * Money is created when it is deposited from outside the economy and destroyed
 * when it is withdrawn out of it; a transfer moves it between two accounts and
 * changes nothing, so it is counted separately as circulation. Towny moves bank
 * deposits through Vault as a withdrawal and a deposit rather than as a
 * transfer, so those land on both sides of the gross figures and cancel in the
 * net — which is why the net, and the per-category net, are the numbers worth
 * reading.
 *
 * Recording happens on whichever thread moved the money, including Towny's, so
 * everything here is lock-free and never touches the disk. Sampling and writing
 * happen on the flush task.
 */
object EconomyPulse {

    // ── Read models ─────────────────────────────────────────────────────

    /** One column of the flow chart: what happened between [from] and [to]. */
    data class Slice(val from: Long, val to: Long, val created: Long, val destroyed: Long) {
        val net: Long get() = created - destroyed
    }

    /**
     * Everything that moved over one window, in minor units.
     *
     * @param createdBy     money that entered the economy, by what it was for
     * @param destroyedBy   money that left it, by what it was for
     * @param createdTo     money that entered, by the kind of account that received it
     * @param destroyedFrom money that left, by the kind of account it came from
     * @param circulated    money moved between accounts, counted once per transfer
     * @param adjusted      how far balances were moved by being set outright
     */
    data class Flow(
        val from: Long,
        val to: Long,
        val createdBy: Map<FlowCategory, Long>,
        val destroyedBy: Map<FlowCategory, Long>,
        val createdTo: Map<AccountType, Long>,
        val destroyedFrom: Map<AccountType, Long>,
        val circulated: Long,
        val adjusted: Long,
        val movements: Long,
        val transfers: Long,
        val slices: List<Slice>,
    ) {
        val created: Long get() = createdBy.values.sum()
        val destroyed: Long get() = destroyedBy.values.sum()
        val net: Long get() = created - destroyed

        /** Whether anything at all happened in this window. */
        val isEmpty: Boolean get() = movements == 0L && transfers == 0L

        /** What [category] did to the supply: positive when it feeds the economy, negative when it drains it. */
        fun netOf(category: FlowCategory): Long = (createdBy[category] ?: 0L) - (destroyedBy[category] ?: 0L)

        /** Every category that saw any money at all, largest gross movement first. */
        fun categories(): List<FlowCategory> = (createdBy.keys + destroyedBy.keys)
            .sortedByDescending { (createdBy[it] ?: 0L) + (destroyedBy[it] ?: 0L) }
    }

    /**
     * The ledger as it stood when it was measured.
     *
     * @param supply        total held, by account type
     * @param accounts      how many accounts of each type exist
     * @param activeWallets player wallets whose balance changed in the last week
     * @param topShare      the share of player wealth held by the richest tenth, 0 to 1
     * @param gini          inequality across player wallets, 0 (identical) to 1 (one player holds everything)
     */
    data class Supply(
        val at: Long,
        val supply: Map<AccountType, Long>,
        val accounts: Map<AccountType, Int>,
        val activeWallets: Int,
        val median: Long,
        val mean: Long,
        val richest: Long,
        val topShare: Double,
        val gini: Double,
    ) {
        /** Every unit of currency in existence. */
        val total: Long get() = supply.values.sum()

        /** How much is held in player wallets rather than by a town, a nation or the server. */
        val held: Long get() = (supply[AccountType.PLAYER] ?: 0L) + (supply[AccountType.UNKNOWN] ?: 0L)

        /** How much sits in town and nation banks. */
        val banked: Long get() = (supply[AccountType.TOWN] ?: 0L) + (supply[AccountType.NATION] ?: 0L)

        /** How many accounts there are of every kind. */
        val accountCount: Int get() = accounts.values.sum()

        /** How many player wallets there are. */
        val wallets: Int get() = (accounts[AccountType.PLAYER] ?: 0) + (accounts[AccountType.UNKNOWN] ?: 0)
    }

    // ── State ───────────────────────────────────────────────────────────

    private val buckets = ConcurrentHashMap<Long, Bucket>()
    private val samples = ConcurrentHashMap<Long, Supply>()
    private val dirty = AtomicBoolean(false)

    private var storage: JsonPulseStorage? = null
    private var retentionHours: Long = 0L

    /** The currency the figures are in. A movement in any other currency is ignored rather than added to it. */
    @Volatile
    private var currencyId: String = ""

    /**
     * Whether statistics are switched on and loaded.
     *
     * Written last by [start] and read first by [record], so a recorder that
     * sees this set is guaranteed to see everything [start] published before it.
     */
    @Volatile
    var isEnabled: Boolean = false
        private set

    // ── Lifecycle ───────────────────────────────────────────────────────

    /**
     * Loads whatever was stored and starts recording.
     *
     * Called from `onLoad` alongside the rest of the economy, because Towny can
     * move money through the Vault provider before TriTown has enabled and
     * those movements should be counted like any other.
     *
     * @param retentionDays how far back the history reaches; 0 keeps everything
     */
    fun start(store: JsonPulseStorage, currency: Currency, retentionDays: Int) {
        buckets.clear()
        samples.clear()

        storage = store
        currencyId = currency.id
        retentionHours = retentionDays.coerceAtLeast(0).toLong() * 24L
        isEnabled = true

        store.load()?.let(::restore)
        prune()
        dirty.set(false)
    }

    /** Writes anything outstanding and stops recording. */
    fun shutdown() {
        if (!isEnabled) return
        isEnabled = false
        flush()
        storage = null
        buckets.clear()
        samples.clear()
    }

    // ── Recording ───────────────────────────────────────────────────────

    /**
     * Files one movement of money.
     *
     * Called from [EconomyService] for every transaction it records, on
     * whichever thread made it.
     *
     * @param holder what kind of account the movement is filed against
     */
    fun record(
        type: TransactionType,
        currency: Currency,
        amount: Money,
        holder: AccountType,
        source: String,
        reason: String,
    ) {
        if (!isEnabled || currency.id != currencyId) return

        val minor = amount.abs().minor
        if (minor == 0L) return

        val bucket = buckets.computeIfAbsent(hourOf(System.currentTimeMillis())) { Bucket() }
        bucket.movements.increment()

        when (type) {
            TransactionType.DEPOSIT -> {
                bucket.createdBy.adder(FlowCategory.of(source, reason)).add(minor)
                bucket.createdTo.adder(holder).add(minor)
            }

            TransactionType.WITHDRAW -> {
                bucket.destroyedBy.adder(FlowCategory.of(source, reason)).add(minor)
                bucket.destroyedFrom.adder(holder).add(minor)
            }

            // A transfer is recorded against both accounts, so only one side is
            // counted or every payment would show up as twice its own size.
            TransactionType.TRANSFER_OUT -> {
                bucket.circulated.add(minor)
                bucket.transfers.increment()
            }

            TransactionType.TRANSFER_IN -> Unit

            // The record carries how far the balance moved but not which way, so
            // a set is kept apart from the money it did or did not create.
            TransactionType.SET -> bucket.adjusted.add(minor)

            TransactionType.CLOSED -> Unit
        }

        dirty.set(true)
    }

    /**
     * Measures the ledger and keeps the result as this hour's sample.
     *
     * Walks every account and sorts the player wallets, so this belongs on the
     * flush task next to the leaderboard rebuild and nowhere near the server
     * thread.
     */
    fun sample(ledger: EconomyLedger, currency: Currency) {
        if (!isEnabled || currency.id != currencyId) return

        val supply = EnumMap<AccountType, Long>(AccountType::class.java)
        val counts = EnumMap<AccountType, Int>(AccountType::class.java)
        val wallets = ArrayList<Long>(ledger.size)
        val activeSince = System.currentTimeMillis() - ACTIVE_WINDOW_MS
        var active = 0

        for (account in ledger.accounts()) {
            val balance = account.balance(currency.id).minor
            supply.merge(account.type, balance, Long::plus)
            counts.merge(account.type, 1, Int::plus)

            if (account.type == AccountType.PLAYER || account.type == AccountType.UNKNOWN) {
                wallets.add(balance)
                if (account.updatedAt >= activeSince) active++
            }
        }

        wallets.sort()
        val now = System.currentTimeMillis()
        val measurement = Supply(
            at = now,
            supply = supply,
            accounts = counts,
            activeWallets = active,
            median = median(wallets),
            mean = if (wallets.isEmpty()) 0L else wallets.sum() / wallets.size,
            richest = wallets.lastOrNull() ?: 0L,
            topShare = topShare(wallets),
            gini = gini(wallets),
        )

        // The sample is always replaced, so the panel shows when the ledger was
        // last looked at, but an idle server does not rewrite the whole file
        // every flush just because that time moved on.
        val previous = samples.put(hourOf(now), measurement)
        if (previous == null || previous.copy(at = now) != measurement) dirty.set(true)

        prune()
    }

    /** Writes the statistics out, if anything has changed since the last write. */
    fun flush() {
        val store = storage ?: return
        if (!dirty.getAndSet(false)) return
        store.save(snapshot())
    }

    // ── Reading ─────────────────────────────────────────────────────────

    /**
     * Everything recorded over the last [hours], split into [slices] equal
     * columns.
     *
     * @param hours how far back to reach; 0 or less means everything still kept
     */
    fun window(hours: Int, slices: Int = 7): Flow {
        val now = System.currentTimeMillis()
        val from = if (hours > 0) now - hours * HOUR_MS else earliest() ?: now
        return window(from, now, slices)
    }

    /** The newest sample taken at or before [millis], or `null` when the ledger had not been measured by then. */
    fun sampleAt(millis: Long): Supply? = samples.values.filter { it.at <= millis }.maxByOrNull { it.at }

    /** The most recent measurement of the ledger, or `null` when none has been taken yet. */
    fun latest(): Supply? = samples.values.maxByOrNull { it.at }

    /** When the oldest record still kept starts, or `null` when nothing is recorded. */
    fun earliest(): Long? {
        val bucket = buckets.keys.minOrNull()
        val sample = samples.keys.minOrNull()
        val hour = listOfNotNull(bucket, sample).minOrNull() ?: return null
        return hour * HOUR_MS
    }

    // ── Internals ───────────────────────────────────────────────────────

    private fun window(from: Long, to: Long, slices: Int): Flow {
        val createdBy = EnumMap<FlowCategory, Long>(FlowCategory::class.java)
        val destroyedBy = EnumMap<FlowCategory, Long>(FlowCategory::class.java)
        val createdTo = EnumMap<AccountType, Long>(AccountType::class.java)
        val destroyedFrom = EnumMap<AccountType, Long>(AccountType::class.java)
        val columns = LongArray(slices.coerceAtLeast(1) * 2)

        var circulated = 0L
        var adjusted = 0L
        var movements = 0L
        var transfers = 0L

        val span = (to - from).coerceAtLeast(1L)
        for ((hour, bucket) in buckets) {
            val start = hour * HOUR_MS
            // The hour the window starts in is half inside it; counting all of it
            // is closer to the truth than dropping it, since a bucket cannot be split.
            if (start + HOUR_MS <= from || start > to) continue

            for ((category, adder) in bucket.createdBy) createdBy.merge(category, adder.sum(), Long::plus)
            for ((category, adder) in bucket.destroyedBy) destroyedBy.merge(category, adder.sum(), Long::plus)
            for ((type, adder) in bucket.createdTo) createdTo.merge(type, adder.sum(), Long::plus)
            for ((type, adder) in bucket.destroyedFrom) destroyedFrom.merge(type, adder.sum(), Long::plus)

            circulated += bucket.circulated.sum()
            adjusted += bucket.adjusted.sum()
            movements += bucket.movements.sum()
            transfers += bucket.transfers.sum()

            val column = (((start - from).coerceAtLeast(0L) * slices) / span).toInt().coerceIn(0, slices - 1)
            columns[column * 2] += bucket.createdBy.values.sumOf { it.sum() }
            columns[column * 2 + 1] += bucket.destroyedBy.values.sumOf { it.sum() }
        }

        val width = span / slices
        return Flow(
            from = from,
            to = to,
            createdBy = createdBy,
            destroyedBy = destroyedBy,
            createdTo = createdTo,
            destroyedFrom = destroyedFrom,
            circulated = circulated,
            adjusted = adjusted,
            movements = movements,
            transfers = transfers,
            slices = List(slices) { index ->
                Slice(
                    from = from + width * index,
                    to = if (index == slices - 1) to else from + width * (index + 1),
                    created = columns[index * 2],
                    destroyed = columns[index * 2 + 1],
                )
            },
        )
    }

    private fun prune() {
        if (retentionHours <= 0L) return
        val oldest = hourOf(System.currentTimeMillis()) - retentionHours
        buckets.keys.removeIf { it < oldest }
        samples.keys.removeIf { it < oldest }
    }

    private fun snapshot(): StoredPulse = StoredPulse(
        v = StorageSchema.CURRENT,
        currency = currencyId,
        buckets = buckets.entries.sortedBy { it.key }.map { (hour, bucket) ->
            StoredPulseBucket(
                hour = hour,
                created = bucket.createdBy.sums(),
                destroyed = bucket.destroyedBy.sums(),
                createdTo = bucket.createdTo.sums(),
                destroyedFrom = bucket.destroyedFrom.sums(),
                circulated = bucket.circulated.sum(),
                adjusted = bucket.adjusted.sum(),
                movements = bucket.movements.sum(),
                transfers = bucket.transfers.sum(),
            )
        },
        samples = samples.entries.sortedBy { it.key }.map { (hour, sample) ->
            StoredPulseSample(
                hour = hour,
                at = sample.at,
                supply = sample.supply.mapKeys { it.key.name },
                accounts = sample.accounts.mapKeys { it.key.name },
                activeWallets = sample.activeWallets,
                median = sample.median,
                mean = sample.mean,
                richest = sample.richest,
                topShare = sample.topShare,
                gini = sample.gini,
            )
        },
    )

    /**
     * Seeds the statistics from [stored].
     *
     * Figures written in another currency are dropped rather than adopted: the
     * numbers are minor units, so reading cents as thousandths would multiply
     * every past hour by ten.
     */
    private fun restore(stored: StoredPulse) {
        if (stored.currency.isNotEmpty() && stored.currency != currencyId) return

        for (entry in stored.buckets) {
            buckets[entry.hour] = Bucket().apply {
                for ((name, value) in entry.created) category(name)?.let { createdBy.adder(it).add(value) }
                for ((name, value) in entry.destroyed) category(name)?.let { destroyedBy.adder(it).add(value) }
                for ((name, value) in entry.createdTo) accountType(name)?.let { createdTo.adder(it).add(value) }
                for ((name, value) in entry.destroyedFrom) accountType(name)?.let { destroyedFrom.adder(it).add(value) }
                circulated.add(entry.circulated)
                adjusted.add(entry.adjusted)
                movements.add(entry.movements)
                transfers.add(entry.transfers)
            }
        }

        for (entry in stored.samples) {
            samples[entry.hour] = Supply(
                at = entry.at,
                supply = entry.supply.mapNotNull { (name, value) -> accountType(name)?.let { it to value } }.toMap(),
                accounts = entry.accounts.mapNotNull { (name, value) -> accountType(name)?.let { it to value } }
                    .toMap(),
                activeWallets = entry.activeWallets,
                median = entry.median,
                mean = entry.mean,
                richest = entry.richest,
                topShare = entry.topShare,
                gini = entry.gini,
            )
        }
    }

    private fun category(name: String): FlowCategory? =
        runCatching { FlowCategory.valueOf(name) }.getOrNull()

    private fun accountType(name: String): AccountType? =
        runCatching { AccountType.valueOf(name) }.getOrNull()

    private fun median(sorted: List<Long>): Long = when {
        sorted.isEmpty() -> 0L
        sorted.size % 2 == 1 -> sorted[sorted.size / 2]
        else -> (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2
    }

    /** The share of all player wealth held by the richest tenth, which is the figure a server owner feels. */
    private fun topShare(sorted: List<Long>): Double {
        if (sorted.isEmpty()) return 0.0
        val total = sorted.sumOf { it.coerceAtLeast(0L) }
        if (total <= 0L) return 0.0
        val tenth = ((sorted.size + 9) / 10).coerceAtLeast(1)
        val top = sorted.takeLast(tenth).sumOf { it.coerceAtLeast(0L) }
        return top.toDouble() / total.toDouble()
    }

    /**
     * The Gini coefficient of the player wallets, from 0 (everyone holds the
     * same) to 1 (one player holds everything).
     *
     * Debts are read as zero, because the measure is defined over non-negative
     * shares and a server that allows overdrafts should not be able to push it
     * past 1.
     */
    private fun gini(sorted: List<Long>): Double {
        if (sorted.size < 2) return 0.0

        var total = 0.0
        var weighted = 0.0
        for ((index, value) in sorted.withIndex()) {
            val amount = value.coerceAtLeast(0L).toDouble()
            total += amount
            weighted += amount * (index + 1)
        }
        if (total <= 0.0) return 0.0

        val n = sorted.size
        return ((2.0 * weighted) / (n * total) - (n + 1.0) / n).coerceIn(0.0, 1.0)
    }

    private fun hourOf(millis: Long): Long = millis / HOUR_MS

    private fun <K> ConcurrentHashMap<K, LongAdder>.adder(key: K): LongAdder =
        computeIfAbsent(key) { LongAdder() }

    private fun <K : Enum<K>> ConcurrentHashMap<K, LongAdder>.sums(): Map<String, Long> =
        entries.associate { (key, adder) -> key.name to adder.sum() }

    /** One hour of movements. Every counter is an adder, so recording never blocks another thread. */
    private class Bucket {
        val createdBy = ConcurrentHashMap<FlowCategory, LongAdder>()
        val destroyedBy = ConcurrentHashMap<FlowCategory, LongAdder>()
        val createdTo = ConcurrentHashMap<AccountType, LongAdder>()
        val destroyedFrom = ConcurrentHashMap<AccountType, LongAdder>()
        val circulated = LongAdder()
        val adjusted = LongAdder()
        val movements = LongAdder()
        val transfers = LongAdder()
    }

    private const val HOUR_MS = 60L * 60L * 1000L
    private const val ACTIVE_WINDOW_MS = 7L * 24L * HOUR_MS
}
