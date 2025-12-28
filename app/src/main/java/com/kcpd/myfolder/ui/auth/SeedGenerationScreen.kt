package com.kcpd.myfolder.ui.auth

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Seed generation screen with long-press to generate animation.
 * Part of the onboarding flow after password creation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeedGenerationScreen(
    password: String,
    onSeedGenerated: (List<String>) -> Unit,
    onComplete: () -> Unit,
    generateSeedWords: () -> List<String>
) {
    var generationState by remember { mutableStateOf<SeedGenerationState>(SeedGenerationState.Initial) }
    var seedWords by remember { mutableStateOf<List<String>>(emptyList()) }
    var hasBackedUp by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            when (generationState) {
                is SeedGenerationState.Initial -> {
                    InitialGenerationUI(
                        onGenerationComplete = {
                            // Generate the seed words
                            seedWords = generateSeedWords()
                            onSeedGenerated(seedWords)
                            generationState = SeedGenerationState.Generated
                        }
                    )
                }

                is SeedGenerationState.Generated -> {
                    SeedWordsDisplayUI(
                        seedWords = seedWords,
                        hasBackedUp = hasBackedUp,
                        onCopy = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Seed Words", seedWords.joinToString(" "))
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Seed words copied to clipboard", Toast.LENGTH_SHORT).show()
                            hasBackedUp = true
                        },
                        onDownload = {
                            val success = downloadSeedWords(context, seedWords)
                            if (success) {
                                Toast.makeText(context, "Saved to Downloads folder", Toast.LENGTH_SHORT).show()
                                hasBackedUp = true
                            } else {
                                Toast.makeText(context, "Failed to save file", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onComplete = onComplete
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

sealed class SeedGenerationState {
    object Initial : SeedGenerationState()
    object Generated : SeedGenerationState()
}

@Composable
private fun InitialGenerationUI(
    onGenerationComplete: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var isComplete by remember { mutableStateOf(false) }

    // Progress animation while pressed
    LaunchedEffect(isPressed) {
        if (isPressed && !isComplete) {
            val startTime = System.currentTimeMillis()
            val duration = 2000L // 2 seconds to fill

            while (isPressed && progress < 1f) {
                val elapsed = System.currentTimeMillis() - startTime
                progress = (elapsed.toFloat() / duration).coerceIn(0f, 1f)
                
                if (progress >= 1f) {
                    isComplete = true
                    delay(200) // Small delay for visual feedback
                    onGenerationComplete()
                }
                delay(16) // ~60fps
            }
        } else if (!isPressed && !isComplete) {
            // Reset progress when released early
            progress = 0f
        }
    }

    // Pulse animation for the outer ring
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Key icon
        Icon(
            imageVector = Icons.Default.Key,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Generate Your Seed",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Your seed words are the key to your encrypted data.\nLong press the button below to generate them securely.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Circular progress button
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(200.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            tryAwaitRelease()
                            isPressed = false
                        }
                    )
                }
        ) {
            // Background pulse ring (only when not pressed)
            if (!isPressed && !isComplete) {
                Canvas(
                    modifier = Modifier.size((180 * pulseScale).dp)
                ) {
                    drawCircle(
                        color = Color(0xFF6200EE).copy(alpha = 0.2f),
                        radius = size.minDimension / 2,
                        style = Stroke(width = 4.dp.toPx())
                    )
                }
            }

            // Progress arc background
            Canvas(modifier = Modifier.size(180.dp)) {
                drawArc(
                    color = Color.Gray.copy(alpha = 0.3f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round),
                    topLeft = Offset(6.dp.toPx(), 6.dp.toPx()),
                    size = Size(size.width - 12.dp.toPx(), size.height - 12.dp.toPx())
                )
            }

            // Progress arc fill
            Canvas(modifier = Modifier.size(180.dp)) {
                drawArc(
                    color = Color(0xFF6200EE),
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round),
                    topLeft = Offset(6.dp.toPx(), 6.dp.toPx()),
                    size = Size(size.width - 12.dp.toPx(), size.height - 12.dp.toPx())
                )
            }

            // Center circle with icon/text
            Surface(
                modifier = Modifier.size(140.dp),
                shape = CircleShape,
                color = if (isPressed) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = if (isComplete) Icons.Default.Check else Icons.Default.Fingerprint,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = if (isPressed || isComplete) 
                                MaterialTheme.colorScheme.onPrimary 
                            else 
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!isComplete) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isPressed) "Hold..." else "Hold",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isPressed) 
                                    MaterialTheme.colorScheme.onPrimary 
                                else 
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = if (isPressed) "Keep holding..." else "Long press to generate",
            style = MaterialTheme.typography.bodyMedium,
            color = if (isPressed) 
                MaterialTheme.colorScheme.primary 
            else 
                MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Info card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "🔐 Why long press?",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "The long press ensures intentional action and adds entropy to the random generation process, making your seed more secure.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SeedWordsDisplayUI(
    seedWords: List<String>,
    hasBackedUp: Boolean,
    onCopy: () -> Unit,
    onDownload: () -> Unit,
    onComplete: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Success icon
        Icon(
            imageVector = Icons.Default.Shield,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = Color(0xFF4CAF50)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Your Recovery Seed",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Write these 12 words down and store them safely.\nThey are the ONLY way to recover your data.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Seed words grid
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Display in 2 columns, 6 rows
                for (row in 0 until 6) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        for (col in 0 until 2) {
                            val index = row * 2 + col
                            if (index < seedWords.size) {
                                SeedWordChip(
                                    number = index + 1,
                                    word = seedWords[index],
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Copy button
            OutlinedButton(
                onClick = onCopy,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Copy")
            }

            // Download button
            OutlinedButton(
                onClick = onDownload,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Warning card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "⚠️ Critical Warning",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = "• Never share these words with anyone\n" +
                            "• Store them offline in a secure location\n" +
                            "• Without these words, lost data CANNOT be recovered\n" +
                            "• Consider writing them on paper",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Let's Go button
        Button(
            onClick = onComplete,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = hasBackedUp,
            shape = MaterialTheme.shapes.medium
        ) {
            Icon(
                imageVector = Icons.Default.RocketLaunch,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (hasBackedUp) "Let's Go!" else "Backup your seed first",
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (!hasBackedUp) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Copy or save your seed words to continue",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SeedWordChip(
    number: Int,
    word: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.padding(horizontal = 4.dp),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$number.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(24.dp)
            )
            Text(
                text = word,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun downloadSeedWords(context: Context, seedWords: List<String>): Boolean {
    return try {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "myfolder_seed_$timestamp.txt"
        
        val content = buildString {
            appendLine("MyFolder Recovery Seed Words")
            appendLine("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
            appendLine()
            appendLine("IMPORTANT: Keep these words safe and secret!")
            appendLine("They are required to recover your encrypted data.")
            appendLine()
            seedWords.forEachIndexed { index, word ->
                appendLine("${index + 1}. $word")
            }
            appendLine()
            appendLine("Full phrase: ${seedWords.joinToString(" ")}")
        }

        // Save to Downloads folder
        val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_DOWNLOADS
        )
        val file = File(downloadsDir, fileName)
        file.writeText(content)
        true
    } catch (e: Exception) {
        android.util.Log.e("SeedGeneration", "Failed to save seed words", e)
        false
    }
}
