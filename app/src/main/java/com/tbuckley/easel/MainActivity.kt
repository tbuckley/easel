package com.tbuckley.easel

import android.annotation.SuppressLint
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.viewinterop.AndroidView
import androidx.ink.authoring.InProgressStrokeId
import androidx.ink.authoring.InProgressStrokesFinishedListener
import androidx.ink.authoring.InProgressStrokesView
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.Stroke
import com.tbuckley.easel.ui.theme.EaselTheme

class MainActivity : ComponentActivity(), InProgressStrokesFinishedListener {
    private lateinit var inProgressStrokesView: InProgressStrokesView
    private val finalStrokes = mutableListOf<Stroke>()
    private val tool = mutableStateOf<Tool>(Tool.Eraser(size = 5f))

    @SuppressLint("RestrictedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        inProgressStrokesView = InProgressStrokesView(this)
        inProgressStrokesView.addFinishedStrokesListener(this)
        inProgressStrokesView.useNewTPlusRenderHelper = true

        setContent {
            EaselTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NoteCanvas(
                        modifier = Modifier.padding(innerPadding),
                        inProgressStrokesView = inProgressStrokesView,
                        strokes = finalStrokes,
                    )
                    Toolbar(tool.value, setTool = { tool.value = it } )
                }
            }
        }
    }

    @UiThread
    override fun onStrokesFinished(strokes: Map<InProgressStrokeId, Stroke>) {
        Log.d("MainActivity", "onStrokesFinished: ${strokes.size}")
        finalStrokes += strokes.values //.map { it.copy(it.brush.copyWithColorIntArgb(Color.RED)) }
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
    val transform = remember { mutableStateOf(Matrix()) }
    val canvasRenderer = CanvasStrokeRenderer.create()

    // Set up input handler
    val inputHandler = InputStateMachine()
    inputHandler.registerNode(IdleNode())
    inputHandler.registerNode(PinchZoomNode(onTransform = { matrix ->
        transform.value = Matrix(transform.value).apply {
            postConcat(matrix)
        }
    }))
    inputHandler.setCurrentNode("idle")

    Box(modifier = modifier.fillMaxSize()) {
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
                inputHandler.registerNode(DrawingNode(rootView, inProgressStrokesView, transform))
                val touchListener = View.OnTouchListener { _, event ->
                    inputHandler.handleMotionEvent(event)
                    true
                }
                inProgressStrokesView.eagerInit()
                rootView.setOnTouchListener(touchListener)
                rootView.addView(inProgressStrokesView)
                rootView
            }
        ) {}
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawContext.canvas.nativeCanvas.concat(transform.value)
            val canvas = drawContext.canvas.nativeCanvas

            strokes.forEach { stroke ->
                canvasRenderer.draw(canvas, stroke, transform.value)
            }
        }
    }
}
