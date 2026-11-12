package app.letostudio.gameemulator.billing.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface EntitlementDao {

    @Upsert
    suspend fun upsert(entitlement: EntitlementEntity)

    @Query("SELECT * FROM entitlements WHERE product_id = :productId")
    suspend fun getByProductId(productId: String): EntitlementEntity?

    @Query("SELECT * FROM entitlements")
    suspend fun getAll(): List<EntitlementEntity>

    @Query("SELECT * FROM entitlements")
    fun observeAll(): Flow<List<EntitlementEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM entitlements WHERE entitlement_type = :type)")
    suspend fun hasAnyOfType(type: String): Boolean

    @Query(
        "UPDATE entitlements SET is_granted = 0, subscription_state = :state, updated_at = :now " +
            "WHERE product_id = :productId",
    )
    suspend fun revoke(productId: String, state: String?, now: Long)

    @Query("DELETE FROM entitlements WHERE product_id = :productId")
    suspend fun delete(productId: String)
}
