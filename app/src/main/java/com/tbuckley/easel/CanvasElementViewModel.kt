package com.tbuckley.easel

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.ViewModel
import androidx.ink.strokes.Stroke

class CanvasElementViewModel : ViewModel() {
    private val _elements = mutableStateListOf<Stroke>()
    val elements: List<Stroke> = _elements

    fun addStrokes(strokes: Collection<Stroke>) {
        _elements.addAll(strokes)
    }

    fun getTotalSize(): Rect {
        val boxes = _elements.mapNotNull { it.shape.computeBoundingBox() }

        if (boxes.isEmpty()) {
            return Rect.Zero
        }

        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY

        boxes.forEach { box ->
            minX = minOf(minX, box.xMin)
            minY = minOf(minY, box.yMin)
            maxX = maxOf(maxX, box.xMax)
            maxY = maxOf(maxY, box.yMax)
        }

        return Rect(minX, minY, maxX, maxY)
    }
}
