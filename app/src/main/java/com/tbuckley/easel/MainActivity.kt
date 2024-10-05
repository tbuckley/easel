package com.tbuckley.easel

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.UiThread
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material3.Switch
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.ink.authoring.InProgressStrokeId
import androidx.ink.authoring.InProgressStrokesFinishedListener
import androidx.ink.authoring.InProgressStrokesView
import androidx.ink.brush.Brush
import androidx.ink.brush.StockBrushes
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.Stroke
import androidx.input.motionprediction.MotionEventPredictor
import com.tbuckley.easel.ui.theme.EaselTheme

class MainActivity : ComponentActivity(), InProgressStrokesFinishedListener {
    private lateinit var inProgressStrokesView: InProgressStrokesView
    private val finalStrokes = mutableListOf<Stroke>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        inProgressStrokesView = InProgressStrokesView(this)
        inProgressStrokesView.addFinishedStrokesListener(this)

        setContent {
            EaselTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NoteCanvas(
                        modifier = Modifier.padding(innerPadding),
                        inProgressStrokesView = inProgressStrokesView,
                        strokes = finalStrokes,
                    )
                }
            }
        }
    }

    @UiThread
    override fun onStrokesFinished(strokes: Map<InProgressStrokeId, Stroke>) {
        finalStrokes += strokes.values.map { it.copy(it.brush.copyWithColorIntArgb(Color.RED)) }
        inProgressStrokesView.removeFinishedStrokes(strokes.keys)
    }
}

@SuppressLint("ClickableViewAccessibility")
@Composable
fun NoteCanvas(
    modifier: Modifier,
    inProgressStrokesView: InProgressStrokesView,
    strokes: List<Stroke>,
) {
    val currentPointerId = remember { mutableStateOf<Int?>(null) }
    val currentStrokeId = remember { mutableStateOf<InProgressStrokeId?>(null) }
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var treatTouchAsStylus by remember { mutableStateOf(false) }
    val defaultBrush = Brush.builder()
        .setFamily(StockBrushes.pressurePenLatest)
        .setSize(5f)
        .setEpsilon(0.1f)
        .setColorIntArgb(Color.BLACK)
        .build()
    val canvasRenderer = CanvasStrokeRenderer.create()

    Column(modifier = modifier.fillMaxSize()) {
        Row {
            Text("Treat touch as stylus")
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = treatTouchAsStylus,
                onCheckedChange = { treatTouchAsStylus = it }
            )
        }
        Box(modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .pointerInput(treatTouchAsStylus) {
                if(!treatTouchAsStylus) {
                    Log.d("NotesCanvas", "pointerInput: $treatTouchAsStylus")
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale *= zoom
                        offset += pan
                    }
                }
            }
        ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                val rootView = FrameLayout(context)
                inProgressStrokesView.apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                }
                val predictor = MotionEventPredictor.newInstance(rootView)
                val touchListener = View.OnTouchListener { view, event ->
                    val isStylus = event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS || treatTouchAsStylus
                    Log.d("NoteCanvas", "isStylus: $isStylus")
                    if (isStylus && event.pointerCount == 1) {
                        predictor.record(event)
                        val predictedEvent = predictor.predict()
                        try {
                            when (event.actionMasked) {
                                MotionEvent.ACTION_DOWN -> {
                                    view.requestUnbufferedDispatch(event)
                                    val pointerIndex = event.actionIndex
                                    val pointerId = event.getPointerId(pointerIndex)
                                    currentPointerId.value = pointerId
                                    currentStrokeId.value = inProgressStrokesView.startStroke(event, pointerId, defaultBrush)
                                    Log.d("NoteCanvas", "startStroke: ${currentStrokeId.value}")
                                    true
                                }
                                MotionEvent.ACTION_MOVE -> {
                                    val pointerId = checkNotNull(currentPointerId.value)
                                    val strokeId = checkNotNull(currentStrokeId.value)
                                    for(pointerIndex in 0 until event.pointerCount) {
                                        if(event.getPointerId(pointerIndex) != pointerId) continue
                                        inProgressStrokesView.addToStroke(event, pointerId, strokeId)
                                        Log.d("NoteCanvas", "addToStroke: ${currentStrokeId.value}")
                                    }
                                    true
                                }
                                MotionEvent.ACTION_UP -> {
                                    val pointerIndex = event.actionIndex
                                    val pointerId = event.getPointerId(pointerIndex)
                                    check(pointerId == currentPointerId.value)
                                    val strokeId = checkNotNull(currentStrokeId.value)
                                    inProgressStrokesView.finishStroke(event, pointerId, strokeId)
                                    Log.d("NoteCanvas", "finishStroke: ${currentStrokeId.value}")
                                    currentPointerId.value = null
                                    currentStrokeId.value = null
                                    true
                                }
                                MotionEvent.ACTION_CANCEL -> {
                                    val pointerIndex = event.actionIndex
                                    val pointerId = event.getPointerId(pointerIndex)
                                    check(pointerId == currentPointerId.value)
                                    val strokeId = checkNotNull(currentStrokeId.value)
                                    inProgressStrokesView.cancelStroke(strokeId, event)
                                    Log.d("NoteCanvas", "cancelStroke: ${currentStrokeId.value}")
                                    currentPointerId.value = null
                                    currentStrokeId.value = null
                                    true
                                }
                                else -> false
                            }
                        } finally {
                            predictedEvent?.recycle()
                        }
                    } else {
                        false
                    }
                }
                rootView.setOnTouchListener(touchListener)
                rootView.addView(inProgressStrokesView)
                rootView
            }
        ) {

        }
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasTransform = Matrix()
            canvasTransform.setScale(scale, scale)
            canvasTransform.postTranslate(offset.x, offset.y)
            drawContext.canvas.nativeCanvas.concat(canvasTransform)
            val canvas = drawContext.canvas.nativeCanvas

            strokes.forEach { stroke ->
                canvasRenderer.draw(canvas, stroke, Matrix())
            }
        }
    }
    }
}
