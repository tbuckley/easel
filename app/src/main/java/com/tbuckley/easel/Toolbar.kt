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
import androidx.compose.ui.unit.dp
import androidx.ink.brush.Brush
import androidx.ink.brush.StockBrushes

// Define the Tool sealed class and supporting data classes
sealed class Tool {
    data class Pen(val brush: Brush) : Tool()
    data class Eraser(val size: Float) : Tool()
    data object Selection : Tool()
}

@Composable
fun Toolbar(
    tool: Tool,
    setTool: (Tool) -> Unit
) {
    val shape = RoundedCornerShape(8.dp)
    // Floating toolbar that overlaps the Canvas
    Row(
        modifier = Modifier
            .padding(32.dp)
            .shadow(elevation = 2.dp, shape = shape)
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
                isSelected = tool is Tool.Pen,
                onClick = {
                    setTool(Tool.Pen(brush = Brush.createWithColorIntArgb(
                        family = StockBrushes.pressurePenLatest,
                        size = 5f,
                        colorIntArgb = Color.Black.toArgb(),
                        epsilon = 0.1f)))
                }
            )
            ToolIconButton(
                icon = Icons.Default.Delete,
                contentDescription = "Eraser",
                isSelected = tool is Tool.Eraser,
                onClick = {
                    setTool(Tool.Eraser(size = 10f))
                }
            )
            ToolIconButton(
                icon = Icons.Default.SelectAll,
                contentDescription = "Selection",
                isSelected = tool is Tool.Selection,
                onClick = {
                    setTool(Tool.Selection)
                }
            )

            // Additional settings based on the selected tool
            when (tool) {
                is Tool.Pen -> {
                    PenSettings(tool.brush) { newBrush ->
                        setTool(Tool.Pen(newBrush))
                    }
                }
                is Tool.Eraser -> {
                    // No additional settings for Eraser tool
                }
                Tool.Selection -> {
                    // No additional settings for Selection tool
                }
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
            contentColor = if (isSelected) MaterialTheme.colorScheme.primary else LocalContentColor.current
        )
    ) {
        Icon(icon, contentDescription = contentDescription)
    }
}

@Composable
fun PenSettings(
    brush: Brush,
    onBrushChange: (Brush) -> Unit
) {
    Row(modifier = Modifier.padding(8.dp, 0.dp)) {
        ColorPalette(selectedColor = Color(brush.colorIntArgb)) { color ->
            onBrushChange(brush.copyWithColorIntArgb(colorIntArgb = color.toArgb()))
        }
    }
}

@Composable
fun ColorPalette(
    selectedColor: Color,
    onColorSelected: (Color) -> Unit
) {
    val colors = listOf(Color.Black, Color.Red, Color.Green, Color.Blue, Color.Yellow, Color.Magenta)
    Row {
        colors.forEach { color ->
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .padding(4.dp)
                    .background(color, shape = CircleShape)
                    .border(
                        width = if (color == selectedColor) 2.dp else 0.dp,
                        color = if (color == selectedColor) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                        shape = CircleShape
                    )
                    .clickable {
                        onColorSelected(color)
                    }
            )
        }
    }
}