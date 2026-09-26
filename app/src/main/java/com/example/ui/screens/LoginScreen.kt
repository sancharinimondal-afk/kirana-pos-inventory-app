package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.security.SecureAuthManager
import com.example.ui.theme.GroceryNavy
import com.example.ui.theme.GroceryOrange

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isConfigured = remember { SecureAuthManager.isConfigured(context) }
    var isLocked by remember { mutableStateOf(SecureAuthManager.isLocked()) }

    // Mode: If already configured, show login; if not configured, show initial setup
    var isSetupMode by remember { mutableStateOf(!isConfigured) }

    // Setup fields
    var setupOwnerName by remember { mutableStateOf("") }
    var setupUsername by remember { mutableStateOf("") }
    var setupPassword by remember { mutableStateOf("") }
    var setupConfirmPassword by remember { mutableStateOf("") }

    // Login fields (no hardcoded credentials)
    var loginUsername by remember {
        mutableStateOf(if (isConfigured) SecureAuthManager.getUsername(context) else "")
    }
    var loginPassword by remember { mutableStateOf("") }

    var rememberMe by remember {
        mutableStateOf(SecureAuthManager.isRememberMeEnabled(context) || !isConfigured)
    }
    var showPassword by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .testTag("login_screen")
    ) {
        // Decorative top curved banner
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
        ) {
            val width = size.width
            val height = size.height
            val path = Path().apply {
                moveTo(0f, 0f)
                lineTo(width, 0f)
                lineTo(width, height * 0.75f)
                cubicTo(width * 0.75f, height, width * 0.25f, height * 0.65f, 0f, height * 0.95f)
                close()
            }
            drawPath(path, color = GroceryOrange)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // Brand Logo & Title Card
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color.White,
                shadowElevation = 4.dp,
                modifier = Modifier.size(76.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(10.dp)) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_grocery_cart_logo),
                        contentDescription = "Logo",
                        modifier = Modifier.size(56.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Grocery ",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = GroceryNavy
                )
                Text(
                    text = "Shop",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = GroceryOrange
                )
            }

            Text(
                text = "Store Management System",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Main Authentication Card
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    // Header of Card
                    Text(
                        text = when {
                            isSetupMode -> "Owner Account Setup"
                            isLocked -> "Store Locked"
                            else -> "Store Owner Login"
                        },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = GroceryNavy
                    )
                    Text(
                        text = when {
                            isSetupMode -> "Create your credentials on this device to protect your store."
                            isLocked -> "Enter your security PIN or password to unlock your shop."
                            else -> "Enter your username and PIN/password to access your shop."
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(top = 2.dp, bottom = 14.dp)
                    )

                    // Error Message Banner if any
                    errorMessage?.let { err ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            Text(
                                text = err,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }

                    if (isSetupMode) {
                        // SETUP FORM (First Launch)
                        Text(
                            text = "Shop Owner Name",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = GroceryNavy
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = setupOwnerName,
                            onValueChange = {
                                setupOwnerName = it
                                errorMessage = null
                            },
                            placeholder = { Text("e.g. Ramesh Kumar", fontSize = 13.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.Badge, contentDescription = null, tint = GroceryOrange)
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("setup_owner_name_input")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Username or Mobile Number",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = GroceryNavy
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = setupUsername,
                            onValueChange = {
                                setupUsername = it
                                errorMessage = null
                            },
                            placeholder = { Text("e.g. 9876543210 or storeowner", fontSize = 13.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.Person, contentDescription = null, tint = GroceryOrange)
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("setup_username_input")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Set Security PIN / Password (min 4 chars)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = GroceryNavy
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = setupPassword,
                            onValueChange = {
                                setupPassword = it
                                errorMessage = null
                            },
                            placeholder = { Text("Enter 4-digit PIN or password", fontSize = 13.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = GroceryOrange)
                            },
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        imageVector = if (showPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = "Toggle password",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("setup_password_input")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Confirm PIN / Password",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = GroceryNavy
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = setupConfirmPassword,
                            onValueChange = {
                                setupConfirmPassword = it
                                errorMessage = null
                            },
                            placeholder = { Text("Re-enter PIN or password", fontSize = 13.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.Security, contentDescription = null, tint = GroceryOrange)
                            },
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("setup_confirm_password_input")
                        )
                    } else {
                        // LOGIN FORM (Normal Login)
                        Text(
                            text = "Username or Mobile Number",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = GroceryNavy
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = loginUsername,
                            onValueChange = {
                                loginUsername = it
                                errorMessage = null
                            },
                            placeholder = { Text("Enter username or mobile no", fontSize = 13.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.Person, contentDescription = null, tint = GroceryOrange)
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("login_username_input")
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Security PIN / Password",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = GroceryNavy
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = loginPassword,
                            onValueChange = {
                                loginPassword = it
                                errorMessage = null
                            },
                            placeholder = { Text("Enter PIN or password", fontSize = 13.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = GroceryOrange)
                            },
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        imageVector = if (showPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = "Toggle password",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("login_password_input")
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Remember Me Checkbox
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { rememberMe = !rememberMe }
                        ) {
                            Checkbox(
                                checked = rememberMe,
                                onCheckedChange = { rememberMe = it },
                                colors = CheckboxDefaults.colors(checkedColor = GroceryOrange)
                            )
                            Text("Keep me signed in", fontSize = 12.sp, color = GroceryNavy)
                        }

                        if (!isSetupMode) {
                            Text(
                                text = "Reset Credentials",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = GroceryOrange,
                                modifier = Modifier.clickable {
                                    isSetupMode = true
                                    errorMessage = null
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Submit Button
                    Button(
                        onClick = {
                            if (isSetupMode) {
                                // Validate setup
                                if (setupOwnerName.isBlank()) {
                                    errorMessage = "Please enter shop owner name"
                                    return@Button
                                }
                                if (setupUsername.isBlank()) {
                                    errorMessage = "Please enter a username or mobile number"
                                    return@Button
                                }
                                if (setupPassword.length < 4) {
                                    errorMessage = "PIN or password must be at least 4 characters"
                                    return@Button
                                }
                                if (setupPassword != setupConfirmPassword) {
                                    errorMessage = "PIN / passwords do not match"
                                    return@Button
                                }

                                val success = SecureAuthManager.setupOwner(
                                    context = context,
                                    ownerName = setupOwnerName,
                                    username = setupUsername,
                                    pinOrPassword = setupPassword,
                                    rememberMe = rememberMe
                                )
                                if (success) {
                                    Toast.makeText(context, "Welcome, $setupOwnerName! Store initialized.", Toast.LENGTH_SHORT).show()
                                    onLoginSuccess()
                                } else {
                                    errorMessage = "Failed to save credentials"
                                }
                            } else if (isLocked) {
                                // Unlock locked store
                                if (loginPassword.isBlank()) {
                                    errorMessage = "Please enter your PIN or password"
                                    return@Button
                                }
                                val unlocked = SecureAuthManager.unlockWithPassword(context, loginPassword) ||
                                        (loginUsername.isNotBlank() && SecureAuthManager.authenticate(context, loginUsername, loginPassword, rememberMe))
                                if (unlocked) {
                                    isLocked = false
                                    Toast.makeText(context, "Store unlocked", Toast.LENGTH_SHORT).show()
                                    onLoginSuccess()
                                } else {
                                    errorMessage = "Invalid PIN or password. Please try again."
                                }
                            } else {
                                // Validate login
                                if (loginUsername.isBlank()) {
                                    errorMessage = "Please enter your username or mobile number"
                                    return@Button
                                }
                                if (loginPassword.isBlank()) {
                                    errorMessage = "Please enter your PIN or password"
                                    return@Button
                                }

                                val authenticated = SecureAuthManager.authenticate(
                                    context = context,
                                    username = loginUsername,
                                    pinOrPassword = loginPassword,
                                    rememberMe = rememberMe
                                )
                                if (authenticated) {
                                    isLocked = false
                                    Toast.makeText(context, "Login successful", Toast.LENGTH_SHORT).show()
                                    onLoginSuccess()
                                } else {
                                    errorMessage = "Invalid username or password/PIN. Please try again."
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GroceryOrange),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("login_submit_btn")
                    ) {
                        Text(
                            text = when {
                                isSetupMode -> "Create Account & Enter Shop"
                                isLocked -> "Unlock Store"
                                else -> "Login to Dashboard"
                            },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    if (isSetupMode && isConfigured) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Back to Login",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .clickable {
                                    isSetupMode = false
                                    errorMessage = null
                                }
                                .padding(4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Local Security Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF16A34A),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "100% Offline & Encrypted Storage",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
