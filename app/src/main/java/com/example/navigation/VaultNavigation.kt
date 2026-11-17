package com.example.navigation

import android.content.Context
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.auth.authStateFlow
import com.example.data.FamilyVaultRepository
import com.example.data.VaultRepository
import com.example.model.ThemePreference
import com.example.model.UiState
import com.example.notifications.VaultReminderScheduler
import com.example.ui.components.GlassVaultCard
import com.example.ui.components.LocalAnimatedVisibilityScope
import com.example.ui.components.LocalSharedTransitionScope
import com.example.ui.components.VaultAtmosphereBackground
import com.example.ui.components.VaultGradientButton
import com.example.ui.components.VaultoraLogoBadge
import com.example.ui.screens.auth.LoginScreen
import com.example.ui.screens.auth.OnboardingScreen
import com.example.ui.screens.auth.SignUpScreen
import com.example.ui.screens.auth.SplashScreen
import com.example.ui.screens.dashboard.HomeDashboardScreen
import com.example.ui.screens.family.FamilyVaultScreen
import com.example.ui.screens.medicine.AddEditMedicineScreen
import com.example.ui.screens.medicine.MedicineDetailsScreen
import com.example.ui.screens.medicine.MedicineVaultDashboardScreen
import com.example.ui.screens.purchase.AddScanReceiptScreen
import com.example.ui.screens.purchase.AllReceiptsScreen
import com.example.ui.screens.purchase.PurchaseVaultDashboardScreen
import com.example.ui.screens.purchase.ReceiptDetailsScreen
import com.example.ui.screens.purchase.ReturnWarrantyTrackerScreen
import com.example.ui.screens.purchase.WarrantyClaimAssistantScreen
import com.example.ui.screens.shared.GlobalSearchScreen
import com.example.ui.screens.shared.NotificationCenterScreen
import com.example.ui.screens.shared.PriorityCenterScreen
import com.example.ui.screens.shared.ProfileScreen
import com.example.ui.screens.shared.SettingsScreen
import com.example.ui.theme.LocalVaultColors
import com.example.ui.theme.VaultoraTheme
import com.example.util.NetworkMonitor
import com.example.viewmodel.FamilyVaultViewModel
import com.example.viewmodel.VaultViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

private const val PREFS_NAME = "vaultora_prefs"
private const val KEY_HAS_SEEN_ONBOARDING = "has_seen_onboarding"

object VaultRoutes {
    const val ONBOARDING = "onboarding"
    const val LOGIN = "login"
    const val SIGNUP = "signup"

    const val HOME = "home"
    const val PURCHASE_VAULT = "purchase_vault"
    const val MEDICINE_VAULT = "medicine_vault"
    const val SEARCH = "search"
    const val SETTINGS = "settings"

    const val ALL_RECEIPTS = "all_receipts"
    const val ADD_SCAN_RECEIPT = "add_scan_receipt?editId={editId}&scan={scan}"
    const val PURCHASE_DETAIL = "purchase_detail/{purchaseId}"
    const val TRACKER = "tracker?tab={tab}"
    const val WARRANTY_CLAIM = "warranty_claim/{purchaseId}"
    const val ADD_EDIT_MEDICINE = "add_edit_medicine?editId={editId}"
    const val MEDICINE_DETAIL = "medicine_detail/{medicineId}"
    const val NOTIFICATIONS = "notifications"
    const val PRIORITY_CENTER = "priority_center"
    const val FAMILY_VAULT = "family_vault"
    const val PROFILE = "profile"

    fun addScanReceipt(editId: String? = null, autoScan: Boolean = false): String {
        val encodedId = Uri.encode(editId.orEmpty())
        return "add_scan_receipt?editId=$encodedId&scan=$autoScan"
    }

    fun purchaseDetail(purchaseId: String): String =
        "purchase_detail/${Uri.encode(purchaseId)}"

    fun tracker(tab: Int = 0): String =
        "tracker?tab=${tab.coerceIn(0, 1)}"

    fun warrantyClaim(purchaseId: String): String =
        "warranty_claim/${Uri.encode(purchaseId)}"

    fun addEditMedicine(editId: String? = null): String =
        "add_edit_medicine?editId=${Uri.encode(editId.orEmpty())}"

    fun medicineDetail(medicineId: String): String =
        "medicine_detail/${Uri.encode(medicineId)}"
}

private enum class TopLevelTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME(VaultRoutes.HOME, "Home", Icons.Filled.Home, Icons.Outlined.Home),
    PURCHASES(VaultRoutes.PURCHASE_VAULT, "Purchases", Icons.Filled.ReceiptLong, Icons.Outlined.ReceiptLong),
    MEDICINES(VaultRoutes.MEDICINE_VAULT, "Medicines", Icons.Filled.Medication, Icons.Outlined.Medication),
    SEARCH(VaultRoutes.SEARCH, "Search", Icons.Filled.Search, Icons.Outlined.Search),
    SETTINGS(VaultRoutes.SETTINGS, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

@Composable
fun VaultoraApp(
    pendingNavTarget: String? = null,
    onConsumeNavTarget: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    var hasSeenOnboarding by remember {
        mutableStateOf(prefs.getBoolean(KEY_HAS_SEEN_ONBOARDING, false))
    }
    var hasCompletedSplash by rememberSaveable { mutableStateOf(false) }
    var startAuthRoute by rememberSaveable {
        mutableStateOf(if (hasSeenOnboarding) VaultRoutes.LOGIN else VaultRoutes.ONBOARDING)
    }

    val currentUser by remember {
        Firebase.auth.authStateFlow()
    }.collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    if (!hasCompletedSplash) {
        VaultoraTheme(themePreference = ThemePreference.DARK) {
            SplashScreen(
                hasSeenOnboarding = hasSeenOnboarding,
                onAuthenticated = {
                    hasCompletedSplash = true
                },
                onGoOnboarding = {
                    startAuthRoute = VaultRoutes.ONBOARDING
                    hasCompletedSplash = true
                },
                onGoLogin = {
                    startAuthRoute = VaultRoutes.LOGIN
                    hasCompletedSplash = true
                }
            )
        }
        return
    }

    val signedInUser = currentUser
    if (signedInUser == null) {
        VaultoraTheme(themePreference = ThemePreference.DARK) {
            UnauthenticatedVaultNavHost(
                startRoute = startAuthRoute,
                onOnboardingCompleted = {
                    prefs.edit().putBoolean(KEY_HAS_SEEN_ONBOARDING, true).apply()
                    hasSeenOnboarding = true
                    startAuthRoute = VaultRoutes.LOGIN
                },
                onAuthSuccess = {
                    prefs.edit().putBoolean(KEY_HAS_SEEN_ONBOARDING, true).apply()
                    hasSeenOnboarding = true
                    startAuthRoute = VaultRoutes.LOGIN
                }
            )
        }
    } else {
        key(signedInUser.uid) {
            val appContext = context.applicationContext
            val vaultViewModel: VaultViewModel = viewModel(
                key = signedInUser.uid,
                factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        val monitor = NetworkMonitor(appContext)
                        return VaultViewModel(
                            repository = VaultRepository(appContext, monitor),
                            currentUserId = signedInUser.uid,
                            networkMonitor = monitor
                        ) as T
                    }
                }
            )
            val familyViewModel: FamilyVaultViewModel = viewModel(
                key = "family_${signedInUser.uid}",
                factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        val monitor = NetworkMonitor(appContext)
                        return FamilyVaultViewModel(
                            repository = FamilyVaultRepository(appContext, monitor),
                            currentUserId = signedInUser.uid,
                            networkMonitor = monitor
                        ) as T
                    }
                }
            )

            val profileState by vaultViewModel.userProfileState.collectAsStateWithLifecycle()
            val profile = (profileState as? UiState.Success)?.data
            val themePreference = profile?.resolvedTheme() ?: ThemePreference.DARK

            var sessionUnlocked by rememberSaveable(signedInUser.uid) { mutableStateOf(false) }
            val requireShield = (profile?.appLockEnabled == true) && !sessionUnlocked

            VaultoraTheme(themePreference = themePreference) {
                if (requireShield) {
                    VaultSecurityShieldOverlay(
                        userName = profile?.fullName.orEmpty(),
                        onUnlock = { sessionUnlocked = true }
                    )
                } else {
                    AuthenticatedVaultShell(
                        viewModel = vaultViewModel,
                        familyViewModel = familyViewModel,
                        pendingNavTarget = pendingNavTarget,
                        onConsumeNavTarget = onConsumeNavTarget,
                        onSignedOut = {
                            startAuthRoute = VaultRoutes.LOGIN
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun UnauthenticatedVaultNavHost(
    startRoute: String,
    onOnboardingCompleted: () -> Unit,
    onAuthSuccess: () -> Unit
) {
    val navController = rememberNavController()
    val focusManager = LocalFocusManager.current
    val textToolbar = LocalTextToolbar.current
    val unauthBackStackEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(unauthBackStackEntry?.id) {
        textToolbar.hide()
        focusManager.clearFocus(force = true)
    }
    NavHost(
        navController = navController,
        startDestination = startRoute
    ) {
        composable(VaultRoutes.ONBOARDING) {
            OnboardingScreen(
                onComplete = {
                    onOnboardingCompleted()
                    navController.navigate(VaultRoutes.LOGIN) {
                        popUpTo(VaultRoutes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }
        composable(VaultRoutes.LOGIN) {
            LoginScreen(
                onAuthSuccess = onAuthSuccess,
                onNavigateToSignUp = {
                    navController.navigate(VaultRoutes.SIGNUP)
                }
            )
        }
        composable(VaultRoutes.SIGNUP) {
            SignUpScreen(
                onAuthSuccess = onAuthSuccess,
                onNavigateToLogin = {
                    navController.popBackStack()
                }
            )
        }
    }
}

@Composable
private fun AuthenticatedVaultShell(
    viewModel: VaultViewModel,
    familyViewModel: FamilyVaultViewModel,
    pendingNavTarget: String?,
    onConsumeNavTarget: () -> Unit,
    onSignedOut: () -> Unit
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()
    val familyStatusMessage by familyViewModel.statusMessage.collectAsStateWithLifecycle()
    val profileState by viewModel.userProfileState.collectAsStateWithLifecycle()
    val purchasesState by viewModel.purchasesState.collectAsStateWithLifecycle()
    val medicinesState by viewModel.medicinesState.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val pendingSyncCount by viewModel.pendingSyncCount.collectAsStateWithLifecycle()

    LaunchedEffect(profileState, purchasesState, medicinesState) {
        val pSuccess = purchasesState as? UiState.Success ?: return@LaunchedEffect
        val mSuccess = medicinesState as? UiState.Success ?: return@LaunchedEffect
        val profile = (profileState as? UiState.Success)?.data
        VaultReminderScheduler.syncTrackedExpiriesAndScheduleDailyCheck(
            context = context,
            purchases = pSuccess.data,
            medicines = mSuccess.data,
            profile = profile
        )
        VaultReminderScheduler.checkAndNotifyExpiringSoon(
            context = context,
            purchases = pSuccess.data,
            medicines = mSuccess.data,
            profile = profile,
            forceAlert = false
        )
    }

    LaunchedEffect(actionMessage) {
        val msg = actionMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearActionMessage()
        }
    }

    LaunchedEffect(familyStatusMessage) {
        val msg = familyStatusMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            familyViewModel.clearStatusMessage()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPriorityEvaluations()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(pendingNavTarget) {
        val target = pendingNavTarget?.trim().orEmpty()
        if (target.isNotEmpty()) {
            when {
                target.startsWith("purchase:") -> {
                    val id = target.removePrefix("purchase:")
                    if (id.isNotBlank()) {
                        navController.navigate(VaultRoutes.purchaseDetail(id))
                    }
                }
                target.startsWith("medicine:") -> {
                    val id = target.removePrefix("medicine:")
                    if (id.isNotBlank()) {
                        navController.navigate(VaultRoutes.medicineDetail(id))
                    }
                }
                target == "notifications" -> {
                    navController.navigate(VaultRoutes.NOTIFICATIONS)
                }
                target == "priority_center" -> {
                    navController.navigate(VaultRoutes.PRIORITY_CENTER)
                }
            }
            onConsumeNavTarget()
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val focusManager = LocalFocusManager.current
    val textToolbar = LocalTextToolbar.current
    LaunchedEffect(navBackStackEntry?.id) {
        textToolbar.hide()
        focusManager.clearFocus(force = true)
    }
    val currentRoute = navBackStackEntry?.destination?.route
    val topLevelRoutes = remember { TopLevelTab.entries.map { it.route }.toSet() }
    val showTopLevelChrome = currentRoute in topLevelRoutes

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = if (showTopLevelChrome) {
                WindowInsets(0, 0, 0, 0)
            } else {
                WindowInsets(0, 0, 0, 0)
            },
            bottomBar = {
                if (showTopLevelChrome && !useNavigationRail) {
                    VaultBottomNavigationBar(
                        currentRoute = currentRoute,
                        onSelectTab = { route -> navigateToTopTab(navController, route) }
                    )
                }
            }
        ) { innerPadding ->
            val bottomBarInset = if (showTopLevelChrome && !useNavigationRail) {
                innerPadding.calculateBottomPadding()
            } else {
                0.dp
            }
            Row(
                modifier = Modifier.fillMaxSize()
            ) {
                if (showTopLevelChrome && useNavigationRail) {
                    VaultSideNavigationRail(
                        currentRoute = currentRoute,
                        onSelectTab = { route -> navigateToTopTab(navController, route) }
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    AnimatedVisibility(
                        visible = !isOnline || pendingSyncCount > 0,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        OfflineSyncStatusBanner(
                            isOnline = isOnline,
                            pendingSyncCount = pendingSyncCount
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        VaultAuthenticatedNavHost(
                            navController = navController,
                            viewModel = viewModel,
                            familyViewModel = familyViewModel,
                            applyTopStatusPadding = showTopLevelChrome && isOnline && pendingSyncCount == 0,
                            bottomContentPadding = bottomBarInset,
                            topLevelRoutes = topLevelRoutes,
                            onSignedOut = onSignedOut
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OfflineSyncStatusBanner(
    isOnline: Boolean,
    pendingSyncCount: Int
) {
    val vaultColors = LocalVaultColors.current
    val accent = if (isOnline) vaultColors.statusValid else vaultColors.statusWarning
    val statusText = when {
        !isOnline && pendingSyncCount > 0 ->
            "Offline Mode • $pendingSyncCount change(s) saved on device, auto-syncs online"
        !isOnline ->
            "Offline Mode • Vault cached locally; changes auto-sync when online"
        else ->
            "Back online • Syncing $pendingSyncCount change(s) to server..."
    }
    Surface(
        color = accent.copy(alpha = if (vaultColors.isDark) 0.18f else 0.14f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .testTag("offline_sync_status_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (isOnline) Icons.Outlined.CloudSync else Icons.Outlined.CloudOff,
                contentDescription = if (isOnline) "Syncing" else "Offline",
                tint = accent,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun navigateToTopTab(navController: NavHostController, route: String) {
    navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun VaultBottomNavigationBar(
    currentRoute: String?,
    onSelectTab: (String) -> Unit
) {
    val vaultColors = LocalVaultColors.current
    val shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(shape)
    ) {
        // Blurred frosted glass refraction backdrop
        Box(
            modifier = Modifier
                .matchParentSize()
                .blur(radius = 22.dp, edgeTreatment = BlurredEdgeTreatment.Rectangle)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            vaultColors.glassSheenStart,
                            vaultColors.glassBarSurface.copy(alpha = if (vaultColors.isDark) 0.72f else 0.78f),
                            vaultColors.glassSheenEnd
                        )
                    )
                )
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                vaultColors.purchaseAccent.copy(alpha = if (vaultColors.isDark) 0.24f else 0.14f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.24f, size.height * 0.2f),
                            radius = size.width * 0.45f
                        )
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                vaultColors.medicineAccent.copy(alpha = if (vaultColors.isDark) 0.20f else 0.12f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.76f, size.height * 0.2f),
                            radius = size.width * 0.45f
                        )
                    )
                }
        )

        // Translucent tint + top specular hairline border
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    vaultColors.glassBarSurface.copy(alpha = if (vaultColors.isDark) 0.68f else 0.76f)
                )
                .border(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            vaultColors.glassHighlightBorder,
                            vaultColors.glassBorder,
                            vaultColors.glassHighlightBorder.copy(alpha = 0.7f)
                        )
                    ),
                    shape = shape
                )
        )

        NavigationBar(
            containerColor = Color.Transparent,
            tonalElevation = 0.dp
        ) {
            TopLevelTab.entries.forEach { tab ->
                val selected = currentRoute == tab.route
                val accent = tabAccentColor(tab)
                NavigationBarItem(
                    selected = selected,
                    onClick = { onSelectTab(tab.route) },
                    icon = {
                        Icon(
                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                            contentDescription = tab.label
                        )
                    },
                    label = { Text(tab.label, maxLines = 1) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = accent,
                        selectedTextColor = accent,
                        indicatorColor = accent.copy(alpha = if (vaultColors.isDark) 0.24f else 0.18f),
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.testTag("nav_tab_${tab.route}")
                )
            }
        }
    }
}

@Composable
private fun VaultSideNavigationRail(
    currentRoute: String?,
    onSelectTab: (String) -> Unit
) {
    val vaultColors = LocalVaultColors.current
    Row(modifier = Modifier.fillMaxHeight()) {
        Box(modifier = Modifier.fillMaxHeight().width(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .blur(radius = 22.dp, edgeTreatment = BlurredEdgeTreatment.Rectangle)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                vaultColors.glassSheenStart,
                                vaultColors.glassBarSurface.copy(alpha = if (vaultColors.isDark) 0.78f else 0.84f),
                                vaultColors.glassSheenEnd
                            )
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(vaultColors.glassBarSurface.copy(alpha = if (vaultColors.isDark) 0.72f else 0.80f))
            )
            NavigationRail(
                containerColor = Color.Transparent,
                header = {
                    Spacer(Modifier.height(12.dp))
                    VaultoraLogoBadge(size = 40.dp)
                    Spacer(Modifier.height(12.dp))
                }
            ) {
                TopLevelTab.entries.forEach { tab ->
                    val selected = currentRoute == tab.route
                    val accent = tabAccentColor(tab)
                    NavigationRailItem(
                        selected = selected,
                        onClick = { onSelectTab(tab.route) },
                        icon = {
                            Icon(
                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.label
                            )
                        },
                        label = { Text(tab.label, maxLines = 1) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = accent,
                            selectedTextColor = accent,
                            indicatorColor = accent.copy(alpha = if (vaultColors.isDark) 0.24f else 0.18f),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.testTag("nav_rail_${tab.route}")
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(1.dp)
                .background(vaultColors.glassHighlightBorder.copy(alpha = 0.6f))
        )
    }
}

@Composable
private fun tabAccentColor(tab: TopLevelTab): Color {
    val vaultColors = LocalVaultColors.current
    return when (tab) {
        TopLevelTab.HOME -> vaultColors.goldAccent
        TopLevelTab.PURCHASES -> vaultColors.purchaseAccent
        TopLevelTab.MEDICINES -> vaultColors.medicineAccent
        TopLevelTab.SEARCH -> vaultColors.goldAccent
        TopLevelTab.SETTINGS -> MaterialTheme.colorScheme.primary
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun VaultAuthenticatedNavHost(
    navController: NavHostController,
    viewModel: VaultViewModel,
    familyViewModel: FamilyVaultViewModel,
    applyTopStatusPadding: Boolean,
    bottomContentPadding: Dp,
    topLevelRoutes: Set<String>,
    onSignedOut: () -> Unit
) {
    val familyGroupState by familyViewModel.familyGroupState.collectAsStateWithLifecycle()
    val familyMembers by familyViewModel.membersState.collectAsStateWithLifecycle()
    val sharedRecords by familyViewModel.sharedRecordsState.collectAsStateWithLifecycle()
    val sharedRecordIds by familyViewModel.sharedRecordIds.collectAsStateWithLifecycle()
    val userProfileState by viewModel.userProfileState.collectAsStateWithLifecycle()

    val familyGroup = (familyGroupState as? UiState.Success)?.data
    val ownerName = (userProfileState as? UiState.Success)?.data?.fullName?.takeIf { it.isNotBlank() } ?: "Vault Owner"

    val topTabModifier = if (applyTopStatusPadding) {
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)
    } else {
        Modifier.fillMaxSize()
    }

    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedTransitionScope provides this@SharedTransitionLayout) {
            NavHost(
                navController = navController,
                startDestination = VaultRoutes.HOME,
                enterTransition = {
                    val isTabSwitch = initialState.destination.route in topLevelRoutes &&
                        targetState.destination.route in topLevelRoutes
                    if (isTabSwitch) {
                        fadeIn(tween(240, easing = FastOutSlowInEasing)) +
                            scaleIn(initialScale = 0.985f, animationSpec = tween(240, easing = FastOutSlowInEasing))
                    } else {
                        fadeIn(tween(300, easing = FastOutSlowInEasing)) +
                            scaleIn(initialScale = 0.96f, animationSpec = tween(300, easing = FastOutSlowInEasing)) +
                            slideInVertically(animationSpec = tween(300, easing = FastOutSlowInEasing)) { it / 18 }
                    }
                },
                exitTransition = {
                    val isTabSwitch = initialState.destination.route in topLevelRoutes &&
                        targetState.destination.route in topLevelRoutes
                    if (isTabSwitch) {
                        fadeOut(tween(180, easing = FastOutSlowInEasing))
                    } else {
                        fadeOut(tween(220, easing = FastOutSlowInEasing)) +
                            scaleOut(targetScale = 0.98f, animationSpec = tween(220, easing = FastOutSlowInEasing))
                    }
                },
                popEnterTransition = {
                    fadeIn(tween(280, easing = FastOutSlowInEasing)) +
                        scaleIn(initialScale = 0.98f, animationSpec = tween(280, easing = FastOutSlowInEasing))
                },
                popExitTransition = {
                    fadeOut(tween(240, easing = FastOutSlowInEasing)) +
                        scaleOut(targetScale = 0.96f, animationSpec = tween(240, easing = FastOutSlowInEasing)) +
                        slideOutVertically(animationSpec = tween(240, easing = FastOutSlowInEasing)) { it / 18 }
                }
            ) {
                composable(VaultRoutes.HOME) {
                    CompositionLocalProvider(LocalAnimatedVisibilityScope provides this@composable) {
                        Box(modifier = topTabModifier) {
                            HomeDashboardScreen(
                                viewModel = viewModel,
                                bottomContentPadding = bottomContentPadding,
                                familyGroupName = familyGroup?.name,
                                familyMemberCount = familyMembers.size.coerceAtLeast(familyGroup?.memberUids?.size ?: 0),
                                familySharedCount = sharedRecords.size,
                                onOpenFamilyVault = { navController.navigate(VaultRoutes.FAMILY_VAULT) },
                                onOpenPurchaseVault = { navigateToTopTab(navController, VaultRoutes.PURCHASE_VAULT) },
                                onOpenMedicineVault = { navigateToTopTab(navController, VaultRoutes.MEDICINE_VAULT) },
                                onScanReceipt = { navController.navigate(VaultRoutes.addScanReceipt(autoScan = true)) },
                                onAddReceipt = { navController.navigate(VaultRoutes.addScanReceipt(autoScan = false)) },
                                onAddMedicine = { navController.navigate(VaultRoutes.addEditMedicine()) },
                                onOpenNotifications = { navController.navigate(VaultRoutes.NOTIFICATIONS) },
                                onOpenPriorityCenter = { navController.navigate(VaultRoutes.PRIORITY_CENTER) },
                                onOpenSearch = { navigateToTopTab(navController, VaultRoutes.SEARCH) },
                                onOpenProfile = { navController.navigate(VaultRoutes.PROFILE) },
                                onOpenPurchaseDetail = { id -> navController.navigate(VaultRoutes.purchaseDetail(id)) },
                                onOpenMedicineDetail = { id -> navController.navigate(VaultRoutes.medicineDetail(id)) }
                            )
                        }
                    }
                }

                composable(VaultRoutes.PURCHASE_VAULT) {
                    CompositionLocalProvider(LocalAnimatedVisibilityScope provides this@composable) {
                        Box(modifier = topTabModifier) {
                            PurchaseVaultDashboardScreen(
                                viewModel = viewModel,
                                bottomContentPadding = bottomContentPadding,
                                onScanReceipt = { navController.navigate(VaultRoutes.addScanReceipt(autoScan = true)) },
                                onAddReceipt = { navController.navigate(VaultRoutes.addScanReceipt(autoScan = false)) },
                                onViewAllReceipts = { navController.navigate(VaultRoutes.ALL_RECEIPTS) },
                                onOpenTracker = { tab -> navController.navigate(VaultRoutes.tracker(tab)) },
                                onOpenPriorityCenter = { navController.navigate(VaultRoutes.PRIORITY_CENTER) },
                                onOpenPurchaseDetail = { id -> navController.navigate(VaultRoutes.purchaseDetail(id)) }
                            )
                        }
                    }
                }

                composable(VaultRoutes.MEDICINE_VAULT) {
                    CompositionLocalProvider(LocalAnimatedVisibilityScope provides this@composable) {
                        Box(modifier = topTabModifier) {
                            MedicineVaultDashboardScreen(
                                viewModel = viewModel,
                                bottomContentPadding = bottomContentPadding,
                                onAddMedicine = { navController.navigate(VaultRoutes.addEditMedicine()) },
                                onOpenPriorityCenter = { navController.navigate(VaultRoutes.PRIORITY_CENTER) },
                                onOpenMedicineDetail = { id -> navController.navigate(VaultRoutes.medicineDetail(id)) }
                            )
                        }
                    }
                }

                composable(VaultRoutes.SEARCH) {
                    CompositionLocalProvider(LocalAnimatedVisibilityScope provides this@composable) {
                        Box(modifier = topTabModifier) {
                            GlobalSearchScreen(
                                viewModel = viewModel,
                                bottomContentPadding = bottomContentPadding,
                                onOpenPurchaseDetail = { id -> navController.navigate(VaultRoutes.purchaseDetail(id)) },
                                onOpenMedicineDetail = { id -> navController.navigate(VaultRoutes.medicineDetail(id)) }
                            )
                        }
                    }
                }

                composable(VaultRoutes.SETTINGS) {
                    CompositionLocalProvider(LocalAnimatedVisibilityScope provides this@composable) {
                        Box(modifier = topTabModifier) {
                            SettingsScreen(
                                viewModel = viewModel,
                                bottomContentPadding = bottomContentPadding,
                                familyGroupName = familyGroup?.name,
                                onOpenFamilyVault = { navController.navigate(VaultRoutes.FAMILY_VAULT) },
                                onOpenProfile = { navController.navigate(VaultRoutes.PROFILE) },
                                onSignedOut = onSignedOut
                            )
                        }
                    }
                }

                composable(VaultRoutes.ALL_RECEIPTS) {
                    CompositionLocalProvider(LocalAnimatedVisibilityScope provides this@composable) {
                        AllReceiptsScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onAddReceipt = { navController.navigate(VaultRoutes.addScanReceipt(autoScan = false)) },
                            onOpenPurchaseDetail = { id -> navController.navigate(VaultRoutes.purchaseDetail(id)) }
                        )
                    }
                }

                composable(
                    route = VaultRoutes.ADD_SCAN_RECEIPT,
                    arguments = listOf(
                        navArgument("editId") {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                        navArgument("scan") {
                            type = NavType.BoolType
                            defaultValue = false
                        }
                    )
                ) { entry ->
                    CompositionLocalProvider(LocalAnimatedVisibilityScope provides this@composable) {
                        val editId = entry.arguments?.getString("editId")?.takeIf { it.isNotBlank() }
                        val autoScan = entry.arguments?.getBoolean("scan") ?: false
                        AddScanReceiptScreen(
                            viewModel = viewModel,
                            editingPurchaseId = editId,
                            autoScanMode = autoScan,
                            onBack = { navController.popBackStack() },
                            onSaved = { savedId ->
                                navController.popBackStack()
                                if (editId == null) {
                                    navController.navigate(VaultRoutes.purchaseDetail(savedId))
                                }
                            },
                            onOpenExistingPurchase = { existingId ->
                                navController.navigate(VaultRoutes.purchaseDetail(existingId))
                            }
                        )
                    }
                }

                composable(
                    route = VaultRoutes.PURCHASE_DETAIL,
                    arguments = listOf(
                        navArgument("purchaseId") { type = NavType.StringType }
                    )
                ) { entry ->
                    CompositionLocalProvider(LocalAnimatedVisibilityScope provides this@composable) {
                        val purchaseId = Uri.decode(entry.arguments?.getString("purchaseId").orEmpty())
                        val shareId = com.example.model.SharedVaultRecord.deterministicShareId(
                            familyViewModel.currentUserId,
                            com.example.model.SharedRecordType.PURCHASE,
                            purchaseId
                        )
                        ReceiptDetailsScreen(
                            purchaseId = purchaseId,
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onEdit = { id -> navController.navigate(VaultRoutes.addScanReceipt(editId = id)) },
                            onTrackReturn = { navController.navigate(VaultRoutes.tracker(tab = 0)) },
                            onTrackWarranty = { navController.navigate(VaultRoutes.tracker(tab = 1)) },
                            onOpenWarrantyClaim = { id -> navController.navigate(VaultRoutes.warrantyClaim(id)) },
                            isSharedWithFamily = familyGroup != null && (shareId in sharedRecordIds),
                            onToggleFamilyShare = { purchase ->
                                if (familyGroup == null) {
                                    navController.navigate(VaultRoutes.FAMILY_VAULT)
                                } else {
                                    familyViewModel.toggleSharePurchase(ownerName, purchase)
                                }
                            }
                        )
                    }
                }

                composable(
                    route = VaultRoutes.TRACKER,
                    arguments = listOf(
                        navArgument("tab") {
                            type = NavType.IntType
                            defaultValue = 0
                        }
                    )
                ) { entry ->
                    CompositionLocalProvider(LocalAnimatedVisibilityScope provides this@composable) {
                        val tab = entry.arguments?.getInt("tab") ?: 0
                        ReturnWarrantyTrackerScreen(
                            initialTab = tab,
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onOpenPurchaseDetail = { id -> navController.navigate(VaultRoutes.purchaseDetail(id)) },
                            onOpenWarrantyClaim = { id -> navController.navigate(VaultRoutes.warrantyClaim(id)) }
                        )
                    }
                }

                composable(
                    route = VaultRoutes.WARRANTY_CLAIM,
                    arguments = listOf(
                        navArgument("purchaseId") { type = NavType.StringType }
                    )
                ) { entry ->
                    CompositionLocalProvider(LocalAnimatedVisibilityScope provides this@composable) {
                        val purchaseId = Uri.decode(entry.arguments?.getString("purchaseId").orEmpty())
                        WarrantyClaimAssistantScreen(
                            purchaseId = purchaseId,
                            vaultViewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }

                composable(
                    route = VaultRoutes.ADD_EDIT_MEDICINE,
                    arguments = listOf(
                        navArgument("editId") {
                            type = NavType.StringType
                            defaultValue = ""
                        }
                    )
                ) { entry ->
                    CompositionLocalProvider(LocalAnimatedVisibilityScope provides this@composable) {
                        val editId = entry.arguments?.getString("editId")?.takeIf { it.isNotBlank() }
                        AddEditMedicineScreen(
                            viewModel = viewModel,
                            editingMedicineId = editId,
                            onBack = { navController.popBackStack() },
                            onSaved = { savedId ->
                                navController.popBackStack()
                                if (editId == null) {
                                    navController.navigate(VaultRoutes.medicineDetail(savedId))
                                }
                            }
                        )
                    }
                }

                composable(
                    route = VaultRoutes.MEDICINE_DETAIL,
                    arguments = listOf(
                        navArgument("medicineId") { type = NavType.StringType }
                    )
                ) { entry ->
                    CompositionLocalProvider(LocalAnimatedVisibilityScope provides this@composable) {
                        val medicineId = Uri.decode(entry.arguments?.getString("medicineId").orEmpty())
                        val shareId = com.example.model.SharedVaultRecord.deterministicShareId(
                            familyViewModel.currentUserId,
                            com.example.model.SharedRecordType.MEDICINE,
                            medicineId
                        )
                        MedicineDetailsScreen(
                            medicineId = medicineId,
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onEdit = { id -> navController.navigate(VaultRoutes.addEditMedicine(editId = id)) },
                            isSharedWithFamily = familyGroup != null && (shareId in sharedRecordIds),
                            onToggleFamilyShare = { medicine ->
                                if (familyGroup == null) {
                                    navController.navigate(VaultRoutes.FAMILY_VAULT)
                                } else {
                                    familyViewModel.toggleShareMedicine(ownerName, medicine)
                                }
                            }
                        )
                    }
                }

                composable(VaultRoutes.NOTIFICATIONS) {
                    CompositionLocalProvider(LocalAnimatedVisibilityScope provides this@composable) {
                        NotificationCenterScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onOpenPurchaseDetail = { id -> navController.navigate(VaultRoutes.purchaseDetail(id)) },
                            onOpenMedicineDetail = { id -> navController.navigate(VaultRoutes.medicineDetail(id)) },
                            onOpenPriorityCenter = { navController.navigate(VaultRoutes.PRIORITY_CENTER) },
                            onOpenPreferences = {
                                navController.popBackStack()
                                navigateToTopTab(navController, VaultRoutes.SETTINGS)
                            }
                        )
                    }
                }

                composable(VaultRoutes.PRIORITY_CENTER) {
                    CompositionLocalProvider(LocalAnimatedVisibilityScope provides this@composable) {
                        PriorityCenterScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onOpenPurchaseDetail = { id -> navController.navigate(VaultRoutes.purchaseDetail(id)) },
                            onOpenMedicineDetail = { id -> navController.navigate(VaultRoutes.medicineDetail(id)) },
                            onOpenNotificationPreferences = {
                                navController.popBackStack()
                                navigateToTopTab(navController, VaultRoutes.SETTINGS)
                            }
                        )
                    }
                }

                composable(VaultRoutes.FAMILY_VAULT) {
                    CompositionLocalProvider(LocalAnimatedVisibilityScope provides this@composable) {
                        FamilyVaultScreen(
                            vaultViewModel = viewModel,
                            familyViewModel = familyViewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }

                composable(VaultRoutes.PROFILE) {
                    CompositionLocalProvider(LocalAnimatedVisibilityScope provides this@composable) {
                        ProfileScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onOpenSettings = {
                                navController.popBackStack()
                                navigateToTopTab(navController, VaultRoutes.SETTINGS)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VaultSecurityShieldOverlay(
    userName: String,
    onUnlock: () -> Unit
) {
    val vaultColors = LocalVaultColors.current
    VaultAtmosphereBackground(
        accentColor = vaultColors.goldAccent,
        secondaryAccent = vaultColors.purchaseAccent
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            GlassVaultCard(
                modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
                accentBorder = vaultColors.goldAccent.copy(alpha = 0.55f)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    VaultoraLogoBadge(size = 76.dp)
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = vaultColors.goldContainer,
                        border = BorderStroke(1.dp, vaultColors.goldAccent.copy(alpha = 0.45f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Lock,
                                contentDescription = null,
                                tint = vaultColors.goldAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "VAULT SHIELD ACTIVE",
                                style = MaterialTheme.typography.labelSmall,
                                color = vaultColors.goldAccent
                            )
                        }
                    }
                    Text(
                        text = if (userName.isNotBlank()) "Locked for $userName" else "VAULTORA Locked",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "Your digital vault security shield is enabled. Tap below to unlock your encrypted purchase and medicine records.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    VaultGradientButton(
                        onClick = onUnlock,
                        colors = vaultColors.brandButtonGradient,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("unlock_vault_shield_button")
                    ) {
                        Text("Unlock VAULTORA", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
