package com.kcpd.myfolder.ui.scanner

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Scanner
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.launch

/**
 * Document Scanner Screen using ML Kit Document Scanner API.
 *
 * This provides a secure, on-device document scanning experience:
 * - No data leaves the device
 * - High-quality OCR and perspective correction
 * - Multi-page PDF generation
 * - Automatic edge detection
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentScannerScreen(
    navController: NavController,
    folderId: String? = null,
    viewModel: DocumentScannerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isScanning by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isImporting by remember { mutableStateOf(false) }

    // Configure ML Kit Document Scanner
    val scannerOptions = remember {
        GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)  // Allow importing from gallery
            .setPageLimit(50)  // Max 50 pages
            .setResultFormats(
                GmsDocumentScannerOptions.RESULT_FORMAT_PDF,
                GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
            )
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)  // Full mode with all features
            .build()
    }

    val scanner = remember(scannerOptions) {
        GmsDocumentScanning.getClient(scannerOptions)
    }

    // Scanner launcher
    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        android.util.Log.d("DocumentScanner", "Scanner result received - resultCode: ${result.resultCode}, RESULT_OK: ${Activity.RESULT_OK}")

        if (result.resultCode == Activity.RESULT_OK) {
            android.util.Log.d("DocumentScanner", "Parsing scanning result from intent...")
            val scanningResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)

            android.util.Log.d("DocumentScanner", "Scanning result: $scanningResult")
            android.util.Log.d("DocumentScanner", "PDF present: ${scanningResult?.pdf != null}")
            android.util.Log.d("DocumentScanner", "Pages present: ${scanningResult?.pages != null}, count: ${scanningResult?.pages?.size}")

            scanningResult?.pdf?.let { pdf ->
                android.util.Log.d("DocumentScanner", "✓ PDF generated successfully!")
                android.util.Log.d("DocumentScanner", "  PDF URI: ${pdf.uri}")
                android.util.Log.d("DocumentScanner", "  PDF page count: ${pdf.pageCount}")

                // Import the scanned PDF
                isImporting = true
                scope.launch {
                    try {
                        android.util.Log.d("DocumentScanner", "Starting PDF import process...")
                        android.util.Log.d("DocumentScanner", "  Target folder ID: $folderId")

                        // Import the PDF file with correct folderId
                        viewModel.importPdf(pdf.uri, folderId).collect { progress ->
                            android.util.Log.d("DocumentScanner", "Import progress event: $progress")
                            when (progress) {
                                is com.kcpd.myfolder.domain.usecase.ImportProgress.Importing -> {
                                    android.util.Log.d("DocumentScanner", "  → Importing: ${progress.fileName} (${progress.currentFile}/${progress.totalFiles})")
                                }
                                is com.kcpd.myfolder.domain.usecase.ImportProgress.FileImported -> {
                                    android.util.Log.d("DocumentScanner", "  ✓ File imported: ${progress.mediaFile.fileName}")
                                    android.util.Log.d("DocumentScanner", "    - ID: ${progress.mediaFile.id}")
                                    android.util.Log.d("DocumentScanner", "    - Type: ${progress.mediaFile.mediaType}")
                                    android.util.Log.d("DocumentScanner", "    - FolderId: ${progress.mediaFile.folderId}")
                                    android.util.Log.d("DocumentScanner", "    - Path: ${progress.mediaFile.filePath}")
                                }
                                is com.kcpd.myfolder.domain.usecase.ImportProgress.Completed -> {
                                    android.util.Log.d("DocumentScanner", "✓ Import completed! Total: ${progress.totalImported}")
                                    isImporting = false
                                    isScanning = false
                                    android.util.Log.d("DocumentScanner", "Navigating back to folder...")
                                    navController.navigateUp()
                                }
                                is com.kcpd.myfolder.domain.usecase.ImportProgress.Error -> {
                                    android.util.Log.e("DocumentScanner", "✗ Import error: ${progress.error.message}", progress.error)
                                    errorMessage = "Failed to import scanned document: ${progress.error.message}"
                                    isImporting = false
                                    isScanning = false
                                }
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("DocumentScanner", "✗ Failed to import scanned PDF - Exception caught", e)
                        android.util.Log.e("DocumentScanner", "  Exception type: ${e.javaClass.name}")
                        android.util.Log.e("DocumentScanner", "  Message: ${e.message}")
                        e.printStackTrace()
                        errorMessage = "Failed to import: ${e.message}"
                        isImporting = false
                        isScanning = false
                    }
                }
            } ?: run {
                android.util.Log.e("DocumentScanner", "✗ No PDF generated from scan!")
                android.util.Log.e("DocumentScanner", "  Scanning result was: $scanningResult")
                errorMessage = "No PDF generated from scan"
                isScanning = false
            }
        } else {
            android.util.Log.w("DocumentScanner", "Scanner cancelled or failed - resultCode: ${result.resultCode}")
            isScanning = false
        }
    }

    // Launch scanner on composition
    LaunchedEffect(Unit) {
        if (!isScanning) {
            android.util.Log.d("DocumentScanner", "╔═══════════════════════════════════════╗")
            android.util.Log.d("DocumentScanner", "║  DOCUMENT SCANNER INITIALIZING        ║")
            android.util.Log.d("DocumentScanner", "╚═══════════════════════════════════════╝")
            android.util.Log.d("DocumentScanner", "Folder ID: $folderId")

            android.util.Log.d("DocumentScanner", "Scanner options:")
            android.util.Log.d("DocumentScanner", "  - Gallery import: enabled")
            android.util.Log.d("DocumentScanner", "  - Page limit: 50")
            android.util.Log.d("DocumentScanner", "  - Result formats: PDF, JPEG")
            android.util.Log.d("DocumentScanner", "  - Scanner mode: FULL")

            isScanning = true
            android.util.Log.d("DocumentScanner", "Requesting scanner intent...")

            scanner.getStartScanIntent(context as Activity)
                .addOnSuccessListener { intentSender ->
                    android.util.Log.d("DocumentScanner", "✓ Scanner intent received, launching scanner UI...")
                    scannerLauncher.launch(
                        IntentSenderRequest.Builder(intentSender).build()
                    )
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("DocumentScanner", "✗ Failed to start scanner!", e)
                    android.util.Log.e("DocumentScanner", "  Exception type: ${e.javaClass.name}")
                    android.util.Log.e("DocumentScanner", "  Message: ${e.message}")
                    android.util.Log.e("DocumentScanner", "  Cause: ${e.cause}")
                    e.printStackTrace()

                    // Provide more helpful error message
                    val userMessage = when {
                        e.message?.contains("module install", ignoreCase = true) == true ->
                            "Document Scanner module needs to be downloaded. Please ensure you have a stable internet connection and try again."
                        e.message?.contains("not available", ignoreCase = true) == true ->
                            "Document Scanner not available on this device. Try updating Google Play Services."
                        else -> "Failed to start scanner: ${e.message}"
                    }

                    errorMessage = userMessage
                    isScanning = false
                }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan Document") },
                navigationIcon = {
                    IconButton(
                        onClick = { navController.navigateUp() },
                        enabled = !isImporting
                    ) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            when {
                isImporting -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(48.dp))
                        Text(
                            text = "Encrypting and saving document...",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                errorMessage != null -> {
                    // Provide fallback option to import PDF from file picker
                    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
                    ) { uri ->
                        if (uri != null) {
                            android.util.Log.d("DocumentScanner", "PDF selected from file picker: $uri")
                            isImporting = true
                            errorMessage = null
                            scope.launch {
                                try {
                                    viewModel.importPdf(uri, folderId).collect { progress ->
                                        android.util.Log.d("DocumentScanner", "Fallback import progress: $progress")
                                        when (progress) {
                                            is com.kcpd.myfolder.domain.usecase.ImportProgress.Completed -> {
                                                android.util.Log.d("DocumentScanner", "✓ Fallback import completed")
                                                isImporting = false
                                                navController.navigateUp()
                                            }
                                            is com.kcpd.myfolder.domain.usecase.ImportProgress.Error -> {
                                                android.util.Log.e("DocumentScanner", "✗ Fallback import error: ${progress.error.message}")
                                                errorMessage = "Failed to import: ${progress.error.message}"
                                                isImporting = false
                                            }
                                            else -> {}
                                        }
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("DocumentScanner", "✗ Fallback import exception", e)
                                    errorMessage = "Import failed: ${e.message}"
                                    isImporting = false
                                }
                            }
                        }
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Text(
                            text = "Scanner Unavailable",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = errorMessage!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Alternative Options:",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = "• Use the Import button to add existing PDFs\n• Use a third-party scanner app and import the result",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = { importLauncher.launch("application/pdf") },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Import PDF from Files")
                        }

                        OutlinedButton(
                            onClick = {
                                errorMessage = null
                                navController.navigateUp()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Cancel")
                        }
                    }
                }
                isScanning -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            Icons.Default.Scanner,
                            contentDescription = "Scanning",
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Launching scanner...",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    }
}
