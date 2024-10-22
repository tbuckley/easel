package com.tbuckley.easel

import android.graphics.Canvas
import android.graphics.Matrix
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.ViewModel
import androidx.ink.strokes.Stroke
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.lifecycle.viewModelScope
import com.tbuckley.easel.data.CanvasElementRepository
import com.tbuckley.easel.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import android.util.Log

private val sharedRenderer = CanvasStrokeRenderer.create()

fun CanvasElement.render(canvas: Canvas) {
    when (this) {
        is StrokeElement -> sharedRenderer.draw(canvas, stroke, canvas.getMatrix())
        // Add cases for other CanvasElement types when implemented
    }
}

fun CanvasElement.getBounds(): Rect {
    return when (this) {
        is StrokeElement -> {
            val box = stroke.shape.computeBoundingBox() ?: return Rect.Zero
            Rect(box.xMin, box.yMin, box.xMax, box.yMax)
        }
        // Add cases for other CanvasElement types when implemented
        else -> Rect.Zero
    }
}

class CanvasElementViewModel(
    private val repository: CanvasElementRepository
) : ViewModel() {
    private val _elements = MutableStateFlow<List<CanvasElement>>(emptyList())
    val elements: StateFlow<List<CanvasElement>> = _elements

    private var currentNoteId: Int = -1

    fun loadElementsForNote(noteId: Int) {
        currentNoteId = noteId
        viewModelScope.launch {
            repository.getCanvasElementsForNote(noteId).collect { canvasElements ->
                Log.d("CanvasElementViewModel", "getCanvasElementsForNote updated: ${canvasElements.size} elements")
                _elements.value = canvasElements
            }
        }
    }

    fun addStrokes(strokes: Collection<Stroke>) {
        val newElements = strokes.map { stroke ->
            StrokeElement(
                id = 0, // Use 0 for new elements, database will assign actual ID
                noteId = currentNoteId,
                stroke = stroke,
                transform = Matrix()
            )
        }
        viewModelScope.launch {
            Log.d("CanvasElementViewModel", "Calling insertAll with ${newElements.size} new elements")
            repository.insertAll(currentNoteId, newElements)
        }
    }

    fun getTotalSize(): Rect {
        val boxes = _elements.value.map { it.getBounds() }

        if (boxes.isEmpty()) {
            return Rect.Zero
        }

        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY

        boxes.forEach { box ->
            minX = minOf(minX, box.left)
            minY = minOf(minY, box.top)
            maxX = maxOf(maxX, box.right)
            maxY = maxOf(maxY, box.bottom)
        }

        return Rect(minX, minY, maxX, maxY)
    }

    fun deleteAllForCurrentNote() {
        if (currentNoteId != -1) {
            viewModelScope.launch {
                Log.d("CanvasElementViewModel", "Deleting all elements for note $currentNoteId")
                repository.deleteAllForNote(currentNoteId)
                // The Flow will automatically update the UI
            }
        } else {
            Log.w("CanvasElementViewModel", "Attempted to delete all elements without a valid noteId")
        }
    }
}
