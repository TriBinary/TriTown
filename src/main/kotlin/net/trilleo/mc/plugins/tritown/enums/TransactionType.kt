package net.trilleo.mc.plugins.tritown.enums

/** What a recorded economy transaction did to the account it is filed against. */
enum class TransactionType {

    /** Money arrived from outside the economy — a payout, a refund, the starting balance. */
    DEPOSIT,

    /** Money left the economy — a cost, a fee, a purchase. */
    WITHDRAW,

    /** Money arrived from another account. */
    TRANSFER_IN,

    /** Money went to another account. */
    TRANSFER_OUT,

    /** The balance was set outright rather than adjusted. */
    SET,

    /** The account was closed, because the town or nation behind it was deleted. */
    CLOSED;

    /** Whether the account gained money. */
    val isCredit: Boolean
        get() = this == DEPOSIT || this == TRANSFER_IN
}
