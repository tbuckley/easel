package com.tbuckley.easel.data

import com.tbuckley.easel.data.local.CanvasElementDao
import kotlinx.coroutines.flow.Flow
import android.graphics.Matrix
import androidx.core.graphics.values
import androidx.ink.strokes.Stroke
import com.tbuckley.easel.Converters
import com.tbuckley.easel.data.local.CanvasElementEntity
import com.tbuckley.easel.data.local.CanvasElementType
import kotlinx.coroutines.flow.map
import android.util.Log

typealias CanvasElementID = Int
typealias NoteID = Int

sealed class CanvasElement {
    abstract val id: CanvasElementID
    abstract val noteId: NoteID
    abstract val transform: Matrix
}

data class StrokeElement(
    override val id: CanvasElementID,
    override val noteId: NoteID,
    override val transform: Matrix,
    val stroke: Stroke
) : CanvasElement()

class CanvasElementLocalDataSource(
    private val canvasElementDao: CanvasElementDao
) {
    fun getCanvasElementsForNote(noteId: Int): Flow<List<CanvasElement>> {
        return canvasElementDao.getCanvasElementsForNote(noteId)
            .map { entities -> 
                Log.d("CanvasElementDataSource", "Mapping ${entities.size} entities to CanvasElements")
                entities.mapNotNull { it.toCanvasElement() }.also { elements ->
                    Log.d("CanvasElementDataSource", "Mapped to ${elements.size} CanvasElements")
                }
            }
    }

//    suspend fun updateAll(canvasElements: List<CanvasElement>) {
//        canvasElementDao.updateAll(canvasElements)
//    }
//
    suspend fun insertAll(noteId: Int, canvasElements: List<CanvasElement>) {
        Log.d("CanvasElementDataSource", "Inserting ${canvasElements.size} CanvasElements")
        val entities = canvasElements.map { it.toCanvasElementEntity() }
        canvasElementDao.insertAll(entities)
        Log.d("CanvasElementDataSource", "Inserted ${entities.size} CanvasElementEntities")
    }

    suspend fun deleteAllForNote(noteId: Int) {
        canvasElementDao.deleteAllForNote(noteId)
    }
//
//    suspend fun delete(canvasElement: CanvasElement) {
//        canvasElementDao.delete(canvasElement)
//    }
//
//    suspend fun deleteAllForNote(noteId: String) {
//        canvasElementDao.deleteAllForNote(noteId)
//    }

    private fun CanvasElement.toCanvasElementEntity(): CanvasElementEntity {
        val converters = Converters()
        return when (this) {
            is StrokeElement -> {
                val data = converters.serializeStroke(stroke)
                CanvasElementEntity(
                    id = id,
                    noteId = noteId,
                    type = CanvasElementType.STROKE,
                    transform = transform.values().joinToString(","),
                    data = data
                )
            }
        }
        // Add other CanvasElement types here when implemented
    }

    private fun CanvasElementEntity.toCanvasElement(): CanvasElement? {
        val matrix = Matrix()
        matrix.setValues(transform.split(",").map { it.toFloat() }.toFloatArray())

        val converters = Converters()
        return when (type) {
            CanvasElementType.STROKE -> {
                val deserializedStroke = converters.deserializeStroke(data)
                if (deserializedStroke != null) {
                    StrokeElement(
                        id = id,
                        noteId = noteId,
                        transform = matrix,
                        stroke = deserializedStroke
                    )
                } else {
                    null
                }
            }
        }
    }
}
