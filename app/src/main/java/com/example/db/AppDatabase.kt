package com.example.db

import android.content.Context
import androidx.room.*
import com.example.model.SavedMedia
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedMediaDao {
    @Query("SELECT * FROM saved_media ORDER BY timestamp DESC")
    fun getAllSavedMedia(): Flow<List<SavedMedia>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedia(media: SavedMedia)

    @Query("DELETE FROM saved_media WHERE title = :title AND type = :type")
    suspend fun deleteByTitleAndType(title: String, type: String)

    @Query("SELECT EXISTS(SELECT 1 FROM saved_media WHERE title = :title AND type = :type LIMIT 1)")
    suspend fun isBookmarked(title: String, type: String): Boolean

    @Delete
    suspend fun deleteMedia(media: SavedMedia)
}

@Database(entities = [SavedMedia::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun savedMediaDao(): SavedMediaDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "media_seeker_database"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class MediaRepository(private val savedMediaDao: SavedMediaDao) {
    val allBookmarks: Flow<List<SavedMedia>> = savedMediaDao.getAllSavedMedia()

    suspend fun insert(media: SavedMedia) = savedMediaDao.insertMedia(media)

    suspend fun delete(media: SavedMedia) = savedMediaDao.deleteMedia(media)

    suspend fun deleteByTitleAndType(title: String, type: String) = 
        savedMediaDao.deleteByTitleAndType(title, type)

    suspend fun isBookmarked(title: String, type: String): Boolean = 
        savedMediaDao.isBookmarked(title, type)
}
