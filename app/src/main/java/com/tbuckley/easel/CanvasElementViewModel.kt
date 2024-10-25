package com.tbuckley.easel

import android.graphics.Canvas
import android.graphics.Matrix
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
import com.tbuckley.easel.data.local.NoteEntity
import com.tbuckley.easel.data.local.NoteDao
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job

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
    }
}

class CanvasElementViewModel(
    private val repository: CanvasElementRepository,
    private val noteDao: NoteDao
) : ViewModel() {
    private val _notes = MutableStateFlow<List<NoteEntity>>(emptyList())
    val notes: StateFlow<List<NoteEntity>> = _notes.asStateFlow()

    private val _elements = MutableStateFlow<List<CanvasElement>>(emptyList())
    val elements: StateFlow<List<CanvasElement>> = _elements.asStateFlow()

    private val _currentNoteId = MutableStateFlow(-1)
    val currentNoteId: StateFlow<Int> = _currentNoteId.asStateFlow()

    private var elementsJob: Job? = null

    init {
        viewModelScope.launch {
            noteDao.getAllNotes().collect { noteList ->
                _notes.value = noteList
            }
        }
    }

    fun loadElementsForNote(noteId: Int) {
        _currentNoteId.value = noteId
        elementsJob?.cancel()
        elementsJob = viewModelScope.launch {
            repository.getCanvasElementsForNote(noteId).collect { canvasElements ->
                _elements.value = canvasElements
            }
        }
    }

    fun addStrokes(strokes: Collection<Stroke>) {
        val newElements = strokes.map { stroke ->
            StrokeElement(
                id = 0, // Use 0 for new elements, database will assign actual ID
                noteId = _currentNoteId.value,
                stroke = stroke,
                transform = Matrix()
            )
        }
        viewModelScope.launch {
            Log.d("CanvasElementViewModel", "Calling insertAll with ${newElements.size} new elements")
            repository.insertAll(_currentNoteId.value, newElements)
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
        if (_currentNoteId.value != -1) {
            viewModelScope.launch {
                Log.d("CanvasElementViewModel", "Deleting all elements for note ${currentNoteId.value}")
                repository.deleteAllForNote(currentNoteId.value)
                // The Flow will automatically update the UI
            }
        } else {
            Log.w("CanvasElementViewModel", "Attempted to delete all elements without a valid noteId")
        }
    }

    fun createNewNote() {
        viewModelScope.launch {
            val newNoteId = noteDao.insertNote(NoteEntity())
            loadElementsForNote(newNoteId.toInt())
        }
    }

    fun deleteNote(note: NoteEntity) {
        viewModelScope.launch {
            noteDao.deleteNote(note)
            repository.deleteAllForNote(note.id)
            if (_notes.value.isNotEmpty()) {
                loadElementsForNote(_notes.value.first().id)
            } else {
                _elements.value = emptyList()
                _currentNoteId.value = -1
            }
        }
    }
}
