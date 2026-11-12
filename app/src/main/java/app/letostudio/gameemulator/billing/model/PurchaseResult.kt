package app.letostudio.gameemulator.billing.model

import com.android.billingclient.api.Purchase

/** Outcome of a purchase flow, emitted to the UI after the 3-step pipeline (FR-010). */
sealed interface PurchaseResult {
    /** Payment completed; entitlement granted and acknowledged/consumed. */
    data class Success(val purchase: Purchase) : PurchaseResult

    /** Payment pending (slow payment method) — no entitlement yet (FR-013). */
    data class Pending(val purchase: Purchase) : PurchaseResult

    /** User dismissed the Google Play purchase dialog — silent, no error UI. */
    data object Cancelled : PurchaseResult

    /** Product already owned; ownership re-synced from Google Play (FR-023). */
    data object AlreadyOwned : PurchaseResult

    /** Purchase failed with a non-network error. */
    data class Error(val error: BillingError) : PurchaseResult

    /** Purchase failed on a retriable network-class error. */
    data class NetworkError(val error: BillingError) : PurchaseResult
}
