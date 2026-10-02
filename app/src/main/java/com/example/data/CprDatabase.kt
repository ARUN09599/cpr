package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "cpr_sessions")
data class CprSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val startTimeMs: Long = System.currentTimeMillis(),
    val durationSec: Int,
    val connectionMode: String,
    val totalCompressions: Int,
    val avgDepthCm: Float,
    val avgRateCpm: Int,
    val recoilCompliancePct: Int,
    val handBalanceScorePct: Int,
    val avgFsr1N: Float,
    val avgFsr2N: Float,
    val avgFsr3N: Float,
    val avgTiltDeg: Float,
    val ahaOverallScore: Int,
    val notes: String
)

@Dao
interface CprSessionDao {
    @Query("SELECT * FROM cpr_sessions ORDER BY startTimeMs DESC")
    fun getAllSessions(): Flow<List<CprSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: CprSessionEntity)

    @Query("DELETE FROM cpr_sessions WHERE id = :id")
    suspend fun deleteSessionById(id: Int)

    @Query("DELETE FROM cpr_sessions")
    suspend fun clearAllSessions()
}

@Database(entities = [CprSessionEntity::class], version = 1, exportSchema = false)
abstract class CprDatabase : RoomDatabase() {
    abstract fun cprSessionDao(): CprSessionDao

    companion object {
        @Volatile
        private var INSTANCE: CprDatabase? = null

        fun getInstance(context: Context): CprDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CprDatabase::class.java,
                    "smart_cpr_glove_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class CprSessionRepository(private val dao: CprSessionDao) {
    val allSessions: Flow<List<CprSessionEntity>> = dao.getAllSessions()

    suspend fun saveSession(session: CprSessionEntity) {
        dao.insertSession(session)
    }

    suspend fun deleteSession(id: Int) {
        dao.deleteSessionById(id)
    }

    suspend fun clearAll() {
        dao.clearAllSessions()
    }
}
