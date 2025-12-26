package com.kcpd.myfolder.ui.note

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.kcpd.myfolder.data.model.MediaType
import com.kcpd.myfolder.ui.camera.CameraViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    navController: NavController,
    folderId: String? = null,
    viewModel: CameraViewModel = hiltViewModel()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var noteTitle by remember { mutableStateOf("") }
    var noteContent by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Note") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (noteTitle.isNotEmpty() || noteContent.isNotEmpty()) {
                                val fileName = if (noteTitle.isNotEmpty()) {
                                    "${noteTitle.take(50)}.txt"
                                } else {
                                    "Note_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.txt"
                                }

                                val noteFile = saveNoteToFile(
                                    context,
                                    fileName,
                                    noteContent
                                )
                                viewModel.addMediaFile(noteFile, MediaType.NOTE, folderId)
                                navController.navigateUp()
                            }
                        },
                        enabled = noteTitle.isNotEmpty() || noteContent.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Check, "Save")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = noteTitle,
                onValueChange = { noteTitle = it },
                label = { Text("Title (optional)") },
                placeholder = { Text("Enter note title") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = noteContent,
                onValueChange = { noteContent = it },
                label = { Text("Note Content") },
                placeholder = { Text("Start writing your note...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                minLines = 10
            )

            Text(
                text = "Tip: Your note will be saved automatically when you tap the save icon",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

private fun saveNoteToFile(
    context: android.content.Context,
    fileName: String,
    content: String
): File {
    val mediaDir = File(context.filesDir, "media").apply { mkdirs() }
    val file = File(mediaDir, fileName)
    file.writeText(content)
    return file
}
