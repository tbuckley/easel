package com.tbuckley.easel

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Matrix
import android.os.Bundle
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
import androidx.compose.ui.graphics.nativeCanvas
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
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.Stroke
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
    val scale by remember { mutableStateOf(1f) }
    val offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var treatTouchAsStylus by remember { mutableStateOf(false) }
    val canvasRenderer = CanvasStrokeRenderer.create()

    val inputHandler = InputStateMachine()
    inputHandler.registerNode(IdleNode())
    inputHandler.setCurrentNode("idle")

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
                inputHandler.registerNode(DrawingNode(rootView, inProgressStrokesView))
                val touchListener = View.OnTouchListener { _, event ->
                    inputHandler.handleMotionEvent(event)
                    true
                }
                rootView.setOnTouchListener(touchListener)
                rootView.addView(inProgressStrokesView)
                rootView
            }
        ) {}
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
