package net.trilleo.mc.plugins.tritown.economy

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The encoded reason is written to the transaction log, so its shape is part of
 * the on-disk format rather than an implementation detail.
 */
class TransactionReasonTest {

    @Test
    fun `a reason without arguments is the bare key`() {
        assertEquals(TransactionReason.PAYMENT, TransactionReason.of(TransactionReason.PAYMENT))
    }

    @Test
    fun `arguments are appended so the log line stays one string`() {
        assertEquals(
            "money.reason.admin-set?admin=Steve",
            TransactionReason.of(TransactionReason.ADMIN_SET, "admin" to "Steve"),
        )
        assertEquals(
            "money.reason.town-deleted?name=Riverbend&by=Alex",
            TransactionReason.of(TransactionReason.TOWN_DELETED, "name" to "Riverbend", "by" to "Alex"),
        )
    }
}
