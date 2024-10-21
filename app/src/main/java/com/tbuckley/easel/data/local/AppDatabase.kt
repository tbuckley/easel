package com.tbuckley.easel.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.tbuckley.easel.data.local.CanvasElementDao
import com.tbuckley.easel.data.local.CanvasElementEntity

@Database(entities = [CanvasElementEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun canvasElementDao(): CanvasElementDao
}
