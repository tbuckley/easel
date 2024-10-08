package com.tbuckley.easel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.ink.brush.Brush
import androidx.ink.brush.StockBrushes

// Define the Tool sealed class and supporting data classes
sealed class Tool {
    data class Pen(val brush: Brush) : Tool()
    data class Eraser(val size: Float) : Tool()
    data object Selection : Tool()
}

data class ToolSettings(
    val pen: Tool.Pen,
    val eraser: Tool.Eraser,
    val selection: Tool.Selection,
)

fun ToolSettings.getActiveTool(tool: ActiveTool) = when (tool) {
    ActiveTool.PEN -> this.pen
    ActiveTool.ERASER -> this.eraser
    ActiveTool.SELECTION -> this.selection
}

enum class ActiveTool {
    PEN, ERASER, SELECTION
}

@Composable
fun Toolbar(
    settings: ToolSettings,
    activeTool: ActiveTool,
    setToolSettings: (ToolSettings) -> Unit,
    setActiveTool: (ActiveTool) -> Unit
) {
    Row(
        modifier = Modifier
            .padding(32.dp)
            .shadow(elevation = 2.dp, shape = RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .wrapContentSize()
    ) {
        // Row of icon buttons
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolIconButton(
                icon = Icons.Default.Edit,
                contentDescription = "Pen",
                isSelected = activeTool == ActiveTool.PEN,
                onClick = { setActiveTool(ActiveTool.PEN) }
            )
            ToolIconButton(
                icon = Icons.Default.Delete,
                contentDescription = "Eraser",
                isSelected = activeTool == ActiveTool.ERASER,
                onClick = { setActiveTool(ActiveTool.ERASER) }
            )
            ToolIconButton(
                icon = Icons.Default.SelectAll,
                contentDescription = "Selection",
                isSelected = activeTool == ActiveTool.SELECTION,
                onClick = { setActiveTool(ActiveTool.SELECTION) }
            )

            PenSettings(settings.pen.brush, penActive =  activeTool == ActiveTool.PEN) { brush ->
                setToolSettings(settings.copy(pen = Tool.Pen(brush)))
                setActiveTool(ActiveTool.PEN)
            }
        }
    }
}

@Composable
fun ToolIconButton(
    icon: ImageVector,
    contentDescription: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha=0.5f)
        )
    ) {
        Icon(icon, contentDescription = contentDescription)
    }
}

@Composable
fun PenSettings(
    brush: Brush,
    penActive: Boolean,
    onBrushChange: (Brush) -> Unit
) {
    Row(modifier = Modifier.padding(8.dp, 0.dp)) {
        ColorPalette(selectedColor = Color(brush.colorIntArgb), penActive = penActive) { color ->
            onBrushChange(brush.copyWithColorIntArgb(colorIntArgb = color.toArgb()))
        }
    }
}

@Composable
fun ColorPalette(
    selectedColor: Color,
    penActive: Boolean,
    onColorSelected: (Color) -> Unit
) {
    val colors = listOf(Color.Black, Color.Red, Color.Blue)
    Row {
        colors.forEach { color ->
            val active = penActive && color == selectedColor
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .padding(4.dp)
                    .border(
                        width = if (active) 2.dp else 0.dp,
                        color = if (active) color else Color.Transparent,
                        shape = CircleShape
                    )
                    .padding(if (active) 4.dp else 0.dp)
                    .background(color, shape = CircleShape)
                    .clickable {
                        onColorSelected(color)
                    }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewToolbar() {
    val settings = remember { mutableStateOf(ToolSettings(
        pen = Tool.Pen(Brush.createWithColorIntArgb(
            family = StockBrushes.pressurePenLatest,
            size = 3f,
            colorIntArgb = Color.Black.toArgb(),
            epsilon = 0.1f
        )),
        eraser = Tool.Eraser(5f),
        selection = Tool.Selection,
    )) }
    val activeTool = remember { mutableStateOf(ActiveTool.PEN) }
    Toolbar(
        settings = settings.value,
        activeTool = activeTool.value,
        setActiveTool = { tool -> activeTool.value = tool },
        setToolSettings = { newSettings -> settings.value = newSettings}
    )
}