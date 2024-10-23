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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.tbuckley.easel.data.local.NoteEntity
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var canvasElementViewModel: CanvasElementViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize the database and DAOs
        val database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "easel-database"
        ).build()
        val canvasElementDao = database.canvasElementDao()
        val noteDao = database.noteDao()

        // Initialize the repository and ViewModel
        val localDataSource = CanvasElementLocalDataSource(canvasElementDao)
        val repository = CanvasElementRepository(localDataSource)
        canvasElementViewModel = CanvasElementViewModel(repository, noteDao)

        setContent {
            EaselTheme {
                MainScreen(canvasElementViewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
    val uiState by canvasElementViewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var isSidebarOpen by remember { mutableStateOf(false) }
    var selectedNoteId by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            // Canvas and Toolbar
                NoteCanvas(
                    modifier = Modifier.padding(innerPadding),
                    elements = uiState.elements,
                    tool = settings.value.getActiveTool(activeTool.value),
                    onStrokesFinished = { strokes ->
                        canvasElementViewModel.addStrokes(strokes.values)
                    }
                )
                Box(
                    modifier = Modifier.padding(innerPadding).fillMaxWidth(),
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
                            canvasElementViewModel.uiState.value.elements.forEach { element ->
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


                // Add the menu button in the top-left corner
                IconButton(
                    onClick = { isSidebarOpen = true },
                    modifier = Modifier
                        .padding(innerPadding)
                        .padding(start = 16.dp, top = 16.dp)
                        .align(Alignment.TopStart)
                        .size(48.dp)
                        .shadow(4.dp, shape = CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainer, shape = CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Open sidebar",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

            // Clickable overlay to close sidebar when tapped outside
            AnimatedVisibility(
                visible = isSidebarOpen,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.2f))
                        .clickable { isSidebarOpen = false }
                )
            }

            // Sidebar
            AnimatedVisibility(
                visible = isSidebarOpen,
                enter = slideInHorizontally(initialOffsetX = { -it }),
                exit = slideOutHorizontally(targetOffsetX = { -it })
            ) {
                NotesSidebar(
                    modifier = Modifier
                        .width(300.dp)
                        .fillMaxHeight()
                        .shadow(elevation = 8.dp)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(innerPadding),
                    notes = uiState.notes,
                    onNoteSelected = { note ->
                        canvasElementViewModel.loadElementsForNote(note.id)
                        selectedNoteId = note.id
                        isSidebarOpen = false
                    },
                    onCreateNote = {
                        canvasElementViewModel.createNewNote()
                        isSidebarOpen = false
                    },
                    onDeleteNote = { note ->
                        canvasElementViewModel.deleteNote(note)
                        if (selectedNoteId == note.id) {
                            selectedNoteId = null
                        }
                    },
                    selectedNoteId = selectedNoteId
                )
            }
        }
    }
}

@Composable
fun NotesSidebar(
    modifier: Modifier = Modifier,
    notes: List<NoteEntity>,
    onNoteSelected: (NoteEntity) -> Unit,
    onCreateNote: () -> Unit,
    onDeleteNote: (NoteEntity) -> Unit,
    selectedNoteId: Int? // New parameter to track the selected note
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(16.dp)
    ) {
        Text("Notes", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onCreateNote) {
            Icon(Icons.Default.Add, contentDescription = "Create new note")
            Spacer(modifier = Modifier.width(8.dp))
            Text("New Note")
        }
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn {
            items(
                count = notes.size,
                key = { index -> notes[index].id }
            ) { index ->
                NoteItem(
                    note = notes[index],
                    onNoteSelected = onNoteSelected,
                    onDeleteNote = onDeleteNote,
                    isSelected = notes[index].id == selectedNoteId // Pass the selection state
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteItem(
    note: NoteEntity,
    onNoteSelected: (NoteEntity) -> Unit,
    onDeleteNote: (NoteEntity) -> Unit,
    isSelected: Boolean // New parameter to track selection state
) {
    ListItem(
        headlineContent = { Text("Note ${note.id}") },
        supportingContent = { Text(note.createdAt.toString()) },
        trailingContent = {
            IconButton(onClick = { onDeleteNote(note) }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete note")
            }
        },
        modifier = Modifier
            .background(
                color = if (isSelected) Color.Gray
                else MaterialTheme.colorScheme.surface
            )
            .clickable { onNoteSelected(note) }
    )
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
