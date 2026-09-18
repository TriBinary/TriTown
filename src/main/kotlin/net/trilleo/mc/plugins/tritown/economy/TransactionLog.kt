package net.trilleo.mc.plugins.tritown.economy

import net.trilleo.mc.plugins.tritown.enums.TransactionType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong
import kotlin.collections.ArrayDeque
import kotlin.collections.ArrayList
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.asReversed
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.emptyList
import kotlin.collections.emptyMap
import kotlin.collections.forEach
import kotlin.collections.isNotEmpty
import kotlin.collections.iterator
import kotlin.collections.set
import kotlin.collections.sortedBy
import kotlin.collections.toList

/**
 * Keeps a short, readable history per account in memory, and queues everything
 * for the on-disk log.
 *
 * The split matters. The history view reads the in-memory ring, so opening it
 * never touches the disk. The queue is drained by the flush task, so recording
 * a transaction never blocks the thread that made it — which is important,
 * because Towny makes plenty of them from its own threads.
 *
 * Rings are created lazily, so only accounts that have actually been touched
 * since startup take up any memory.
 *
 * @param maxPerAccount how many records each account keeps in memory
 */
class TransactionLog(private val maxPerAccount: Int) {

    private val rings = ConcurrentHashMap<UUID, ArrayDeque<TransactionRecord>>()
    private val pending = ConcurrentLinkedQueue<TransactionRecord>()
    private val nextId = AtomicLong(1L)

    /** Seeds the in-memory history from storage and continues ids after [lastId]. */
    fun seed(lastId: Long, history: Map<UUID, List<TransactionRecord>>) {
        nextId.set(lastId + 1L)
        for ((account, records) in history) {
            val ring = ArrayDeque<TransactionRecord>(maxPerAccount)
            records.sortedBy { it.id }.forEach { ring.addLast(it) }
            while (ring.size > maxPerAccount) ring.removeFirst()
            rings[account] = ring
        }
    }

    /** Files a record against [account] and queues it for the on-disk log. */
    fun record(
        account: UUID,
        counterparty: UUID?,
        currency: Currency,
        type: TransactionType,
        amount: Money,
        balanceAfter: Money,
        meta: Map<String, String> = emptyMap(),
    ): TransactionRecord {
        val context = EconomyContext.current()
        val record = TransactionRecord(
            id = nextId.getAndIncrement(),
            timestamp = System.currentTimeMillis(),
            account = account,
            counterparty = counterparty,
            currency = currency.id,
            type = type,
            amount = amount.abs().minor,
            balanceAfter = balanceAfter.minor,
            source = context.source,
            reason = context.reason,
            meta = meta,
        )

        val ring = rings.computeIfAbsent(account) { ArrayDeque(maxPerAccount) }
        synchronized(ring) {
            ring.addLast(record)
            while (ring.size > maxPerAccount) ring.removeFirst()
        }
        pending.add(record)
        return record
    }

    /** The history kept for [account], most recent first. */
    fun recent(account: UUID): List<TransactionRecord> {
        val ring = rings[account] ?: return emptyList()
        return synchronized(ring) { ring.toList() }.asReversed()
    }

    /** Takes everything waiting to be written and clears the queue. */
    fun drainPending(): List<TransactionRecord> {
        if (pending.isEmpty()) return emptyList()
        val drained = ArrayList<TransactionRecord>()
        while (true) {
            drained.add(pending.poll() ?: break)
        }
        return drained
    }

    /** Whether anything is waiting to be written. */
    fun hasPendingWrites(): Boolean = pending.isNotEmpty()

    /** Drops the history kept for [account]. */
    fun forget(account: UUID) {
        rings.remove(account)
    }

    /** Drops every ring, without touching what is already queued for the disk. */
    fun clear() {
        rings.clear()
    }
}
