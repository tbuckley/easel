package com.tbuckley.easel

import android.graphics.Color
import android.util.Log
import android.view.MotionEvent
import android.view.MotionEvent.*
import android.view.View
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
        return when (event.actionMasked) {
            ACTION_DOWN -> "drawing"
            else -> null // Stay in current state
        }
    }
}

// Drawing State Node
class DrawingNode(private val view: View, private val inProgressStrokesView: InProgressStrokesView) : InputStateNode {
    override val id: String = "drawing"
    private val predictor: MotionEventPredictor = MotionEventPredictor.newInstance(view);
    private var currentPointerId: Int? = null
    private var currentStrokeId: InProgressStrokeId? = null
    var brush: Brush = Brush.createWithColorIntArgb(
        family = StockBrushes.pressurePenLatest,
        colorIntArgb = Color.BLACK,
        size = 5f,
        epsilon = 0.1f
    )

    override fun onEnter(event: MotionEvent?) {
        checkNotNull(event)
        check(event.pointerCount == 1)

        view.requestUnbufferedDispatch(event)
        val pointerIndex = event.actionIndex
        val pointerId = event.getPointerId(pointerIndex)
        currentPointerId = pointerId
        currentStrokeId = inProgressStrokesView.startStroke(event, pointerId, brush)
        Log.d("NoteCanvas", "startStroke: $currentStrokeId")
    }

    override fun handleMotionEvent(event: MotionEvent): String? {
        // Transition to idle if pointer count is no longer 1
        if(event.pointerCount != 1) {
            return "idle"
        }

        predictor.record(event)

        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // TODO handle an additional pointer down?
                null
            }

            MotionEvent.ACTION_MOVE -> {
                val pointerId = checkNotNull(currentPointerId)
                val strokeId = checkNotNull(currentStrokeId)
                for (pointerIndex in 0 until event.pointerCount) {
                    if (event.getPointerId(pointerIndex) != pointerId) continue
                    val predictedEvent = predictor.predict()
                    inProgressStrokesView.addToStroke(event, pointerId, strokeId)
                    Log.d("NoteCanvas", "addToStroke: $currentStrokeId")
                    predictedEvent?.recycle()
                }
                null
            }

            MotionEvent.ACTION_UP -> {
                val pointerIndex = event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)
                check(pointerId == currentPointerId)
                val strokeId = checkNotNull(currentStrokeId)
                inProgressStrokesView.finishStroke(event, pointerId, strokeId)
                Log.d("NoteCanvas", "finishStroke: $currentStrokeId")
                currentPointerId = null
                currentStrokeId = null
                "idle"
            }

            MotionEvent.ACTION_CANCEL -> {
                val pointerIndex = event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)
                check(pointerId == currentPointerId)
                val strokeId = checkNotNull(currentStrokeId)
                inProgressStrokesView.cancelStroke(strokeId, event)
                Log.d("NoteCanvas", "cancelStroke: $currentStrokeId")
                currentPointerId = null
                currentStrokeId = null
                "idle"
            }

            else -> null
        }
    }
}
