package app.letostudio.gameemulator.billing.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Local record of every Google Play transaction (FR-046/FR-047). Google Play remains
 * the source of truth — rows are reconciled against queryPurchasesAsync on each launch.
 */
@Entity(
    tableName = "purchases",
    indices = [Index("product_ids"), Index("purchase_state")],
)
data class PurchaseEntity(
    @PrimaryKey
    @ColumnInfo(name = "purchase_token")
    val purchaseToken: String,
    @ColumnInfo(name = "order_id")
    val orderId: String?,
    /** Comma-separated product IDs (a purchase can carry several products). */
    @ColumnInfo(name = "product_ids")
    val productIds: String,
    /** Purchase.PurchaseState: 0 = UNSPECIFIED, 1 = PURCHASED, 2 = PENDING. */
    @ColumnInfo(name = "purchase_state")
    val purchaseState: Int,
    @ColumnInfo(name = "is_acknowledged", defaultValue = "0")
    val isAcknowledged: Boolean,
    @ColumnInfo(name = "is_consumed", defaultValue = "0")
    val isConsumed: Boolean,
    @ColumnInfo(name = "purchase_time")
    val purchaseTime: Long,
    @ColumnInfo(name = "quantity", defaultValue = "1")
    val quantity: Int,
    @ColumnInfo(name = "obfuscated_account_id")
    val obfuscatedAccountId: String?,
    @ColumnInfo(name = "is_auto_renewing", defaultValue = "0")
    val isAutoRenewing: Boolean,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
