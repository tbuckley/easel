package com.tbuckley.easel

import android.annotation.SuppressLint
import android.content.Context
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.ink.authoring.InProgressStrokeId
import androidx.ink.authoring.InProgressStrokesFinishedListener
import androidx.ink.authoring.InProgressStrokesView
import androidx.ink.brush.Brush
import androidx.ink.brush.StockBrushes
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.Stroke
import com.tbuckley.easel.ui.theme.EaselTheme
import android.view.MotionEvent
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import android.graphics.Bitmap
import android.graphics.Picture
import android.os.Environment
import androidx.compose.runtime.collectAsState
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.tbuckley.easel.data.CanvasElementRepository
import com.tbuckley.easel.data.CanvasElementLocalDataSource
import com.tbuckley.easel.data.local.CanvasElementDao
import androidx.room.Room
import com.tbuckley.easel.data.CanvasElement
import com.tbuckley.easel.data.local.AppDatabase
import kotlinx.coroutines.flow.forEach
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var canvasElementViewModel: CanvasElementViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize the database and DAO
        val database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "easel-database"
        ).build()
        val canvasElementDao = database.canvasElementDao()

        // Initialize the repository and ViewModel
        val localDataSource = CanvasElementLocalDataSource(canvasElementDao)
        val repository = CanvasElementRepository(localDataSource)
        canvasElementViewModel = CanvasElementViewModel(repository)

        setContent {
            EaselTheme {
                MainScreen(canvasElementViewModel)
            }
        }
    }
}

@Composable
fun MainScreen(
    canvasElementViewModel: CanvasElementViewModel
) {
    val converters = Converters()

    val settings = rememberSaveable(stateSaver = toolSettingsSaver(converters)) {
        mutableStateOf(ToolSettings(
            pen = Tool.Pen(
                Brush.createWithColorIntArgb(
                    family = StockBrushes.pressurePenLatest,
                    size = 3f,
                    colorIntArgb = Color.Black.toArgb(),
                    epsilon = 0.1f
                )
            ),
            eraser = Tool.Eraser(5f),
            selection = Tool.Selection,
        ))
    }

    val activeTool = rememberSaveable(stateSaver = activeToolSaver()) {
        mutableStateOf(ActiveTool.PEN)
    }

    val context = LocalContext.current

    // Load elements for a specific note (you might want to pass the noteId as a parameter)
    LaunchedEffect(Unit) {
        canvasElementViewModel.loadElementsForNote(1) // Example: Load elements for note with ID 1
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        NoteCanvas(
            modifier = Modifier.padding(innerPadding),
            elements = canvasElementViewModel.elements.collectAsState().value,
            tool = settings.value.getActiveTool(activeTool.value),
            onStrokesFinished = { strokes ->
                canvasElementViewModel.addStrokes(strokes.values)
            }
        )
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            Toolbar(
                settings = settings.value,
                activeTool = activeTool.value,
                setActiveTool = { tool -> activeTool.value = tool },
                setToolSettings = { newSettings -> settings.value = newSettings},
                onScreenshot = {
                    val PADDING = 16
                    val size = canvasElementViewModel.getTotalSize()
                    val picture = Picture()
                    val canvas = picture.beginRecording(size.right.toInt() + PADDING, size.bottom.toInt() + PADDING)
                    
                    // Fill the canvas with a white background
                    canvas.drawColor(Color.White.toArgb())

                    // Draw the elements
                    canvasElementViewModel.elements.value.forEach { element ->
                        canvas.save()
                        canvas.concat(element.transform)
                        element.render(canvas)
                        canvas.restore()
                    }
                    picture.endRecording()

                    val bitmap = Bitmap.createBitmap(picture)
                    saveImageToGallery(context, bitmap)
                },
                onClearAll = {
                    canvasElementViewModel.deleteAllForCurrentNote()
                }
            )
        }
    }
}

@SuppressLint("RestrictedApi")
@Composable
fun NoteCanvas(
    modifier: Modifier,
    elements: List<CanvasElement>,
    tool: Tool,
    onStrokesFinished: (Map<InProgressStrokeId, Stroke>) -> Unit
) {
    val context = LocalContext.current
    var transform by rememberSaveable(stateSaver = matrixSaver()) { mutableStateOf(Matrix()) }
    var removedStrokes = remember { mutableListOf<Stroke>() }

    val inProgressStrokesView = remember {
        InProgressStrokesView(context).apply {
            addFinishedStrokesListener(object : InProgressStrokesFinishedListener {
                @UiThread
                override fun onStrokesFinished(strokes: Map<InProgressStrokeId, Stroke>) {
                    onStrokesFinished(strokes)
                    removedStrokes.addAll(strokes.values)
                    removeFinishedStrokes(strokes.keys)
                }
            })
            useNewTPlusRenderHelper = true
        }
    }

    // Clear removedStrokes when elements change
    SideEffect {
        removedStrokes.clear()
    }

    val inputHandler = remember { 
        createInputStateMachine(
            initialTransform = transform,
            onTransformChanged = { newTransform ->
                transform = Matrix(transform).apply {
                    postConcat(newTransform)
                }
            }
        )
    }
    var drawingNode by remember { mutableStateOf<DrawingNode?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                createDrawingView(
                    context = context,
                    inProgressStrokesView = inProgressStrokesView,
                    onCreated = { rootView ->
                        val newDrawingNode = DrawingNode(rootView, inProgressStrokesView) { transform }
                        inputHandler.registerNode(newDrawingNode)
                        drawingNode = newDrawingNode
                    },
                    onMotionEvent = { event ->
                        inputHandler.handleMotionEvent(event)
                        true
                    }
                )
            }
        ) {}

        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvas = drawContext.canvas.nativeCanvas
            canvas.save()
            canvas.concat(transform)

            elements.forEach { element ->
                canvas.save()
                canvas.concat(element.transform)
                element.render(canvas)
                canvas.restore()
            }

            val renderer = CanvasStrokeRenderer.create()
            removedStrokes.forEach { stroke ->
                renderer.draw(canvas, stroke, transform)
            }

            canvas.restore()
        }
    }

    // Update brush if tool changes
    LaunchedEffect(tool) {
        if (tool is Tool.Pen) {
            drawingNode?.brush = tool.brush
        }
    }
}

private fun createInputStateMachine(
    initialTransform: Matrix,
    onTransformChanged: (Matrix) -> Unit
): InputStateMachine {
    val inputHandler = InputStateMachine()
    inputHandler.registerNode(IdleNode())
    inputHandler.registerNode(PinchZoomNode(onTransform = { matrix ->
        onTransformChanged(matrix)
    }))
    inputHandler.setCurrentNode("idle")
    return inputHandler
}

@SuppressLint("ClickableViewAccessibility")
private fun createDrawingView(
    context: Context,
    inProgressStrokesView: InProgressStrokesView,
    onCreated: (View) -> Unit,
    onMotionEvent: (MotionEvent) -> Boolean
): View {
    val rootView = FrameLayout(context)
    inProgressStrokesView.apply {
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
    }
    
    val touchListener = View.OnTouchListener { _, event ->
        onMotionEvent(event)
    }
    
    inProgressStrokesView.eagerInit()
    rootView.setOnTouchListener(touchListener)
    rootView.addView(inProgressStrokesView)
    
    onCreated(rootView)
    
    return rootView
}

fun matrixSaver(): Saver<Matrix, List<Float>> = Saver(
    save = { matrix ->
        val values = FloatArray(9)
        matrix.getValues(values)
        values.toList()
    },
    restore = { list ->
        Matrix().apply {
            setValues(list.toFloatArray())
        }
    }
)

private fun saveImageToGallery(context: Context, bitmap: Bitmap) {
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val filename = "Easel_$timestamp.png"

    val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
    val imageFile = File(imagesDir, filename)

    try {
        FileOutputStream(imageFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        // Notify the system about the new file
        ContextCompat.getExternalFilesDirs(context, null)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
