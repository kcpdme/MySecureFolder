package com.kcpd.myfolder.ui.note

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val contentFocusRequester = remember { FocusRequester() }

    val canSave = noteTitle.isNotEmpty() || noteContent.isNotEmpty()

    // Background gradient
    val backgroundGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0F0F1A),
            Color(0xFF1A1A2E),
            Color(0xFF16213E)
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            Icons.Default.ArrowBack, 
                            "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    // Character count
                    Text(
                        text = "${noteContent.length}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    
                    // Save button
                    FilledIconButton(
                        onClick = {
                            if (canSave) {
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
                        enabled = canSave,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (canSave) Color(0xFF4facfe) else Color.Gray.copy(alpha = 0.3f),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.Check, "Save")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundGradient)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                
                // Title field - minimal, large
                BasicTextField(
                    value = noteTitle,
                    onValueChange = { noteTitle = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    cursorBrush = SolidColor(Color(0xFF4facfe)),
                    singleLine = true,
                    decorationBox = { innerTextField ->
                        Box {
                            if (noteTitle.isEmpty()) {
                                Text(
                                    text = "Title",
                                    style = TextStyle(
                                        color = Color.White.copy(alpha = 0.3f),
                                        fontSize = 28.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                            innerTextField()
                        }
                    }
                )
                
                // Subtle divider
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.1f),
                                    Color.Transparent
                                )
                            )
                        )
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                // Content field - takes most space
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    BasicTextField(
                        value = noteContent,
                        onValueChange = { noteContent = it },
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .focusRequester(contentFocusRequester),
                        textStyle = TextStyle(
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 16.sp,
                            lineHeight = 26.sp
                        ),
                        cursorBrush = SolidColor(Color(0xFF4facfe)),
                        decorationBox = { innerTextField ->
                            Box {
                                if (noteContent.isEmpty()) {
                                    Text(
                                        text = "Start writing...",
                                        style = TextStyle(
                                            color = Color.White.copy(alpha = 0.3f),
                                            fontSize = 16.sp
                                        )
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }
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
