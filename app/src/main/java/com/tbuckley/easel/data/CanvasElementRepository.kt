package com.tbuckley.easel.data

import kotlinx.coroutines.flow.Flow

class CanvasElementRepository(
    private val localDataSource: CanvasElementLocalDataSource
) {
    fun getCanvasElementsForNote(noteId: Int): Flow<List<CanvasElement>> {
        return localDataSource.getCanvasElementsForNote(noteId)
    }

    suspend fun insertAll(noteId: Int, canvasElements: List<CanvasElement>) {
        localDataSource.insertAll(noteId, canvasElements)
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
