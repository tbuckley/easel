package com.tbuckley.easel.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.tbuckley.easel.data.local.CanvasElementDao
import com.tbuckley.easel.data.local.CanvasElementEntity
import com.tbuckley.easel.data.local.NoteEntity

@Database(entities = [CanvasElementEntity::class, NoteEntity::class], version = 1)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun canvasElementDao(): CanvasElementDao
    abstract fun noteDao(): NoteDao
}
