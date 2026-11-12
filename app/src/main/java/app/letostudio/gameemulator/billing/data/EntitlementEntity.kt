package app.letostudio.gameemulator.billing.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The user's access right to one product, derived from purchases and reconciled with
 * Google Play on every launch (FR-046, FR-049). Premium status is computed from the
 * set of granted rows — cached locally for the instant cold-start paywall decision.
 */
@Entity(
    tableName = "entitlements",
    indices = [Index("entitlement_type"), Index("is_granted")],
)
data class EntitlementEntity(
    @PrimaryKey
    @ColumnInfo(name = "product_id")
    val productId: String,
    /** One of [TYPE_SUBSCRIPTION], [TYPE_NON_CONSUMABLE], [TYPE_CONSUMABLE]. */
    @ColumnInfo(name = "entitlement_type")
    val entitlementType: String,
    @ColumnInfo(name = "is_granted", defaultValue = "0")
    val isGranted: Boolean,
    @ColumnInfo(name = "purchase_token")
    val purchaseToken: String?,
    @ColumnInfo(name = "granted_at")
    val grantedAt: Long?,
    /** Subscription expiry; null for lifetime/non-subscription entitlements. */
    @ColumnInfo(name = "expires_at")
    val expiresAt: Long?,
    /** Subscription lifecycle state (see STATE_*); null for non-subscriptions. */
    @ColumnInfo(name = "subscription_state")
    val subscriptionState: String?,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
) {
    companion object {
        const val TYPE_SUBSCRIPTION = "SUBSCRIPTION"
        const val TYPE_NON_CONSUMABLE = "NON_CONSUMABLE"
        const val TYPE_CONSUMABLE = "CONSUMABLE"

        const val STATE_ACTIVE = "ACTIVE"
        const val STATE_CANCELLED = "CANCELLED"
        const val STATE_GRACE_PERIOD = "GRACE_PERIOD"
        const val STATE_ON_HOLD = "ON_HOLD"
        const val STATE_PAUSED = "PAUSED"
        const val STATE_EXPIRED = "EXPIRED"
        const val STATE_REVOKED = "REVOKED"
    }
}
