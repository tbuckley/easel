package com.tbuckley.easel

import android.graphics.Canvas
import android.graphics.Matrix
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.ViewModel
import androidx.ink.strokes.Stroke
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer

interface CanvasElement {
    fun render(canvas: Canvas)
    fun getBounds(): Rect
    val transform: Matrix
}

class StrokeCanvasElement(
    private val stroke: Stroke,
    override val transform: Matrix = Matrix()
) : CanvasElement {
    companion object {
        private val sharedRenderer = CanvasStrokeRenderer.create()
    }

    override fun render(canvas: Canvas) {
        sharedRenderer.draw(canvas, stroke, transform)
    }

    override fun getBounds(): Rect {
        val box = stroke.shape.computeBoundingBox() ?: return Rect.Zero
        return Rect(box.xMin, box.yMin, box.xMax, box.yMax)
    }
}

class CanvasElementViewModel : ViewModel() {
    private val _elements = mutableStateListOf<CanvasElement>()
    val elements: List<CanvasElement> = _elements

    fun addStrokes(strokes: Collection<Stroke>) {
        _elements.addAll(strokes.map { StrokeCanvasElement(it) })
    }

    fun getTotalSize(): Rect {
        val boxes = _elements.map { it.getBounds() }

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
}
