package app.letostudio.gameemulator.billing.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PurchaseDao {

    @Upsert
    suspend fun upsert(purchase: PurchaseEntity)

    @Query("SELECT * FROM purchases WHERE purchase_token = :token")
    suspend fun getByToken(token: String): PurchaseEntity?

    @Query("SELECT * FROM purchases")
    suspend fun getAll(): List<PurchaseEntity>

    @Query("SELECT * FROM purchases")
    fun observeAll(): Flow<List<PurchaseEntity>>

    /** PURCHASED (state 1) but never acknowledged — crash-recovery targets (FR-015). */
    @Query("SELECT * FROM purchases WHERE purchase_state = 1 AND is_acknowledged = 0 AND is_consumed = 0")
    suspend fun getUnacknowledged(): List<PurchaseEntity>

    @Query("UPDATE purchases SET is_acknowledged = 1, updated_at = :now WHERE purchase_token = :token")
    suspend fun markAcknowledged(token: String, now: Long)

    @Query("UPDATE purchases SET is_consumed = 1, is_acknowledged = 1, updated_at = :now WHERE purchase_token = :token")
    suspend fun markConsumed(token: String, now: Long)

    @Query("DELETE FROM purchases WHERE purchase_token = :token")
    suspend fun deleteByToken(token: String)
}
