package com.tbuckley.easel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tbuckley.easel.data.local.NoteEntity
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun NotesManager(
    modifier: Modifier = Modifier,
    notes: List<NoteEntity>,
    onSelectNote: (NoteEntity) -> Unit,
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
                    onSelectNote = onSelectNote,
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
    onSelectNote: (NoteEntity) -> Unit,
    onDeleteNote: (NoteEntity) -> Unit,
    isSelected: Boolean
) {
    val dateFormatter = remember { SimpleDateFormat("h:mma d MMM yyyy", Locale.getDefault()) }

    ListItem(
        headlineContent = { Text("Note ${note.id}") },
        supportingContent = {
            Text(dateFormatter.format(note.createdAt))
        },
        trailingContent = {
            IconButton(onClick = { onDeleteNote(note) }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete note")
            }
        },
        colors = ListItemDefaults.colors(containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .clickable { onSelectNote(note) }
            .clip(RoundedCornerShape(8.dp))
    )
}
