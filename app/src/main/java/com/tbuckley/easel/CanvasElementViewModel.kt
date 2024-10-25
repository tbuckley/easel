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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

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

    private val _elements = MutableStateFlow<Map<Int, CanvasElement>>(emptyMap())
    val elements: StateFlow<Map<Int, CanvasElement>> = _elements.asStateFlow()

    private val _currentNoteId = MutableStateFlow(-1)
    val currentNoteId: StateFlow<Int> = _currentNoteId.asStateFlow()

    private var elementsJob: Job? = null

    init {
        viewModelScope.launch {
            noteDao.getAllNotes().collect { noteList ->
                _notes.value = noteList
            }
        }

        viewModelScope.launch {
            elements.collect {
                Log.d("CanvasElementViewModel", "Elements updated: ${it.size}")
            }
        }
    }

    fun loadElementsForNote(noteId: Int) {
        _currentNoteId.value = noteId
//        elementsJob?.cancel()
//        elementsJob = viewModelScope.launch {
            _elements.value = emptyMap() // Clear the current elements
            val newElements = repository.getCanvasElementsForNote(noteId)
            _elements.value = newElements.associateBy { it.id }
//        }
    }

    fun addStrokes(strokes: Collection<Stroke>) {
        Log.d("CanvasElementViewModel", "Adding ${strokes.size} new elements")
        val baseId = _elements.value.size
        val newElements = strokes.mapIndexed { index, stroke ->
            val newId = baseId + index
            val newElement = StrokeElement(
                id = newId,
                noteId = _currentNoteId.value,
                stroke = stroke,
                transform = Matrix()
            )
            newId to newElement
        }.toMap()

        _elements.value += newElements
        Log.d("CanvasElementViewModel", "Added ${newElements.size} new elements")
    }

//    fun deleteElement(elementId: Int) {
//        viewModelScope.launch {
//            repository.deleteById(elementId)
//            _elements.update { it - elementId }
//        }
//    }

    fun getTotalSize(): Rect {
        val boxes = _elements.value.map { it.value.getBounds() }

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
                _elements.value = emptyMap()
                _currentNoteId.value = -1
            }
        }
    }
}
