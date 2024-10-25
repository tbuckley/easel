package com.tbuckley.easel.data

import kotlinx.coroutines.flow.Flow

class CanvasElementRepository(
    private val localDataSource: CanvasElementLocalDataSource
) {
    fun getCanvasElementsForNote(noteId: Int): List<CanvasElement> {
        return localDataSource.getCanvasElementsForNote(noteId)
    }

    suspend fun insert(noteId: Int, canvasElement: CanvasElement): Int {
        return localDataSource.insert(noteId, canvasElement)
    }

    suspend fun insertAll(noteId: Int, canvasElements: List<CanvasElement>) {
        localDataSource.insertAll(noteId, canvasElements)
    }

    suspend fun deleteAllForNote(noteId: Int) {
        localDataSource.deleteAllForNote(noteId)
    }

    // Uncomment and implement these methods when needed
    /*
    suspend fun updateAll(canvasElements: List<CanvasElement>) {
        // Implement when CanvasElementLocalDataSource.updateAll is implemented
    }

    suspend fun delete(canvasElement: CanvasElement) {
        // Implement when CanvasElementLocalDataSource.delete is implemented
    }

    suspend fun deleteAllForNote(noteId: String) {
        // Implement when CanvasElementLocalDataSource.deleteAllForNote is implemented
    }
    */
}
