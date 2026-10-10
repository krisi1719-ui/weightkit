package com.weightkit.app.model

/**
 * Replaces Play Billing in debug UI tests (Play only sells to apps uploaded to the Play Console).
 * Only the debug source set ever returns one; release builds always use real billing.
 */
interface BillingOverride {
    /** Owned purchases as the store would report them. */
    fun purchases(): List<PurchaseInfo>
    fun buy()
    val price: String
}
