package com.example.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.backup.BackupCrypto
import com.example.backup.BackupManager
import com.example.backup.BackupValidationResult
import com.example.ui.KiranaViewModel
import com.example.ui.theme.GroceryGreen
import com.example.ui.theme.GroceryOrange
import com.example.ui.theme.kiranaTextFieldColors
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    viewModel: KiranaViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isOperating by remember { mutableStateOf(false) }
    var operationMessage by remember { mutableStateOf<String?>(null) }

    // Export with Password Dialog State
    var showExportPasswordDialog by remember { mutableStateOf(false) }
    var exportPasswordInput by remember { mutableStateOf("") }
    var exportPasswordVisible by remember { mutableStateOf(false) }

    // Decrypt Input Dialog State (when restoring encrypted backup)
    var showDecryptPasswordDialog by remember { mutableStateOf(false) }
    var rawEncryptedContent by remember { mutableStateOf<String?>(null) }
    var decryptPasswordInput by remember { mutableStateOf("") }
    var decryptPasswordVisible by remember { mutableStateOf(false) }
    var decryptError by remember { mutableStateOf<String?>(null) }

    // Restore Confirmation Dialog State
    var pendingRestoreResult by remember { mutableStateOf<BackupValidationResult?>(null) }
    var pendingJsonContent by remember { mutableStateOf<String?>(null) }
    var pendingPassword by remember { mutableStateOf<String?>(null) }
    var isRestoring by remember { mutableStateOf(false) }

    // File Picker for restore
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                isOperating = true
                operationMessage = "Reading and validating backup file..."
                try {
                    val content = BackupManager.readBackupFromUri(context, uri)
                    if (content.isNullOrBlank()) {
                        Toast.makeText(context, "Could not read backup file", Toast.LENGTH_LONG).show()
                    } else if (BackupCrypto.isEncrypted(content)) {
                        // Needs password to decrypt and preview contents
                        rawEncryptedContent = content
                        decryptPasswordInput = ""
                        decryptError = null
                        showDecryptPasswordDialog = true
                    } else {
                        // Plain unencrypted JSON backup
                        val validation = viewModel.validateBackupContent(content)
                        if (validation.isValid) {
                            pendingJsonContent = content
                            pendingPassword = null
                            pendingRestoreResult = validation
                        } else {
                            Toast.makeText(context, "Invalid backup: ${validation.errorMessage}", Toast.LENGTH_LONG).show()
                        }
                    }
                } finally {
                    isOperating = false
                    operationMessage = null
                }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Backup & Restore",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .testTag("backup_restore_screen"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // SECTION 1: Automatic Daily Backup
            Text(
                text = "1. AUTOMATIC DAILY BACKUP (LOCAL ONLY)",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp
            )

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = GroceryGreen.copy(alpha = 0.12f),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Alarm,
                                    contentDescription = null,
                                    tint = GroceryGreen,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Daily Background Auto-Backup",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Runs daily at 02:00 AM • Stored strictly on device",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "🔒 Zero Cloud Sync: Data remains 100% private on this device. Store data is never automatically uploaded to any cloud server.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Current Schedule Status:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = GroceryGreen, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Active (Daily Local)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GroceryGreen)
                        }
                    }
                }
            }

            // SECTION 2: Manual Backup
            Text(
                text = "2. MANUAL BACKUP",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp
            )

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Create an instant snapshot of your entire shop catalog, sales history, customer khata, purchases, stock movements, and shop settings.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Standard Unencrypted Backup Button
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isOperating = true
                                operationMessage = "Creating local JSON backup..."
                                viewModel.exportBackup(context, password = null) { file: File? ->
                                    isOperating = false
                                    operationMessage = null
                                    if (file != null) {
                                        val fileUri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            file
                                        )
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "application/json"
                                            putExtra(Intent.EXTRA_STREAM, fileUri)
                                            putExtra(Intent.EXTRA_SUBJECT, "Grocery Shop Backup - ${file.name}")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Save or Share Backup File"))
                                    } else {
                                        Toast.makeText(context, "Local backup failed", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("manual_backup_json_btn")
                    ) {
                        Icon(imageVector = Icons.Default.FolderZip, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Create Local JSON Backup", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                    }

                    // Optional Password-Protected / Encrypted Backup Button
                    OutlinedButton(
                        onClick = {
                            exportPasswordInput = ""
                            exportPasswordVisible = false
                            showExportPasswordDialog = true
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("encrypted_backup_btn")
                    ) {
                        Icon(imageVector = Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Create Encrypted Backup (Password-Protected)", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // SECTION 3: Restore From Backup
            Text(
                text = "3. RESTORE FROM BACKUP",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp
            )

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Restore shop data from an existing backup JSON file. Previews complete counts and verifies integrity before replacing data. Atomic restore rolls back 100% on failure.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedButton(
                        onClick = {
                            filePickerLauncher.launch("*/*")
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("restore_select_file_btn")
                    ) {
                        Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Select Backup File to Restore", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // Operation Progress Indicator
            if (isOperating) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = operationMessage ?: "Processing...",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }

    // DIALOG: Create Encrypted Backup with Password
    if (showExportPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showExportPasswordDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Password-Protected Backup",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Set a secure password to encrypt your customer khata, sales records, and business data using AES-256-GCM.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = exportPasswordInput,
                        onValueChange = { exportPasswordInput = it },
                        label = { Text("Backup Password") },
                        placeholder = { Text("Enter password") },
                        colors = kiranaTextFieldColors(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = if (exportPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { exportPasswordVisible = !exportPasswordVisible }) {
                                Icon(
                                    imageVector = if (exportPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle password visibility",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("export_password_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val pass = exportPasswordInput.trim()
                        if (pass.isEmpty()) {
                            Toast.makeText(context, "Password cannot be empty", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        showExportPasswordDialog = false
                        coroutineScope.launch {
                            isOperating = true
                            operationMessage = "Encrypting and exporting backup..."
                            viewModel.exportBackup(context, password = pass) { file: File? ->
                                isOperating = false
                                operationMessage = null
                                if (file != null) {
                                    val fileUri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        file
                                    )
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/json"
                                        putExtra(Intent.EXTRA_STREAM, fileUri)
                                        putExtra(Intent.EXTRA_SUBJECT, "Encrypted Grocery Shop Backup - ${file.name}")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Save or Share Encrypted Backup"))
                                } else {
                                    Toast.makeText(context, "Encrypted backup export failed", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.testTag("confirm_export_encrypted_btn")
                ) {
                    Text("Export Encrypted Backup", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showExportPasswordDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // DIALOG: Decrypt Encrypted Backup for Restore
    if (showDecryptPasswordDialog && rawEncryptedContent != null) {
        val encryptedJson = rawEncryptedContent!!
        AlertDialog(
            onDismissRequest = {
                showDecryptPasswordDialog = false
                rawEncryptedContent = null
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Encrypted Backup Detected",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "This backup is password-protected. Enter the password to unlock and preview store contents before restore:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = decryptPasswordInput,
                        onValueChange = {
                            decryptPasswordInput = it
                            decryptError = null
                        },
                        label = { Text("Password") },
                        placeholder = { Text("Enter backup password") },
                        colors = kiranaTextFieldColors(),
                        singleLine = true,
                        isError = decryptError != null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = if (decryptPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { decryptPasswordVisible = !decryptPasswordVisible }) {
                                Icon(
                                    imageVector = if (decryptPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle password visibility",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("decrypt_password_input")
                    )
                    if (decryptError != null) {
                        Text(
                            text = decryptError!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val pass = decryptPasswordInput.trim()
                        if (pass.isEmpty()) {
                            decryptError = "Please enter password"
                            return@Button
                        }
                        val validation = viewModel.validateEncryptedBackupContent(encryptedJson, pass)
                        if (validation.isValid) {
                            pendingRestoreResult = validation
                            pendingJsonContent = encryptedJson
                            pendingPassword = pass
                            showDecryptPasswordDialog = false
                            rawEncryptedContent = null
                        } else {
                            decryptError = validation.errorMessage ?: "Incorrect password or corrupted backup"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.testTag("unlock_backup_btn")
                ) {
                    Text("Unlock & Validate", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    showDecryptPasswordDialog = false
                    rawEncryptedContent = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // MANDATORY RESTORE CONFIRMATION DIALOG PER PHASE 13 REQUIREMENTS
    if (pendingRestoreResult != null && pendingJsonContent != null) {
        val valResult = pendingRestoreResult!!
        AlertDialog(
            onDismissRequest = {
                if (!isRestoring) {
                    pendingRestoreResult = null
                    pendingJsonContent = null
                    pendingPassword = null
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Confirm Database Restore",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            // 1. Backup date
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Backup date:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                                Text(valResult.exportDate.ifBlank { "Unknown" }, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                            }
                            // 2. Product count
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Product count:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                                Text("${valResult.productCount}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                            }
                            // 3. Sales count
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Sales count:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                                Text("${valResult.salesCount}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                            }
                            // 4. Purchase count
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Purchase count:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                                Text("${valResult.purchaseCount}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                            }
                            // 5. Customer count
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Customer count:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                                Text("${valResult.customersCount}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                            }
                            // 6. File size
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("File size:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                                Text(valResult.fileSizeFormatted.ifBlank { BackupManager.formatFileSize(valResult.fileSizeBytes) }, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                            }
                            // 7. Database version
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Database version:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                                Text("v${valResult.databaseVersion}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Explicit Confirmation String per user specification
                    Text(
                        text = "Restore will replace current store data.",
                        fontSize = 13.sp,
                        color = Color(0xFFDC2626),
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val content = pendingJsonContent ?: return@Button
                        val pass = pendingPassword
                        isRestoring = true
                        viewModel.restoreBackup(content, password = pass) { success, msg ->
                            isRestoring = false
                            if (success) {
                                Toast.makeText(context, "Database restored successfully!", Toast.LENGTH_LONG).show()
                                pendingRestoreResult = null
                                pendingJsonContent = null
                                pendingPassword = null
                            } else {
                                Toast.makeText(context, "Restore failed: $msg. All changes rolled back.", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    modifier = Modifier.testTag("confirm_restore_btn")
                ) {
                    Text("RESTORE", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        pendingRestoreResult = null
                        pendingJsonContent = null
                        pendingPassword = null
                    },
                    modifier = Modifier.testTag("cancel_restore_btn")
                ) {
                    Text("CANCEL")
                }
            }
        )
    }
}
