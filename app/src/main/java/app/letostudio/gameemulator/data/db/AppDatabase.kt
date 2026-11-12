package app.letostudio.gameemulator.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.Upsert
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** One game file in the library (F02–F06). Per-game emulator overrides live here too. */
@Entity(tableName = "games", indices = [Index(value = ["uri"], unique = true)])
data class GameEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** SAF document URI (tree child or single OpenDocument). */
    val uri: String,
    /** Display file name (UI only, never analytics). */
    val fileName: String,
    /** iso | cso | pbp */
    val format: String,
    val sizeBytes: Long,
    /** Title from PARAM.SFO, else file name without extension. */
    val title: String,
    /** User rename (⋯ › Rename); wins over [title]. */
    val customTitle: String? = null,
    val discId: String? = null,
    /** analytics game_hash — see GameHash. */
    val gameHash: String,
    /** Cached ICON0.PNG on disk. */
    val coverPath: String? = null,
    val isHomebrew: Boolean = false,
    val favorite: Boolean = false,
    /** Tree URI it was found under; null for a single added file. */
    val sourceTree: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val lastPlayedAt: Long? = null,
    val playSeconds: Long = 0,
    /** Per-game overrides (null = global default). */
    val resolution: Int? = null,
    val frameSkip: Int? = null,
    val effectsOn: Boolean? = null,
    val fixLagSteps: Int? = null,
    /** great | tweaks | poor — Smart setup verdict for this game on this phone. */
    val compat: String? = null,
    /** R1: one extra save slot until this time (epoch ms). */
    val extraSlotUntil: Long = 0,
    /** False when the last scan could not find the file any more. */
    val available: Boolean = true,
) {
    val displayTitle: String get() = customTitle?.takeIf { it.isNotBlank() } ?: title
}

/** Save state slot. index 0 = auto-save slot. */
@Entity(tableName = "save_slots", indices = [Index(value = ["gameId", "slotIndex"], unique = true)])
data class SaveSlotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val gameId: Long,
    val slotIndex: Int,
    val statePath: String,
    val thumbPath: String?,
    val savedAt: Long,
    val note: String? = null,
) {
    val isAuto: Boolean get() = slotIndex == AUTO_SLOT

    companion object {
        const val AUTO_SLOT = 0
    }
}

@Dao
interface GameDao {
    @Query("SELECT * FROM games WHERE available = 1 ORDER BY title COLLATE NOCASE")
    fun observeAll(): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE id = :id")
    fun observe(id: Long): Flow<GameEntity?>

    @Query("SELECT * FROM games WHERE id = :id")
    suspend fun get(id: Long): GameEntity?

    @Query("SELECT * FROM games WHERE uri = :uri")
    suspend fun byUri(uri: String): GameEntity?

    @Query("SELECT * FROM games")
    suspend fun all(): List<GameEntity>

    @Query("SELECT COUNT(*) FROM games WHERE available = 1")
    fun observeCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(game: GameEntity): Long

    @Update
    suspend fun update(game: GameEntity)

    @Query("UPDATE games SET favorite = :on WHERE id = :id")
    suspend fun setFavorite(id: Long, on: Boolean)

    @Query("UPDATE games SET customTitle = :title WHERE id = :id")
    suspend fun rename(id: Long, title: String?)

    @Query("UPDATE games SET lastPlayedAt = :at, playSeconds = playSeconds + :seconds WHERE id = :id")
    suspend fun addPlay(id: Long, at: Long, seconds: Long)

    @Query("UPDATE games SET available = :available WHERE id = :id")
    suspend fun setAvailable(id: Long, available: Boolean)

    @Query("DELETE FROM games WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface SaveSlotDao {
    @Query("SELECT * FROM save_slots WHERE gameId = :gameId ORDER BY slotIndex")
    fun observeForGame(gameId: Long): Flow<List<SaveSlotEntity>>

    @Query("SELECT * FROM save_slots WHERE gameId = :gameId ORDER BY slotIndex")
    suspend fun forGame(gameId: Long): List<SaveSlotEntity>

    @Query("SELECT * FROM save_slots WHERE gameId = :gameId AND slotIndex = :slot")
    suspend fun get(gameId: Long, slot: Int): SaveSlotEntity?

    @Query("SELECT COUNT(*) FROM save_slots")
    fun observeCount(): Flow<Int>

    @Upsert
    suspend fun upsert(slot: SaveSlotEntity)

    @Query("DELETE FROM save_slots WHERE gameId = :gameId")
    suspend fun deleteForGame(gameId: Long)
}

@Database(entities = [GameEntity::class, SaveSlotEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun games(): GameDao
    abstract fun saveSlots(): SaveSlotDao
}

@Module
@InstallIn(SingletonComponent::class)
object AppDatabaseModule {
    @Provides
    @Singleton
    fun provideDb(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "retronova.db").build()

    @Provides fun gameDao(db: AppDatabase): GameDao = db.games()
    @Provides fun saveSlotDao(db: AppDatabase): SaveSlotDao = db.saveSlots()
}
