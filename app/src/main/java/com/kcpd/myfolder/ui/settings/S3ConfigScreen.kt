package com.kcpd.myfolder.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun S3ConfigScreen(
    navController: NavController,
    viewModel: S3ConfigViewModel = hiltViewModel()
) {
    val s3Config by viewModel.s3Config.collectAsState(initial = null)

    var endpoint by remember { mutableStateOf("") }
    var accessKey by remember { mutableStateOf("") }
    var secretKey by remember { mutableStateOf("") }
    var bucketName by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("us-east-1") }
    var showSecretKey by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(s3Config) {
        s3Config?.let { config ->
            endpoint = config.endpoint
            accessKey = config.accessKey
            secretKey = config.secretKey
            bucketName = config.bucketName
            region = config.region
        }
    }

    /**
     * Validates S3 configuration before saving
     */
    fun validateConfig(): Boolean {
        validationError = when {
            endpoint.isBlank() -> "Endpoint required"
            !endpoint.startsWith("http://") && !endpoint.startsWith("https://") -> 
                "Endpoint must start with http:// or https://"
            endpoint.length < 10 -> "Endpoint appears invalid"
            accessKey.isBlank() -> "Access Key required"
            accessKey.length < 10 -> "Access Key too short (should be at least 10 chars)"
            secretKey.isBlank() -> "Secret Key required"
            secretKey.length < 10 -> "Secret Key too short (should be at least 10 chars)"
            bucketName.isBlank() -> "Bucket Name required"
            !bucketName.matches(Regex("^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$")) ->
                "Bucket name invalid (lowercase, numbers, dots, hyphens only)"
            region.isBlank() -> "Region required"
            else -> null
        }
        return validationError == null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("S3/Minio Configuration") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.saveConfig(
                                endpoint = endpoint,
                                accessKey = accessKey,
                                secretKey = secretKey,
                                bucketName = bucketName,
                                region = region
                            )
                            navController.navigateUp()
                        },
                        enabled = endpoint.isNotBlank() && accessKey.isNotBlank() &&
                                secretKey.isNotBlank() && bucketName.isNotBlank()
                    ) {
                        Icon(Icons.Default.Save, "Save")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Configure your S3 or Minio storage settings",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = endpoint,
                onValueChange = { endpoint = it },
                label = { Text("Endpoint URL") },
                placeholder = { Text("https://minio.example.com or https://s3.amazonaws.com") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                singleLine = true
            )

            OutlinedTextField(
                value = bucketName,
                onValueChange = { bucketName = it },
                label = { Text("Bucket Name") },
                placeholder = { Text("my-bucket") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = accessKey,
                onValueChange = { accessKey = it },
                label = { Text("Access Key") },
                placeholder = { Text("Your access key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = secretKey,
                onValueChange = { secretKey = it },
                label = { Text("Secret Key") },
                placeholder = { Text("Your secret key") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (showSecretKey) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { showSecretKey = !showSecretKey }) {
                        Icon(
                            if (showSecretKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            "Toggle visibility"
                        )
                    }
                },
                singleLine = true
            )

            OutlinedTextField(
                value = region,
                onValueChange = { region = it },
                label = { Text("Region (optional)") },
                placeholder = { Text("us-east-1") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Quick Setup Examples",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        "Minio: http://localhost:9000",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        "AWS S3: https://s3.amazonaws.com",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        "DigitalOcean Spaces: https://nyc3.digitaloceanspaces.com",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (validationError != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Configuration Error",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            validationError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            Button(
                onClick = {
                    if (validateConfig()) {
                        viewModel.saveConfig(
                            endpoint = endpoint,
                            accessKey = accessKey,
                            secretKey = secretKey,
                            bucketName = bucketName,
                            region = region
                        )
                        navController.navigateUp()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = endpoint.isNotBlank() && accessKey.isNotBlank() &&
                        secretKey.isNotBlank() && bucketName.isNotBlank()
            ) {
                Icon(Icons.Default.Save, "Save")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save Configuration")
            }
        }
    }
}
