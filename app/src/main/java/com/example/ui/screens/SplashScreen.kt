package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.GroceryNavy
import com.example.ui.theme.GroceryOrange
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onNavigateToDashboard: () -> Unit,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(Unit) {
        delay(1200)
        val isConfigured = com.example.security.SecureAuthManager.isConfigured(context)
        val isLoggedIn = com.example.security.SecureAuthManager.isLoggedIn(context)
        if (isConfigured && isLoggedIn) {
            onNavigateToDashboard()
        } else {
            onNavigateToLogin()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(GroceryNavy)
            .testTag("splash_screen")
    ) {
        // Bottom Orange Curved Wave
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val path = Path().apply {
                moveTo(0f, height * 0.65f)
                cubicTo(
                    width * 0.25f, height * 0.58f,
                    width * 0.75f, height * 0.68f,
                    width, height * 0.60f
                )
                lineTo(width, height)
                lineTo(0f, height)
                close()
            }
            drawPath(
                path = path,
                color = GroceryOrange
            )
        }

        // Top & Center Branding Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // Logo & Title Section
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .padding(bottom = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_grocery_cart_logo),
                        contentDescription = "Grocery Shop Logo",
                        modifier = Modifier.size(96.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Grocery ",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Shop",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = GroceryOrange
                    )
                }

                Text(
                    text = "Store Management",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF94A3B8),
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "Manage Your Store\nEasily & Smartly",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center,
                    lineHeight = 26.sp
                )
            }

            // Fresh Groceries Emoji Collage
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 20.dp)
            ) {
                Text("🥦 🍅 🍞 🧀 🥛 🍇 🌽 🧃", fontSize = 26.sp)
            }

            // Bottom Wave Section: Loading indicator & Button
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 42.dp)
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(36.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Loading...",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        val isConfigured = com.example.security.SecureAuthManager.isConfigured(context)
                        val isLoggedIn = com.example.security.SecureAuthManager.isLoggedIn(context)
                        if (isConfigured && isLoggedIn) {
                            onNavigateToDashboard()
                        } else {
                            onNavigateToLogin()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = GroceryOrange
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("splash_continue_btn")
                ) {
                    Text("Enter Store", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
