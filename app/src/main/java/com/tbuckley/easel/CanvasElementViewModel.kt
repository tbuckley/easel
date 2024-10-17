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
        if (_elements.isEmpty()) {
            return Rect.Zero
        }

        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY

        _elements.forEach { stroke ->
            val bounds = stroke.shape.computeBoundingBox()
            if(bounds != null) {
                minX = minOf(minX, bounds.xMin)
                minY = minOf(minY, bounds.yMin)
                maxX = maxOf(maxX, bounds.xMax)
                maxY = maxOf(maxY, bounds.yMax)
            }
        }

        return Rect(minX, minY, maxX, maxY)
    }
}
