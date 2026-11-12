package app.letostudio.gameemulator.billing.model

import androidx.annotation.StringRes
import app.letostudio.gameemulator.R
import com.android.billingclient.api.BillingClient.BillingResponseCode

/**
 * Typed representation of every BillingResponseCode (FR-020..FR-024, data-model.md).
 * [isRetriable] drives the retry strategy in BillingResponseHandler; [userMessageRes]
 * is the user-facing message (never a raw debug message, tech_stacks.md §10).
 */
sealed class BillingError(
    val responseCode: Int,
    val isRetriable: Boolean,
    @param:StringRes val userMessageRes: Int,
) {
    data object ServiceDisconnected : BillingError(
        BillingResponseCode.SERVICE_DISCONNECTED, true, R.string.billing_error_service,
    )

    data object ServiceUnavailable : BillingError(
        BillingResponseCode.SERVICE_UNAVAILABLE, true, R.string.billing_error_service,
    )

    data object ServiceTimeout : BillingError(
        SERVICE_TIMEOUT_CODE, true, R.string.billing_error_service,
    )

    data object BillingUnavailable : BillingError(
        BillingResponseCode.BILLING_UNAVAILABLE, false, R.string.billing_error_unavailable,
    )

    data object FeatureNotSupported : BillingError(
        BillingResponseCode.FEATURE_NOT_SUPPORTED, false, R.string.billing_error_feature_not_supported,
    )

    data class DeveloperError(val debugMessage: String) : BillingError(
        BillingResponseCode.DEVELOPER_ERROR, false, R.string.billing_error_generic,
    )

    data object ItemUnavailable : BillingError(
        BillingResponseCode.ITEM_UNAVAILABLE, false, R.string.billing_error_item_unavailable,
    )

    data object ItemAlreadyOwned : BillingError(
        BillingResponseCode.ITEM_ALREADY_OWNED, false, R.string.billing_error_already_owned,
    )

    data object ItemNotOwned : BillingError(
        BillingResponseCode.ITEM_NOT_OWNED, false, R.string.billing_error_generic,
    )

    data object NetworkError : BillingError(
        BillingResponseCode.NETWORK_ERROR, true, R.string.billing_error_network,
    )

    data class GenericError(val debugMessage: String) : BillingError(
        BillingResponseCode.ERROR, true, R.string.billing_error_generic,
    )

    data class Unknown(val code: Int, val debugMessage: String) : BillingError(
        code, false, R.string.billing_error_generic,
    )

    companion object {
        // BillingResponseCode.SERVICE_TIMEOUT (-3) was deprecated/removed from the
        // public constants in PBL 8 but the service can still return the raw code.
        const val SERVICE_TIMEOUT_CODE = -3
    }
}

/** Carries a [BillingError] through the repository's Result-based API (tech_stacks.md §10). */
class BillingException(val error: BillingError) : Exception("Billing error ${error.responseCode}")
