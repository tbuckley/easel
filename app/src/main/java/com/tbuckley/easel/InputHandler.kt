package com.tbuckley.easel

import android.graphics.Color
import android.graphics.Matrix
import android.util.Log
import android.view.MotionEvent
import android.view.MotionEvent.*
import android.view.View
import androidx.compose.runtime.MutableState
import androidx.ink.authoring.InProgressStrokeId
import androidx.ink.authoring.InProgressStrokesView
import androidx.ink.brush.Brush
import androidx.ink.brush.StockBrushes
import androidx.input.motionprediction.MotionEventPredictor

// Define an interface for state nodes
interface InputStateNode {
    val id: String
    fun onEnter(event: MotionEvent?) {}
    fun onExit(event: MotionEvent?) {}
    fun handleMotionEvent(event: MotionEvent): String? // Returns ID of node to transition to, or null
}

// Input State Machine Handler
class InputStateMachine {
    private val nodes = mutableMapOf<String, InputStateNode>()
    private var currentNode: InputStateNode? = null

    fun registerNode(node: InputStateNode) {
        nodes[node.id] = node
    }

    fun setCurrentNode(nodeId: String, event: MotionEvent? = null) {
        Log.d("InputStateMachine", "Setting node to: $nodeId")
        currentNode?.onExit(event)
        currentNode = nodes[nodeId]
        currentNode?.onEnter(event)
    }

    fun handleMotionEvent(event: MotionEvent) {
        val transitionId = currentNode?.handleMotionEvent(event)
        transitionId?.let { newId ->
            if (nodes.containsKey(newId)) {
                setCurrentNode(newId, event)
            } else {
                // Handle error: node ID not found
            }
        }
    }
}

// Idle State Node
class IdleNode : InputStateNode {
    override val id: String = "idle"

    override fun handleMotionEvent(event: MotionEvent): String? {
        return when {
            // Only change when we have a single pointer with action down
            event.actionMasked == ACTION_DOWN && event.pointerCount == 1 -> "drawing"
            else -> null
        }
    }
}

// Drawing State Node
class DrawingNode(
    private val view: View,
    private val inProgressStrokesView: InProgressStrokesView,
    private val worldToScreenTransform: MutableState<Matrix>
) : InputStateNode {
    override val id: String = "drawing"
    private val predictor: MotionEventPredictor = MotionEventPredictor.newInstance(view)
    private var currentPointerId: Int? = null
    private var currentStrokeId: InProgressStrokeId? = null
    var brush: Brush = Brush.createWithColorIntArgb(
        family = StockBrushes.pressurePenLatest,
        colorIntArgb = Color.BLACK,
        size = 3f,
        epsilon = 0.1f
    )

    override fun onEnter(event: MotionEvent?) {
        checkNotNull(event)
        check(event.pointerCount == 1)
        check(currentPointerId == null)
        check(currentStrokeId == null)

        view.requestUnbufferedDispatch(event)
        val pointerIndex = event.actionIndex
        val pointerId = event.getPointerId(pointerIndex)
        currentPointerId = pointerId

        val motionEventToWorldTransform = Matrix()
        worldToScreenTransform.value.invert(motionEventToWorldTransform)
        val scale = getMatrixScale(motionEventToWorldTransform)
        currentStrokeId = inProgressStrokesView.startStroke(event,
            pointerId,
            brush.copy(epsilon = 0.1f * scale),
            motionEventToWorldTransform)

        predictor.record(event)

        Log.d("NoteCanvas", "onEnter: $currentStrokeId (scale=${scale})")
    }

    override fun onExit(event: MotionEvent?) {
        Log.d("NoteCanvas", "onExit: $currentStrokeId")
        currentPointerId = null
        currentStrokeId = null
    }

    override fun handleMotionEvent(event: MotionEvent): String? {
        // Transition to idle if pointer count is no longer 1
//        if(event.pointerCount != 1) {
//            return "idle"
//        }
        predictor.record(event)


        return when (event.actionMasked) {
            ACTION_POINTER_DOWN -> {
                Log.d("DrawingNode", "ACTION_POINTER_DOWN: ${event.pointerCount}")
                // TODO only when it's a touch
                val strokeId = checkNotNull(currentStrokeId)
                inProgressStrokesView.cancelStroke(strokeId, event)
                "pinch-zoom"
            }

            ACTION_MOVE -> {
                val pointerId = checkNotNull(currentPointerId)
                val strokeId = checkNotNull(currentStrokeId)
                for (pointerIndex in 0 until event.pointerCount) {
                    if (event.getPointerId(pointerIndex) != pointerId) continue
                    val predictedEvent = predictor.predict()
                    inProgressStrokesView.addToStroke(event, pointerId, strokeId, predictedEvent)
                    predictedEvent?.recycle()
                }
                null
            }

            ACTION_UP -> {
                val pointerIndex = event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)
                check(pointerId == currentPointerId)
                val strokeId = checkNotNull(currentStrokeId)
                inProgressStrokesView.finishStroke(event, pointerId, strokeId)
                Log.d("DrawingNode", "finishStroke: $currentStrokeId")
                "idle"
            }

            ACTION_CANCEL -> {
                val pointerIndex = event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)
                check(pointerId == currentPointerId)
                val strokeId = checkNotNull(currentStrokeId)
                inProgressStrokesView.cancelStroke(strokeId, event)
                Log.d("DrawingNode", "cancelStroke: $currentStrokeId")
                "idle"
            }

            else -> null
        }
    }
}

class PinchZoomNode(private val onTransform: (Matrix) -> Unit) : InputStateNode {
    override val id: String = "pinch-zoom"
    private var previousCenterX: Float = 0f
    private var previousCenterY: Float = 0f
    private var previousDistance: Float = 0f
    private val matrix = Matrix()

    override fun onEnter(event: MotionEvent?) {
        checkNotNull(event)
        check(event.pointerCount == 2)
        updatePreviousValues(event)
    }

    override fun handleMotionEvent(event: MotionEvent): String? {
        return when (event.actionMasked) {
            ACTION_MOVE -> {
                if (event.pointerCount == 2) {
                    updateTransform(event)
                    updatePreviousValues(event)
                    null
                } else {
                    "idle"
                }
            }

            ACTION_UP, ACTION_POINTER_UP, ACTION_CANCEL -> "idle"
            else -> null
        }
    }

    private fun updatePreviousValues(event: MotionEvent) {
        val (centerX, centerY) = calculateCenter(event)
        previousCenterX = centerX
        previousCenterY = centerY
        previousDistance = calculateDistance(event)
    }

    private fun updateTransform(event: MotionEvent) {
        val (centerX, centerY) = calculateCenter(event)
        val distance = calculateDistance(event)

        matrix.reset()

        // Calculate and apply translation
        val dx = centerX - previousCenterX
        val dy = centerY - previousCenterY
        matrix.postTranslate(dx, dy)

        // Calculate and apply scale
        val scale = distance / previousDistance
        matrix.postScale(scale, scale, previousCenterX, previousCenterY)

        onTransform(matrix)
    }

    private fun calculateCenter(event: MotionEvent): Pair<Float, Float> {
        val x0 = event.getX(0)
        val y0 = event.getY(0)
        val x1 = event.getX(1)
        val y1 = event.getY(1)
        return Pair((x0 + x1) / 2, (y0 + y1) / 2)
    }

    private fun calculateDistance(event: MotionEvent): Float {
        val x0 = event.getX(0)
        val y0 = event.getY(0)
        val x1 = event.getX(1)
        val y1 = event.getY(1)
        val dx = x1 - x0
        val dy = y1 - y0
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
}

fun getMatrixScale(matrix: Matrix): Float {
    val values = FloatArray(9)
    matrix.getValues(values)

    // Scale is stored in values[0] (scaleX) and values[4] (scaleY)
    // Assuming uniform scaling, return values[0] (scaleX)
    return values[Matrix.MSCALE_X]
}