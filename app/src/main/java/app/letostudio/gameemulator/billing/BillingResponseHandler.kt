package app.letostudio.gameemulator.billing

import app.letostudio.gameemulator.billing.model.BillingError
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay

/**
 * Maps every BillingResponseCode to a typed [BillingError] (FR-020..FR-022) and runs
 * billing calls with the retry strategy from research.md: exponential backoff
 * (1s → 2s → 4s, max 4 attempts) for background work, a single retry for
 * user-initiated actions.
 */
@Singleton
class BillingResponseHandler @Inject constructor() {

    /** A billing call outcome: the BillingResult plus an optional payload. */
    data class BillingCall<T>(val result: BillingResult, val value: T?) {
        val isOk: Boolean get() = result.responseCode == BillingResponseCode.OK
    }

    fun toError(result: BillingResult): BillingError = toError(result.responseCode, result.debugMessage)

    fun toError(responseCode: Int, debugMessage: String = ""): BillingError = when (responseCode) {
        BillingResponseCode.SERVICE_DISCONNECTED -> BillingError.ServiceDisconnected
        BillingResponseCode.SERVICE_UNAVAILABLE -> BillingError.ServiceUnavailable
        BillingError.SERVICE_TIMEOUT_CODE -> BillingError.ServiceTimeout
        BillingResponseCode.BILLING_UNAVAILABLE -> BillingError.BillingUnavailable
        BillingResponseCode.FEATURE_NOT_SUPPORTED -> BillingError.FeatureNotSupported
        BillingResponseCode.DEVELOPER_ERROR -> BillingError.DeveloperError(debugMessage)
        BillingResponseCode.ITEM_UNAVAILABLE -> BillingError.ItemUnavailable
        BillingResponseCode.ITEM_ALREADY_OWNED -> BillingError.ItemAlreadyOwned
        BillingResponseCode.ITEM_NOT_OWNED -> BillingError.ItemNotOwned
        BillingResponseCode.NETWORK_ERROR -> BillingError.NetworkError
        BillingResponseCode.ERROR -> BillingError.GenericError(debugMessage)
        else -> BillingError.Unknown(responseCode, debugMessage)
    }

    /**
     * Runs [operation] until it succeeds, hits a non-retriable error, or exhausts the
     * attempt budget. USER_CANCELED is never retried. Returns the last outcome.
     */
    suspend fun <T> withRetry(
        userInitiated: Boolean = false,
        operation: suspend () -> BillingCall<T>,
    ): BillingCall<T> {
        val maxAttempts = if (userInitiated) MAX_ATTEMPTS_USER else MAX_ATTEMPTS_BACKGROUND
        var attempt = 0
        while (true) {
            val outcome = operation()
            attempt++
            val code = outcome.result.responseCode
            if (code == BillingResponseCode.OK || code == BillingResponseCode.USER_CANCELED) return outcome
            if (!toError(outcome.result).isRetriable || attempt >= maxAttempts) return outcome
            delay(BACKOFF_DELAYS_MS[minOf(attempt - 1, BACKOFF_DELAYS_MS.lastIndex)])
        }
    }

    companion object {
        const val MAX_ATTEMPTS_BACKGROUND = 4
        const val MAX_ATTEMPTS_USER = 2
        val BACKOFF_DELAYS_MS = longArrayOf(1_000L, 2_000L, 4_000L)
    }
}
