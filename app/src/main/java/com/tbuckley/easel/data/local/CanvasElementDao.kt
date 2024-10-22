package com.tbuckley.easel.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

enum class CanvasElementType {
    STROKE
}

@Entity(tableName = "canvas_elements")
data class CanvasElementEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val noteId: Int,
    val type: CanvasElementType,
    val transform: String, // JSON version of Matrix
    val data: String // Type-specific data
)

class CanvasElementTypeConverter {
    @TypeConverter
    fun toCanvasElementType(value: String) = enumValueOf<CanvasElementType>(value)

    @TypeConverter
    fun fromCanvasElementType(value: CanvasElementType) = value.name
}

@TypeConverters(CanvasElementTypeConverter::class)
@Dao
interface CanvasElementDao {
    @Query("SELECT * FROM canvas_elements WHERE noteId = :noteId")
    fun getCanvasElementsForNote(noteId: Int): Flow<List<CanvasElementEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(canvasElements: List<CanvasElementEntity>)

    @Update
    suspend fun update(canvasElement: CanvasElementEntity)

    @Delete
    suspend fun delete(canvasElement: CanvasElementEntity)

    @Transaction
    suspend fun updateAll(canvasElements: List<CanvasElementEntity>) {
        for (canvasElement in canvasElements) {
            update(canvasElement)
        }
    }

    @Query("DELETE FROM canvas_elements WHERE noteId = :noteId")
    suspend fun deleteAllForNote(noteId: Int)
}
