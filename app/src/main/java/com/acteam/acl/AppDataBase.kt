package com.acteam.acl

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "app_queue")
data class AppEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val packageName: String,
    val label: String,
    val delaySeconds: Int,
    val orderIndex: Int,
    val runOnlyOnce: Boolean = false
)

@Dao
interface AppDao {
    @Query("SELECT * FROM app_queue ORDER BY orderIndex ASC")
    fun getAllApps(): Flow<List<AppEntity>>

    @Query("SELECT * FROM app_queue ORDER BY orderIndex ASC")
    suspend fun getQueueSnapshot(): List<AppEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApp(app: AppEntity)

    @Query("DELETE FROM app_queue WHERE id = :appId")
    suspend fun deleteApp(appId: Int)

    @Query("DELETE FROM app_queue")
    suspend fun clearAll()
}

@Database(entities = [AppEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao
}