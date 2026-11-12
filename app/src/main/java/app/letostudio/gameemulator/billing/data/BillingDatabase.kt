package app.letostudio.gameemulator.billing.data

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Billing persistence, deliberately separate from the app database (FR-028): the billing
 * module stays self-contained and copy-paste reusable, and IPTV schema migrations
 * never touch purchase records (FR-050).
 */
@Database(
    entities = [PurchaseEntity::class, EntitlementEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class BillingDatabase : RoomDatabase() {
    abstract fun purchaseDao(): PurchaseDao
    abstract fun entitlementDao(): EntitlementDao
}
