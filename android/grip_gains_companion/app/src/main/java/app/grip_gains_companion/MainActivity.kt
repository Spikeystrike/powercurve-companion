package app.grip_gains_companion

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.grip_gains_companion.data.PreferencesRepository
import app.grip_gains_companion.database.*
import app.grip_gains_companion.model.ConnectionState
import app.grip_gains_companion.service.IsoSessionManager
import app.grip_gains_companion.service.ProgressorHandler
import app.grip_gains_companion.service.TimerForegroundService
import app.grip_gains_companion.service.ble.BluetoothManager
import app.grip_gains_companion.service.web.WebViewBridge
import app.grip_gains_companion.ui.screens.*
import app.grip_gains_companion.ui.theme.GripGainsTheme
import app.grip_gains_companion.util.HapticManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

class MainActivity : ComponentActivity() {
    private val interactionFrameRate by lazy { app.grip_gains_companion.ui.InteractionFrameRate(window) }

    override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean {
        interactionFrameRate.onTouch(event.actionMasked)
        return super.dispatchTouchEvent(event)
    }


    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var progressorHandler: ProgressorHandler
    private lateinit var webViewBridge: WebViewBridge
    private lateinit var preferencesRepository: PreferencesRepository
    private lateinit var hapticManager: HapticManager

    private val calibrationPreferencesReady = kotlinx.coroutines.CompletableDeferred<Unit>()

    private val forceDropDetector = app.grip_gains_companion.service.ForceDropDetector()
    private var autoEndEnabled = true
    private var forceDropFraction = 0.50
    private var forceDropHoldMs = 250L
    private var trainingVisible = false
    private var isoSessionManager = IsoSessionManager()

    private var showIsoSummary by mutableStateOf(false)
    private var currentManualWeight by mutableDoubleStateOf(20.0)

    private lateinit var database: AppDatabase
    private lateinit var sessionRepository: SessionRepository

    private val failRepReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == TimerForegroundService.ACTION_FAIL_REP) {
                hapticManager.warning()
                webViewBridge.clickFailButton()

                val stopIntent = Intent(this@MainActivity, TimerForegroundService::class.java)
                stopIntent.action = TimerForegroundService.ACTION_STOP
                startService(stopIntent)
            }
        }
    }

    private val requiredPermissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.POST_NOTIFICATIONS
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.all { it.value }) {
                bluetoothManager.startScanning()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        ContextCompat.registerReceiver(
            this,
            failRepReceiver,
            IntentFilter(TimerForegroundService.ACTION_FAIL_REP),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        database = AppDatabase.getDatabase(this)
        sessionRepository = SessionRepository(database.rawSessionDao(), database.isoSessionDao())

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        bluetoothManager = BluetoothManager(this)
        progressorHandler = ProgressorHandler()
        webViewBridge = WebViewBridge()
        webViewBridge.offline = app.grip_gains_companion.service.offline.OfflineTraining(this, webViewBridge, lifecycleScope)
        webViewBridge.offline.onPullCue = { if(trainingVisible) app.grip_gains_companion.util.ToneGenerator.playHighTone() }
        preferencesRepository = PreferencesRepository(this)
        hapticManager = HapticManager(this)

        lifecycleScope.launch { preferencesRepository.initializeUnitsIfNeeded() }

        bluetoothManager.onForceSample = { force, timestamp ->
            lifecycleScope.launch {
                if (!force.isFinite()) return@launch
                calibrationPreferencesReady.await()
                progressorHandler.processSample(force, timestamp)
                val measuredKg = progressorHandler.currentForce.value
                if (isoSessionManager.isRepActive) isoSessionManager.addSample(measuredKg)
                if(webViewBridge.offline.isRealForce) {
                    forceDropDetector.reset()
                    if(trainingVisible && !progressorHandler.calibrating) webViewBridge.offline.forceSample(measuredKg,android.os.SystemClock.elapsedRealtime())
                    return@launch
                }
                val active = trainingVisible && autoEndEnabled && webViewBridge.isFreshActive &&
                    bluetoothManager.connectionState.value == ConnectionState.Connected && !progressorHandler.calibrating
                if (forceDropDetector.sample(measuredKg, android.os.SystemClock.elapsedRealtime(),
                        webViewBridge.repKey, active, webViewBridge.targetWeight.value ?: Double.NaN,
                        forceDropFraction, forceDropHoldMs)) {
                    hapticManager.warning()
                    webViewBridge.clickFailButton()
                }
            }
        }

        setupEventHandlers()

        if (hasAllPermissions()) {
            bluetoothManager.startScanning()
        } else {
            permissionLauncher.launch(requiredPermissions)
        }

        setContent {
            val navController = rememberNavController()
            val snackbarHostState = remember { SnackbarHostState() }

            val enableBluetoothLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
            val btAdapter = remember { (getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager).adapter }

            var showWeightPrompt by remember { mutableStateOf(false) }
            var showTensionSheet by remember { mutableStateOf(false) }

            val enableAnalytics by preferencesRepository.enableAnalytics.collectAsState(initial = true)

            val currentUrl by webViewBridge.currentUrl.collectAsState()
            val webWeight by webViewBridge.targetWeight.collectAsState()
            val isBasicTimerPage = currentUrl.contains("basic-timer")
            val enableIsotonicMode by preferencesRepository.enableIsotonicMode.collectAsState(initial = true)
            val isIsotonicSession = isBasicTimerPage && enableIsotonicMode

            val allIsoSessions by sessionRepository.getAllIsoSessions().collectAsState(initial = emptyList())
            val recentGrippers = remember(allIsoSessions) { allIsoSessions.map { it.gripperType }.distinct().sorted() }


            val connectionState by bluetoothManager.connectionState.collectAsState()
            val isConnected = connectionState == ConnectionState.Connected
            val discoveredDevices by bluetoothManager.discoveredDevices.collectAsState()

            val isoGripper by webViewBridge.sessionGripper.collectAsState()
            val isoSide by webViewBridge.sessionSide.collectAsState()

            val useLbs by preferencesRepository.useLbs.collectAsState(initial = false)

            val whc06FallbackLbs by preferencesRepository.whc06FallbackLbs.collectAsState(initial = false)
            LaunchedEffect(whc06FallbackLbs) {
                bluetoothManager.setHardwareUnitIsLbs(whc06FallbackLbs)
            }

            val showForceGraph by preferencesRepository.showForceGraph.collectAsState(initial = true)
            val forceGraphWindow by preferencesRepository.forceGraphWindow.collectAsState(initial = 5)
            val enableTargetWeight by preferencesRepository.enableTargetWeight.collectAsState(initial = true)
            val weightTolerance by preferencesRepository.weightTolerance.collectAsState(initial = 0.5)
            val enableHaptics by preferencesRepository.enableHaptics.collectAsState(initial = true)
            val enableCalibration by preferencesRepository.enableCalibration.collectAsState(initial = true)
            val enableTargetSound by preferencesRepository.enableTargetSound.collectAsState(initial = true)

            // The active timer owns the target, including local setup before a set starts.
            val effectiveTargetWeight = if (isBasicTimerPage && !webViewBridge.usingOfflineTarget) currentManualWeight else webWeight

            val currentForce by progressorHandler.currentForce.collectAsState()
            var previousForce by remember { mutableDoubleStateOf(0.0) }
            var hasHitTargetThisRep by remember { mutableStateOf(false) }

            LaunchedEffect(currentForce, effectiveTargetWeight) {
                if (effectiveTargetWeight != null && effectiveTargetWeight > 0.0 && enableTargetWeight) {
                    val targetLowerBound = effectiveTargetWeight - weightTolerance

                    if (currentForce >= targetLowerBound && previousForce < targetLowerBound) {
                        hasHitTargetThisRep = true
                        if (enableHaptics) hapticManager.success()
                        if (enableTargetSound) app.grip_gains_companion.util.ToneGenerator.playHighTone()
                    }
                    else if (currentForce < targetLowerBound && previousForce >= targetLowerBound && hasHitTargetThisRep && currentForce > (effectiveTargetWeight * 0.5)) {
                        if (enableHaptics) hapticManager.warning()
                        if (enableTargetSound) app.grip_gains_companion.util.ToneGenerator.playLowTone()
                    }

                    if (currentForce < 2.0) {
                        hasHitTargetThisRep = false
                    }
                }
                previousForce = currentForce
            }

            LaunchedEffect(connectionState) {
                if (connectionState == ConnectionState.Connected && enableHaptics) hapticManager.success()
                if (connectionState != ConnectionState.Connected && !webViewBridge.isFreshActive && !webViewBridge.offline.isRealForce) {
                    forceDropDetector.reset()
                    progressorHandler.reset()
                }
            }
            LaunchedEffect(effectiveTargetWeight) {
                progressorHandler.targetWeight = effectiveTargetWeight
            }

            // FRESH STATE SAVING FIX
            LaunchedEffect(Unit) {
                kotlinx.coroutines.flow.merge(
                    webViewBridge.manualSessionEndTrigger,
                    webViewBridge.saveButtonAppeared
                ).collect { triggered ->
                    if (triggered) {
                        processSessionEnd(
                            onAutoSaveIso = {
                                lifecycleScope.launch { snackbarHostState.showSnackbar("Session auto-saved") }
                            }
                        )
                        webViewBridge.resetManualSessionEndTrigger()
                        webViewBridge.resetSaveFlag()
                    }
                }
            }

            var weightInputText by remember { mutableStateOf(effectiveTargetWeight?.toString() ?: "") }
            val baseContext = LocalContext.current
            val darkContext = remember(baseContext) {
                android.view.ContextThemeWrapper(baseContext, android.R.style.Theme_Material_NoActionBar)
            }

            val cachedWebView = remember {
                android.webkit.WebView(darkContext).apply {
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(android.graphics.Color.parseColor("#1A2231"))

                    if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.ALGORITHMIC_DARKENING)) {
                        androidx.webkit.WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, false)
                    }

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        allowFileAccess = false
                        allowContentAccess = false
                        mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        userAgentString = userAgentString.replace("; wv", "")
                    }

                    android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)

                    webViewClient = android.webkit.WebViewClient()

                    webChromeClient = object : android.webkit.WebChromeClient() {
                        override fun onJsAlert(view: android.webkit.WebView?, url: String?, message: String?, result: android.webkit.JsResult?): Boolean {
                            android.app.AlertDialog.Builder(darkContext, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                                .setMessage(message)
                                .setPositiveButton(android.R.string.ok) { dialog, _ -> result?.confirm(); dialog.dismiss() }
                                .setOnDismissListener { result?.cancel() }
                                .show()
                            return true
                        }

                        override fun onJsConfirm(view: android.webkit.WebView?, url: String?, message: String?, result: android.webkit.JsResult?): Boolean {
                            android.app.AlertDialog.Builder(darkContext, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                                .setMessage(message)
                                .setPositiveButton(android.R.string.ok) { dialog, _ ->
                                    result?.confirm()
                                    dialog.dismiss()
                                }
                                .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                                    result?.cancel()
                                    dialog.dismiss()
                                }
                                .setOnDismissListener { result?.cancel() }
                                .show()
                            return true
                        }
                    }

                    // The Powercurve-only message listener is installed by TimerWebView.
                }
            }

            GripGainsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        NavHost(
                            navController = navController,
                            startDestination = "main" ,
                            enterTransition = {
                                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Up, tween(300))
                            },
                            exitTransition = {
                                fadeOut(tween(300))
                            },
                            popEnterTransition = {
                                fadeIn(tween(300))
                            },
                            popExitTransition = {
                                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Down, tween(300))
                            }
                        ) {
                            composable("main") {
                                DisposableEffect(Unit) {
                                    enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
                                    onDispose {
                                        enableEdgeToEdge(statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT))
                                    }
                                }

                                MainScreen(
                                    preferencesRepository = preferencesRepository,
                                    bluetoothManager = bluetoothManager,
                                    progressorHandler = progressorHandler,
                                    webViewBridge = webViewBridge,
                                    cachedWebView = cachedWebView,
                                    showForceGraph = showForceGraph,
                                    forceGraphWindow = forceGraphWindow,
                                    useLbs = useLbs,
                                    enableTargetWeight = enableTargetWeight,
                                    manualTargetWeight = effectiveTargetWeight,
                                    weightTolerance = weightTolerance,
                                    enableAnalytics = enableAnalytics,
                                    isIsotonicSession = isIsotonicSession,
                                    onSettingsTap = { navController.navigate("settings") },
                                    onHistoryTap = { navController.navigate("history") },
                                    onSetManualWeightTap = {
                                        if (isBasicTimerPage) {
                                            val weight = effectiveTargetWeight ?: currentManualWeight
                                            weightInputText = String.format(java.util.Locale.US, "%.1f", if (useLbs) weight * 2.20462 else weight)
                                            showWeightPrompt = true
                                        } else {
                                            lifecycleScope.launch { snackbarHostState.showSnackbar("Set the target in Powercurve's Weight field before starting the set.") }
                                        }
                                    }
                                )
                            }

                            composable("settings") {
                                SettingsScreen(
                                    preferencesRepository = preferencesRepository,
                                    bluetoothManager = bluetoothManager,
                                    webViewBridge = webViewBridge,
                                    currentManualWeight = currentManualWeight,
                                    onWeightChange = { newWeight -> currentManualWeight = newWeight },
                                    onDismiss = { navController.popBackStack() },
                                    onDisconnect = { bluetoothManager.disconnect() },
                                    onConnectDevice = { bluetoothManager.restartScanning(); navController.popBackStack() },
                                    onRecalibrate = {
                                        progressorHandler.recalibrate()
                                        lifecycleScope.launch { snackbarHostState.showSnackbar("Scale Zeroed") }
                                    },
                                    onViewLogs = { navController.navigate("logs") },
                                    onViewHistory = { navController.navigate("history") }
                                )
                            }

                            composable("history") {
                                HistoryScreen(
                                    sessionRepository = sessionRepository,
                                    enableIsotonicMode = enableIsotonicMode,
                                    onBack = { navController.popBackStack() },
                                    onViewSession = { id, _ ->
                                        val isoId = id.replace("ISO_", "")
                                        navController.navigate("iso_session_details/$isoId")
                                    }
                                )
                            }

                            composable("iso_session_details/{sessionId}") { backStackEntry ->
                                val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""
                                IsoSessionDetailScreen(
                                    sessionId = sessionId,
                                    sessionRepository = sessionRepository,
                                    useLbs = useLbs,
                                    recentEquipment = recentGrippers,
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable("logs") {
                                LogViewerScreen(onDismiss = { navController.popBackStack() })
                            }
                        }

                        SnackbarHost(
                            hostState = snackbarHostState,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 100.dp)
                        )
                    }

                    if (showWeightPrompt) {
                        AlertDialog(
                            onDismissRequest = { showWeightPrompt = false },
                            title = { Text(if (isBasicTimerPage) "Set Basic Timer Weight" else "Override Target Weight") },
                            text = {
                                OutlinedTextField(
                                    value = weightInputText,
                                    onValueChange = { weightInputText = it },
                                    label = { Text(if (useLbs) "Weight (lbs)" else "Weight (kg)") },
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                                )
                            },
                            confirmButton = {
                                Button(onClick = {
                                    weightInputText.toDoubleOrNull()?.let { typedWeight ->
                                        val internalKg = if (useLbs) typedWeight / 2.20462 else typedWeight
                                        if (isBasicTimerPage) {
                                            currentManualWeight = internalKg
                                            lifecycleScope.launch { preferencesRepository.setManualTargetWeight(internalKg); preferencesRepository.setUseManualTarget(true) }
                                        } else {
                                            // Powercurve targets are read from its Weight field.
                                        }
                                    }
                                    showWeightPrompt = false
                                }) { Text("Save") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showWeightPrompt = false }) { Text("Cancel") }
                            }
                        )
                    }

                    if (showTensionSheet) {
                        AlertDialog(
                            onDismissRequest = { showTensionSheet = false },
                            shape = RoundedCornerShape(28.dp),
                            title = { Text("Select Tension Source", fontWeight = FontWeight.Bold) },
                            text = {
                                Column {
                                    if (isConnected) {
                                        TextButton(
                                            onClick = { bluetoothManager.disconnect(); showTensionSheet = false },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Disconnect Current Scale", color = MaterialTheme.colorScheme.error)
                                        }
                                        Spacer(modifier = Modifier.height(16.dp))
                                    }

                                    if (discoveredDevices.isEmpty() && !isConnected) {
                                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                            CircularProgressIndicator()
                                        }
                                    } else {
                                        LazyColumn(modifier = Modifier.heightIn(max = 350.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            items(discoveredDevices) { device ->
                                                Surface(
                                                    onClick = {
                                                        bluetoothManager.connect(device)
                                                        showTensionSheet = false
                                                    },
                                                    shape = RoundedCornerShape(16.dp),
                                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    @android.annotation.SuppressLint("MissingPermission")
                                                    ListItem(
                                                        headlineContent = { Text(device.name ?: "Unknown Device", fontWeight = FontWeight.Bold) },
                                                        supportingContent = { Text(device.address) },
                                                        leadingContent = { Icon(Icons.Default.Bluetooth, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { showTensionSheet = false }) { Text("Close") }
                            }
                        )
                    }

                    if (showIsoSummary) {
                        val mappedSide = when {
                            isoSide?.contains("Left", ignoreCase = true) == true -> "Left"
                            isoSide?.contains("Right", ignoreCase = true) == true -> "Right"
                            else -> "Bilateral"
                        }

                        IsoSummaryScreen(
                            reps = isoSessionManager.completedReps,
                            useLbs = useLbs,
                            initialGripper = isoGripper ?: "",
                            initialSide = mappedSide,
                            recentEquipment = recentGrippers,
                            isIsotonic = isIsotonicSession,
                            onDismiss = {
                                showIsoSummary = false
                                isoSessionManager.clearSession()
                                webViewBridge.resetSaveFlag()
                            },
                            onSave = { equipment, side ->
                                lifecycleScope.launch {
                                    val sessionEntity = IsoSessionEntity(
                                        id = UUID.randomUUID().toString(),
                                        timestamp = System.currentTimeMillis(),
                                        gripperType = equipment,
                                        side = side,
                                        scrapedGripper = isoGripper,
                                        scrapedSide = mappedSide,
                                        isIsotonic = isIsotonicSession
                                    )
                                    sessionRepository.insertIsoSession(sessionEntity)
                                    sessionRepository.insertIsoReps(isoSessionManager.completedReps.map {
                                        it.copy(sessionId = sessionEntity.id)
                                    })
                                    showIsoSummary = false
                                    isoSessionManager.clearSession()
                                    webViewBridge.resetSaveFlag()
                                    snackbarHostState.showSnackbar("Session saved successfully")
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    private fun setupEventHandlers() {
        lifecycleScope.launch { preferencesRepository.rememberZoneTiming.collect { webViewBridge.offline.rememberZoneTiming = it } }
        lifecycleScope.launch {
            preferencesRepository.enableCalibration.collect { enabled ->
                progressorHandler.enableCalibration = enabled
                calibrationPreferencesReady.complete(Unit)
            }
        }
        lifecycleScope.launch {
            kotlinx.coroutines.flow.combine(preferencesRepository.realForceEnabled,preferencesRepository.realForceMethod,bluetoothManager.connectionState,
                preferencesRepository.failThreshold,preferencesRepository.forceDropHoldMs) { enabled,method,connection,drop,hold ->
                webViewBridge.offline.configureRealForce(enabled,method,connection==ConnectionState.Connected,drop,hold.toLong())
            }.collect {}
        }
        lifecycleScope.launch { preferencesRepository.autoFailRep.collect { autoEndEnabled = it; forceDropDetector.reset() } }
        lifecycleScope.launch { preferencesRepository.failThreshold.collect { forceDropFraction = it; forceDropDetector.reset() } }
        lifecycleScope.launch { preferencesRepository.forceDropHoldMs.collect { forceDropHoldMs = it.toLong(); forceDropDetector.reset() } }
        lifecycleScope.launch {
            preferencesRepository.manualTargetWeight.collect { currentManualWeight = it }
        }

        lifecycleScope.launch {
            webViewBridge.buttonEnabled.collect { isLive ->
                forceDropDetector.reset()
                progressorHandler.canEngage = isLive
                if (isLive) {
                    if (!isoSessionManager.isRepActive) isoSessionManager.startRep()
                } else {
                    if (isoSessionManager.isRepActive) {
                        val fallbackWeight = webViewBridge.targetWeight.value
                        val targetDur = webViewBridge.targetDuration.value
                        isoSessionManager.endRep(fallbackWeight, targetDur)


                    }
                }
            }
        }

        var isForegroundServiceRunning = false
        var sessionStartTime = 0L

        lifecycleScope.launch {
            webViewBridge.remainingTime.collect { remaining ->
                if (remaining != null && remaining != -9999 && trainingVisible && bluetoothManager.connectionState.value == ConnectionState.Connected) {
                    if (!isForegroundServiceRunning) {
                        sessionStartTime = System.currentTimeMillis()
                        isForegroundServiceRunning = true
                    }
                    val elapsed = ((System.currentTimeMillis() - sessionStartTime) / 1000).toInt()
                    TimerForegroundService.updateNotification(this@MainActivity, elapsed, remaining)
                } else {
                    if (isForegroundServiceRunning) {
                        val stopIntent = Intent(this@MainActivity, TimerForegroundService::class.java).apply {
                            action = TimerForegroundService.ACTION_STOP
                        }
                        startService(stopIntent)
                        isForegroundServiceRunning = false
                    }
                }
            }
        }
    }

    private fun processSessionEnd(onAutoSaveIso: () -> Unit) {
        // ALWAYS launch a coroutine to grab fresh preference values instead of capturing stale ones
        lifecycleScope.launch {
            val fallbackWeight = webViewBridge.targetWeight.value
            if (isoSessionManager.isRepActive) isoSessionManager.endRep(fallbackWeight)

            val enableAnalytics = preferencesRepository.enableAnalytics.first()
            if (!enableAnalytics) {
                isoSessionManager.clearSession()
                return@launch
            }

            if (isoSessionManager.completedReps.isNotEmpty()) {

                // Read exact live state directly
                val currentUrl = webViewBridge.currentUrl.value
                val isBasicTimerPage = currentUrl.contains("basic-timer")
                val enableIsotonicMode = preferencesRepository.enableIsotonicMode.first()
                val isIsotonic = isBasicTimerPage && enableIsotonicMode

                if (isIsotonic) {
                    val showRawSummaryPref = preferencesRepository.showRawSummary.first()
                    if (showRawSummaryPref) {
                        showIsoSummary = true
                    } else {
                        // Discard if popups are off, as we have no muscle data
                        isoSessionManager.clearSession()
                    }
                } else {
                    val showIsoSummaryPref = preferencesRepository.showIsoSummary.first()
                    if (showIsoSummaryPref) {
                        showIsoSummary = true
                    } else {
                        // Isometric Popups are OFF: Auto-save the scraped data
                        val sSide = webViewBridge.sessionSide.value
                        val sGrip = webViewBridge.sessionGripper.value ?: ""
                        val mappedSide = when {
                            sSide?.contains("Left", ignoreCase = true) == true -> "Left"
                            sSide?.contains("Right", ignoreCase = true) == true -> "Right"
                            else -> "Bilateral"
                        }

                        val sessionEntity = IsoSessionEntity(
                            id = UUID.randomUUID().toString(),
                            timestamp = System.currentTimeMillis(),
                            gripperType = sGrip,
                            side = mappedSide,
                            scrapedGripper = sGrip.takeIf { it.isNotBlank() },
                            scrapedSide = mappedSide,
                            isIsotonic = false
                        )
                        sessionRepository.insertIsoSession(sessionEntity)
                        sessionRepository.insertIsoReps(isoSessionManager.completedReps.map {
                            it.copy(sessionId = sessionEntity.id)
                        })
                        isoSessionManager.clearSession()
                        onAutoSaveIso()
                    }
                }
            }
        }
    }

    private fun hasAllPermissions(): Boolean = requiredPermissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onResume() {
        super.onResume()
        trainingVisible = true
    }

    override fun onPause() {
        interactionFrameRate.reset()
        trainingVisible = false
        forceDropDetector.reset()
        if (::webViewBridge.isInitialized) {webViewBridge.offline.pauseRealForce("App paused. The unfinished rep was not counted.");webViewBridge.invalidate("Auto-end paused")}
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(failRepReceiver)
        webViewBridge.offline.close()
        bluetoothManager.disconnect(preserveAutoReconnect = true)
    }
}
