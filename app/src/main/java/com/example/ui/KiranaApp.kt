package com.example.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.model.ProductItem
import com.example.scanner.BarcodeScannerDialog
import com.example.ui.components.AddEditProductDialog
import com.example.ui.components.GitHubReleaseGuideDialog
import com.example.ui.components.ThermalReceiptDialog
import com.example.ui.screens.BackupRestoreScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.InventoryScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.PosBillingScreen
import com.example.ui.screens.PurchaseScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.StockAlertScreen
import com.example.ui.screens.TransactionsScreen
import com.example.ui.theme.GroceryOrange
import com.example.ui.theme.LowStockAlertColor
import kotlinx.coroutines.launch

sealed class KiranaScreen(val route: String, val title: String, val icon: ImageVector) {
    object Dashboard : KiranaScreen("dashboard", "Dashboard", Icons.Default.Dashboard)
    object Pos : KiranaScreen("pos", "Sales", Icons.Default.ShoppingCart)
    object Inventory : KiranaScreen("inventory", "Products", Icons.Default.Inventory2)
    object Transactions : KiranaScreen("transactions", "Bills", Icons.AutoMirrored.Filled.ReceiptLong)
    object Purchase : KiranaScreen("purchase", "Purchase", Icons.Default.ShoppingCart)
    object Reports : KiranaScreen("reports", "Reports", Icons.Default.BarChart)
    object StockAlert : KiranaScreen("stock_alert", "Stock Alert", Icons.Default.Warning)
    object Backup : KiranaScreen("backup", "Backup & Restore", Icons.Default.CloudUpload)
    object Settings : KiranaScreen("settings", "Settings", Icons.Default.Settings)
    object Login : KiranaScreen("login", "Login", Icons.Default.Lock)
    object Splash : KiranaScreen("splash", "Welcome", Icons.Default.Visibility)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KiranaApp(
    viewModel: KiranaViewModel,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    val snackbarHostState = remember { SnackbarHostState() }
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()

    val lowStockProducts by viewModel.lowStockProducts.collectAsStateWithLifecycle()
    val outOfStockProducts by viewModel.outOfStockProducts.collectAsStateWithLifecycle()
    val totalLowStockCount = lowStockProducts.size + outOfStockProducts.size

    val cartItems by viewModel.cartItems.collectAsStateWithLifecycle()
    val shopSettings by viewModel.shopSettings.collectAsStateWithLifecycle()
    val allProducts by viewModel.allProducts.collectAsStateWithLifecycle()

    // Dialog States
    var showScannerDialog by remember { mutableStateOf(false) }
    var showAddProductDialog by remember { mutableStateOf(false) }
    var editingProduct by remember { mutableStateOf<ProductItem?>(null) }
    var scannedBarcodeForAdd by remember { mutableStateOf<String?>(null) }
    var showGitHubGuideDialog by remember { mutableStateOf(false) }

    // Receipt Dialog from ViewModel
    val showReceiptDialog by viewModel.showReceiptDialog.collectAsStateWithLifecycle()
    val activeReceiptTx by viewModel.activeReceiptTransaction.collectAsStateWithLifecycle()
    val activeReceiptItems by viewModel.activeReceiptCartItems.collectAsStateWithLifecycle()

    // Listen to user messages for snackbar
    LaunchedEffect(userMessage) {
        userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
            viewModel.clearUserMessage()
        }
    }

    val context = LocalContext.current
    LaunchedEffect(currentRoute) {
        val route = currentRoute ?: return@LaunchedEffect
        if (route != KiranaScreen.Login.route && route != KiranaScreen.Splash.route) {
            if (!com.example.security.SecureAuthManager.isLoggedIn(context)) {
                try {
                    navController.navigate(KiranaScreen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                } catch (_: Exception) {}
            } else {
                com.example.security.SecureAuthManager.recordUserActivity(context)
            }
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                val currentDest = try { navController.currentDestination?.route } catch (_: Exception) { null }
                if (currentDest != null && currentDest != KiranaScreen.Login.route && currentDest != KiranaScreen.Splash.route) {
                    if (com.example.security.SecureAuthManager.checkAutoLock(context) ||
                        !com.example.security.SecureAuthManager.isLoggedIn(context)
                    ) {
                        try {
                            navController.navigate(KiranaScreen.Login.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        } catch (_: Exception) {}
                    }
                }
            } else if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                com.example.security.SecureAuthManager.recordUserActivity(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val bottomNavItems = listOf(
        KiranaScreen.Dashboard,
        KiranaScreen.Pos,
        KiranaScreen.Inventory,
        KiranaScreen.Transactions,
        KiranaScreen.Settings
    )

    val isFullscreenScreen = currentRoute == null || currentRoute == KiranaScreen.Login.route || currentRoute == KiranaScreen.Splash.route

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !isFullscreenScreen,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color.White,
                modifier = Modifier.width(300.dp)
            ) {
                // Header with Shop Info
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(GroceryOrange)
                        .padding(20.dp)
                ) {
                    Column {
                        Surface(
                            shape = CircleShape,
                            color = Color.White,
                            modifier = Modifier.size(54.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Storefront,
                                    contentDescription = null,
                                    tint = GroceryOrange,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = shopSettings.shopName,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Owner: ${shopSettings.ownerName.ifBlank { "Rahul Kumar" }}",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Navigation items inside drawer
                val drawerNavItems = listOf(
                    KiranaScreen.Dashboard,
                    KiranaScreen.Pos,
                    KiranaScreen.Inventory,
                    KiranaScreen.Purchase,
                    KiranaScreen.Transactions,
                    KiranaScreen.Reports,
                    KiranaScreen.StockAlert,
                    KiranaScreen.Backup,
                    KiranaScreen.Settings
                )

                Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                    drawerNavItems.forEach { screen ->
                        val isSelected = currentRoute == screen.route
                        NavigationDrawerItem(
                            label = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = screen.title,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 14.sp
                                    )
                                    if (screen == KiranaScreen.StockAlert && totalLowStockCount > 0) {
                                        Spacer(modifier = Modifier.weight(1f))
                                        Surface(
                                            shape = CircleShape,
                                            color = LowStockAlertColor,
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = totalLowStockCount.toString(),
                                                    color = Color.White,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = screen.icon,
                                    contentDescription = screen.title,
                                    tint = if (isSelected) GroceryOrange else Color(0xFF64748B)
                                )
                            },
                            selected = isSelected,
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = Color(0xFFFFE8DC),
                                selectedTextColor = GroceryOrange,
                                unselectedTextColor = Color(0xFF1E293B)
                            ),
                            onClick = {
                                coroutineScope.launch { drawerState.close() }
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFFE2E8F0))

                    // Logout / Lock Store Item
                    NavigationDrawerItem(
                        label = { Text("Logout / Lock Store", fontSize = 13.sp) },
                        icon = { Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFEF4444)) },
                        selected = false,
                        onClick = {
                            coroutineScope.launch { drawerState.close() }
                            val context = navController.context
                            com.example.security.SecureAuthManager.logout(context)
                            navController.navigate(KiranaScreen.Login.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    )

                    // Splash Screen Preview Item
                    NavigationDrawerItem(
                        label = { Text("View Splash Screen", fontSize = 13.sp) },
                        icon = { Icon(Icons.Default.Visibility, contentDescription = null, tint = Color(0xFF64748B)) },
                        selected = currentRoute == KiranaScreen.Splash.route,
                        onClick = {
                            coroutineScope.launch { drawerState.close() }
                            navController.navigate(KiranaScreen.Splash.route)
                        }
                    )
                }
            }
        }
    ) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                if (!isFullscreenScreen) {
                    CenterAlignedTopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (currentRoute == KiranaScreen.Dashboard.route) shopSettings.shopName else when (currentRoute) {
                                        KiranaScreen.Pos.route -> "New Sale"
                                        KiranaScreen.Inventory.route -> "Products"
                                        KiranaScreen.Purchase.route -> "Purchase Entry"
                                        KiranaScreen.Reports.route -> "Reports & Analytics"
                                        KiranaScreen.StockAlert.route -> "Stock Alert"
                                        KiranaScreen.Backup.route -> "Backup & Restore"
                                        KiranaScreen.Transactions.route -> "Bills History"
                                        KiranaScreen.Settings.route -> "Shop Settings"
                                        else -> shopSettings.shopName
                                    },
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        },
                        navigationIcon = {
                            if (currentRoute == KiranaScreen.Dashboard.route) {
                                IconButton(
                                    onClick = {
                                        coroutineScope.launch { drawerState.open() }
                                    },
                                    modifier = Modifier.testTag("topbar_drawer_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Menu,
                                        contentDescription = "Open Drawer",
                                        tint = Color.White
                                    )
                                }
                            } else {
                                IconButton(
                                    onClick = {
                                        navController.navigate(KiranaScreen.Dashboard.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                        }
                                    },
                                    modifier = Modifier.testTag("topbar_back_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = Color.White
                                    )
                                }
                            }
                        },
                        actions = {
                            // Barcode Scanner Top Action
                            IconButton(
                                onClick = { showScannerDialog = true },
                                modifier = Modifier.testTag("topbar_scanner_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QrCodeScanner,
                                    contentDescription = "Scan Barcode",
                                    tint = Color.White
                                )
                            }

                            // Notification Bell (Stock Alert) with Red Badge
                            IconButton(
                                onClick = {
                                    navController.navigate(KiranaScreen.StockAlert.route)
                                },
                                modifier = Modifier.testTag("topbar_notification_bell")
                            ) {
                                BadgedBox(
                                    badge = {
                                        if (totalLowStockCount > 0) {
                                            Badge(
                                                containerColor = Color(0xFFEF4444),
                                                contentColor = Color.White
                                            ) {
                                                Text(totalLowStockCount.toString(), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Notifications,
                                        contentDescription = "Stock Alert Notifications",
                                        tint = Color.White
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = GroceryOrange,
                            titleContentColor = Color.White
                        )
                    )
                }
            },
            bottomBar = {
                if (!isFullscreenScreen) {
                    NavigationBar(
                        containerColor = Color.White,
                        tonalElevation = 8.dp,
                        modifier = Modifier.testTag("bottom_nav_bar")
                    ) {
                        bottomNavItems.forEach { screen ->
                            val isSelected = currentRoute == screen.route
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    if (screen == KiranaScreen.Pos && cartItems.isNotEmpty()) {
                                        BadgedBox(
                                            badge = {
                                                Badge(
                                                    containerColor = GroceryOrange,
                                                    contentColor = Color.White
                                                ) {
                                                    Text(cartItems.size.toString())
                                                }
                                            }
                                        ) {
                                            Icon(imageVector = screen.icon, contentDescription = screen.title)
                                        }
                                    } else if (screen == KiranaScreen.Inventory && totalLowStockCount > 0) {
                                        BadgedBox(
                                            badge = {
                                                Badge(containerColor = LowStockAlertColor) {
                                                    Text(totalLowStockCount.toString())
                                                }
                                            }
                                        ) {
                                            Icon(imageVector = screen.icon, contentDescription = screen.title)
                                        }
                                    } else {
                                        Icon(imageVector = screen.icon, contentDescription = screen.title)
                                    }
                                },
                                label = {
                                    Text(
                                        text = screen.title,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = GroceryOrange,
                                    selectedTextColor = GroceryOrange,
                                    indicatorColor = Color(0xFFFFE8DC),
                                    unselectedIconColor = Color(0xFF64748B),
                                    unselectedTextColor = Color(0xFF64748B)
                                ),
                                modifier = Modifier.testTag("nav_item_${screen.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = KiranaScreen.Splash.route,
                modifier = Modifier.padding(innerPadding)
            ) {
                // 1. Splash Screen
                composable(KiranaScreen.Splash.route) {
                    SplashScreen(
                        onNavigateToDashboard = {
                            navController.navigate(KiranaScreen.Dashboard.route) {
                                popUpTo(KiranaScreen.Splash.route) { inclusive = true }
                            }
                        },
                        onNavigateToLogin = {
                            navController.navigate(KiranaScreen.Login.route) {
                                popUpTo(KiranaScreen.Splash.route) { inclusive = true }
                            }
                        }
                    )
                }

                // 2. Login Screen
                composable(KiranaScreen.Login.route) {
                    LoginScreen(
                        onLoginSuccess = {
                            navController.navigate(KiranaScreen.Dashboard.route) {
                                popUpTo(KiranaScreen.Login.route) { inclusive = true }
                            }
                        }
                    )
                }

                // 3. Dashboard Screen (Mockup #3)
                composable(KiranaScreen.Dashboard.route) {
                    DashboardScreen(
                        viewModel = viewModel,
                        onNavigateToPos = {
                            navController.navigate(KiranaScreen.Pos.route)
                        },
                        onNavigateToInventory = { filter ->
                            viewModel.stockFilter.value = filter
                            navController.navigate(KiranaScreen.Inventory.route)
                        },
                        onOpenAddProduct = {
                            editingProduct = null
                            scannedBarcodeForAdd = null
                            showAddProductDialog = true
                        },
                        onOpenScanner = {
                            showScannerDialog = true
                        },
                        onOpenPurchase = {
                            navController.navigate(KiranaScreen.Purchase.route)
                        },
                        onOpenReports = {
                            navController.navigate(KiranaScreen.Reports.route)
                        },
                        onOpenBackup = {
                            navController.navigate(KiranaScreen.Backup.route)
                        },
                        onOpenStockAlert = {
                            navController.navigate(KiranaScreen.StockAlert.route)
                        },
                        onOpenGitHubReleaseGuide = {
                            showGitHubGuideDialog = true
                        }
                    )
                }

                // 4. POS / Sales Screen
                composable(KiranaScreen.Pos.route) {
                    PosBillingScreen(
                        viewModel = viewModel,
                        onOpenScanner = {
                            showScannerDialog = true
                        }
                    )
                }

                // 5. Products / Inventory Screen (Mockup #4)
                composable(KiranaScreen.Inventory.route) {
                    InventoryScreen(
                        viewModel = viewModel,
                        onOpenAddProduct = { product ->
                            editingProduct = product
                            scannedBarcodeForAdd = null
                            showAddProductDialog = true
                        },
                        onOpenScanner = {
                            showScannerDialog = true
                        }
                    )
                }

                // 6. Purchase Entry Screen
                composable(KiranaScreen.Purchase.route) {
                    PurchaseScreen(
                        viewModel = viewModel,
                        onNavigateBack = {
                            navController.navigateUp()
                        }
                    )
                }

                // 7. Reports & Analytics Screen
                composable(KiranaScreen.Reports.route) {
                    ReportsScreen(
                        viewModel = viewModel,
                        onNavigateBack = {
                            navController.navigateUp()
                        }
                    )
                }

                // 8. Stock Alert Screen (Mockup #5)
                composable(KiranaScreen.StockAlert.route) {
                    StockAlertScreen(
                        viewModel = viewModel,
                        onNavigateBack = {
                            navController.navigateUp()
                        },
                        onEditProduct = { product ->
                            editingProduct = product
                            scannedBarcodeForAdd = null
                            showAddProductDialog = true
                        }
                    )
                }

                // 9. Backup & Restore Screen (Mockup #6)
                composable(KiranaScreen.Backup.route) {
                    BackupRestoreScreen(
                        viewModel = viewModel,
                        onNavigateBack = {
                            navController.navigateUp()
                        }
                    )
                }

                // 10. Transactions / Bills Screen
                composable(KiranaScreen.Transactions.route) {
                    TransactionsScreen(
                        viewModel = viewModel
                    )
                }

                // 11. Shop Settings Screen
                composable(KiranaScreen.Settings.route) {
                    SettingsScreen(
                        viewModel = viewModel,
                        onNavigateToBackup = {
                            navController.navigate(KiranaScreen.Backup.route)
                        },
                        onOpenGitHubReleaseGuide = {
                            showGitHubGuideDialog = true
                        },
                        onLockStore = {
                            navController.navigate(KiranaScreen.Login.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    )
                }
            }
        }
    }

    // Camera Barcode Scanner Dialog
    if (showScannerDialog) {
        BarcodeScannerDialog(
            inventoryProducts = allProducts,
            onDismiss = { showScannerDialog = false },
            onBarcodeDetected = { barcode ->
                showScannerDialog = false
                if (currentRoute == KiranaScreen.Pos.route) {
                    viewModel.handleScannedBarcodeInPos(barcode)
                } else {
                    // Pre-fill barcode in add product dialog
                    editingProduct = null
                    scannedBarcodeForAdd = barcode
                    showAddProductDialog = true
                }
            }
        )
    }

    // Add / Edit Product Dialog
    if (showAddProductDialog) {
        AddEditProductDialog(
            initialProduct = editingProduct,
            existingProducts = allProducts,
            scannedBarcode = scannedBarcodeForAdd,
            onDismiss = {
                showAddProductDialog = false
                editingProduct = null
                scannedBarcodeForAdd = null
            },
            onSave = { product ->
                viewModel.saveProduct(product)
            },
            onOpenScanner = {
                showScannerDialog = true
            }
        )
    }

    // Thermal Receipt Dialog Preview & Bluetooth Print
    if (showReceiptDialog && activeReceiptTx != null) {
        ThermalReceiptDialog(
            transaction = activeReceiptTx!!,
            items = activeReceiptItems,
            settings = shopSettings,
            onDismiss = {
                viewModel.showReceiptDialog.value = false
            }
        )
    }

    // GitHub Release & Automatic APK Download Guide Dialog
    if (showGitHubGuideDialog) {
        GitHubReleaseGuideDialog(
            onDismiss = { showGitHubGuideDialog = false }
        )
    }
}
