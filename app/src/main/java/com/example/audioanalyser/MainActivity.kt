package com.example.audioanalyser

import java.util.Locale
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.example.audioanalyser.ui.theme.AudioAnalyserTheme
import kotlin.math.log10
import kotlin.math.pow

class MainActivity : ComponentActivity() {
    private val audioAnalyzer = AudioAnalyzer()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AudioAnalyserTheme {
                MainScreen(audioAnalyzer)
            }
        }
    }
}

@Composable
fun MainScreen(analyzer: AudioAnalyzer) {
    val context = LocalContext.current
    val activity = LocalContext.current as? Activity
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var permissionRequested by remember { mutableStateOf(false) }

    val shouldShowRationale = remember(activity) {
        activity?.let { ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.RECORD_AUDIO) } ?: false
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionRequested = true
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    if (hasPermission) {
        AudioAnalyserContent(analyzer)
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(id = R.string.mic_access_required))
                Spacer(modifier = Modifier.height(16.dp))
                Row {
                    Button(onClick = {
                        permissionRequested = true
                        launcher.launch(Manifest.permission.RECORD_AUDIO)
                    }) {
                        Text(stringResource(id = R.string.allow_mic))
                    }
                    if (permissionRequested && !shouldShowRationale) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        }) {
                            Text(stringResource(id = R.string.open_settings))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioAnalyserContent(analyzer: AudioAnalyzer) {
    val dbLevel by analyzer.dbLevel.collectAsState()
    val minDb by analyzer.minDb.collectAsState()
    val maxDb by analyzer.maxDb.collectAsState()
    val avgDb by analyzer.avgDb.collectAsState()
    val dbHistory by analyzer.dbHistory.collectAsState()
    val frequencies by analyzer.frequencies.collectAsState()
    val dbOffset by analyzer.dbOffset.collectAsState()
    val noiseThreshold by analyzer.noiseThreshold.collectAsState()
    val dominantFrequency by analyzer.dominantFrequency.collectAsState()
    val error by analyzer.error.collectAsState()

    var showInfoDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showCalibrationScreen by remember { mutableStateOf(false) }
    val overlayProfiles = remember { analyzerOverlayProfiles }
    var selectedOverlayId by rememberSaveable { mutableStateOf(overlayProfiles.first().id) }
    val selectedOverlay = overlayProfiles.firstOrNull { it.id == selectedOverlayId } ?: overlayProfiles.first()

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Load persisted calibration values (if any)
    val ctx = LocalContext.current
    LaunchedEffect(Unit) {
        val prefs = ctx.getSharedPreferences("audio_prefs", android.content.Context.MODE_PRIVATE)
        val savedOffset = prefs.getFloat("db_offset", 30f)
        val savedNoise = prefs.getFloat("noise_threshold", 25f)
        analyzer.setDbOffset(savedOffset)
        analyzer.setNoiseThreshold(savedNoise)
    }

    // Lifecycle-aware start/stop
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> scope.launch { analyzer.startAnalyzing() }
                Lifecycle.Event.ON_PAUSE -> analyzer.stop()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            analyzer.stop()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(id = R.string.app_name), style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = { analyzer.resetStats() }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(id = R.string.reset_stats))
                    }
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(id = R.string.settings))
                    }
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(Icons.Default.Info, contentDescription = stringResource(id = R.string.info))
                    }
                }
            )
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            if (!error.isNullOrEmpty()) {
                ElevatedCard(modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)) {
                    Text(text = error ?: "", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp))
                }
            }

            if (isLandscape) {
                LandscapeLayout(
                    dbLevel = dbLevel,
                    minDb = minDb,
                    maxDb = maxDb,
                    avgDb = avgDb,
                    dbHistory = dbHistory,
                    frequencies = frequencies,
                    dominantFrequency = dominantFrequency,
                    selectedOverlay = selectedOverlay,
                    overlayProfiles = overlayProfiles,
                    onOverlaySelected = { selectedOverlayId = it.id },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                )
            } else {
                PortraitLayout(
                    dbLevel = dbLevel,
                    minDb = minDb,
                    maxDb = maxDb,
                    avgDb = avgDb,
                    dbHistory = dbHistory,
                    frequencies = frequencies,
                    dominantFrequency = dominantFrequency,
                    selectedOverlay = selectedOverlay,
                    overlayProfiles = overlayProfiles,
                    onOverlaySelected = { selectedOverlayId = it.id },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                )
            }
        }
    }

    if (showSettingsDialog) {
        SettingsDialog(
            currentOffset = dbOffset,
            currentThreshold = noiseThreshold,
            onOffsetChange = { analyzer.setDbOffset(it) },
            onThresholdChange = { analyzer.setNoiseThreshold(it) },
            onDismiss = { showSettingsDialog = false },
            onOpenCalibration = {
                showSettingsDialog = false
                showCalibrationScreen = true
            }
        )
    }

    if (showInfoDialog) {
        MixingInfoDialog(onDismiss = {
            showInfoDialog = false
        })
    }

    if (showCalibrationScreen) {
        CalibrationScreen(analyzer = analyzer, onClose = { showCalibrationScreen = false })
    }
}

@Composable
fun PortraitLayout(
    dbLevel: Float,
    minDb: Float,
    maxDb: Float,
    avgDb: Float,
    dbHistory: List<Float>,
    frequencies: FloatArray,
    dominantFrequency: Float,
    selectedOverlay: AnalyzerOverlayProfile,
    overlayProfiles: List<AnalyzerOverlayProfile>,
    onOverlaySelected: (AnalyzerOverlayProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        DbMeterCard(
            dbLevel = dbLevel,
            minDb = minDb,
            maxDb = maxDb,
            avgDb = avgDb,
            dbHistory = dbHistory,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        VisualizerCard(
            frequencies = frequencies,
            dominantFrequency = dominantFrequency,
            selectedOverlay = selectedOverlay,
            overlayProfiles = overlayProfiles,
            onOverlaySelected = onOverlaySelected,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
    }
}

@Composable
fun LandscapeLayout(
    dbLevel: Float,
    minDb: Float,
    maxDb: Float,
    avgDb: Float,
    dbHistory: List<Float>,
    frequencies: FloatArray,
    dominantFrequency: Float,
    selectedOverlay: AnalyzerOverlayProfile,
    overlayProfiles: List<AnalyzerOverlayProfile>,
    onOverlaySelected: (AnalyzerOverlayProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        DbMeterCard(
            dbLevel = dbLevel,
            minDb = minDb,
            maxDb = maxDb,
            avgDb = avgDb,
            dbHistory = dbHistory,
            modifier = Modifier
                .weight(0.4f)
                .fillMaxHeight()
        )

        Spacer(modifier = Modifier.width(16.dp))

        VisualizerCard(
            frequencies = frequencies,
            dominantFrequency = dominantFrequency,
            selectedOverlay = selectedOverlay,
            overlayProfiles = overlayProfiles,
            onOverlaySelected = onOverlaySelected,
            modifier = Modifier
                .weight(0.6f)
                .fillMaxHeight()
        )
    }
}

@Composable
fun DbMeterCard(
    dbLevel: Float,
    minDb: Float,
    maxDb: Float,
    avgDb: Float,
    dbHistory: List<Float>,
    modifier: Modifier = Modifier
) {
    val dbColor = when {
        dbLevel > 90 -> Color.Red
        dbLevel > 80 -> Color(0xFFFFC107) // Amber
        else -> MaterialTheme.colorScheme.primary
    }

    val semanticsDesc = stringResource(id = R.string.db_meter_semantics, dbLevel, if (minDb == Float.MAX_VALUE) 0f else minDb, avgDb, maxDb)

    ElevatedCard(modifier = modifier.semantics { contentDescription = semanticsDesc }) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(id = R.string.sound_level),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = String.format(Locale.getDefault(), "%.1f dB", dbLevel),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = dbColor
            )
            Surface(
                color = dbColor.copy(alpha = 0.1f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text(
                    text = if (dbLevel > 85) stringResource(id = R.string.too_loud) else stringResource(id = R.string.safe_level),
                    style = MaterialTheme.typography.labelMedium,
                    color = dbColor,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Stats Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(stringResource(id = R.string.min_label), if (minDb == Float.MAX_VALUE) 0f else minDb)
                StatItem(stringResource(id = R.string.avg_label), avgDb)
                StatItem(stringResource(id = R.string.max_label), maxDb)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // History Sparkline
            HistorySparkline(
                history = dbHistory,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
            )
        }
    }
}

@Composable
fun StatItem(label: String, value: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
        Text(
            text = String.format(Locale.getDefault(), "%.1f", value),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun HistorySparkline(history: List<Float>, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    Canvas(modifier = modifier) {
        if (history.size < 2) return@Canvas

        val width = size.width
        val height = size.height
        val maxVal = (history.maxOrNull() ?: 100f).coerceAtLeast(1f)
        val stepX = width / (history.size - 1)

        val points = history.mapIndexed { index, value ->
            Offset(
                x = index * stepX,
                y = height - (value / maxVal).coerceIn(0f, 1f) * height
            )
        }

        for (i in 0 until points.size - 1) {
            drawLine(
                color = lineColor,
                start = points[i],
                end = points[i + 1],
                strokeWidth = 2.dp.toPx()
            )
        }
    }
}

@Composable
fun VisualizerCard(
    frequencies: FloatArray,
    dominantFrequency: Float,
    selectedOverlay: AnalyzerOverlayProfile,
    overlayProfiles: List<AnalyzerOverlayProfile>,
    onOverlaySelected: (AnalyzerOverlayProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    val matchedBand = selectedOverlay.bandFor(dominantFrequency)
    val semanticsDesc = buildString {
        append(String.format(Locale.getDefault(), "Frequency visualizer. Dominant frequency %.0f Hz. ", dominantFrequency))
        when {
            matchedBand != null -> append("Current peak sits in ${matchedBand.label}.")
            selectedOverlay.bands.isNotEmpty() -> append("Current peak is outside the selected ${selectedOverlay.label} focus bands.")
            else -> append("No focus overlay selected.")
        }
    }
    ElevatedCard(modifier = modifier.semantics { contentDescription = semanticsDesc }) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxSize()
        ) {
            Text(
                text = stringResource(id = R.string.frequency_spectrum),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            Text(
                text = String.format(Locale.getDefault(), "Dominant %.0f Hz", dominantFrequency),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = when {
                    matchedBand != null -> "Peak focus: ${matchedBand.label} (${formatFrequencyRange(matchedBand.startHz, matchedBand.endHz)})"
                    selectedOverlay.bands.isNotEmpty() -> "Peak focus: outside ${selectedOverlay.label} guide bands"
                    else -> "Select an overlay to highlight a source's main ranges."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
            )

            Text(
                text = stringResource(id = R.string.overlay_selector),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                overlayProfiles.forEach { profile ->
                    FilterChip(
                        selected = profile.id == selectedOverlay.id,
                        onClick = { onOverlaySelected(profile) },
                        label = { Text(profile.label) }
                    )
                }
            }

            if (selectedOverlay.bands.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = selectedOverlay.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        selectedOverlay.bands.forEach { band ->
                            Text(
                                text = "${band.label}: ${formatFrequencyRange(band.startHz, band.endHz)} - ${band.note}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            FrequencyVisualizer(
                frequencies = frequencies,
                overlayBands = selectedOverlay.bands,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(id = R.string.bass_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text(stringResource(id = R.string.mids_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                Text(stringResource(id = R.string.highs_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
            }
        }
    }
}

@Composable
fun FrequencyVisualizer(
    frequencies: FloatArray,
    overlayBands: List<AnalyzerRangeBand> = emptyList(),
    modifier: Modifier = Modifier
) {
    val labels = listOf(
        60f to "60",
        250f to "250",
        1000f to "1k",
        4000f to "4k",
        16000f to "16k"
    )
    
    val minFreq = 20f
    val maxFreq = 22050f 
    val bassColor = MaterialTheme.colorScheme.primary
    val midsColor = MaterialTheme.colorScheme.secondary
    val highsColor = MaterialTheme.colorScheme.tertiary

    Canvas(modifier = modifier) {
        if (frequencies.isEmpty()) return@Canvas

        val width = size.width
        val height = size.height
        val labelArea = 24.dp.toPx()
        val drawHeight = height - labelArea
        val overlayLabelY = 14.dp.toPx()
        val logMin = log10(minFreq)
        val logMax = log10(maxFreq)

        // Helper to map frequency to X coordinate (log scale)
        fun getXForFreq(freq: Float): Float {
            val logFreq = log10(freq.coerceIn(minFreq, maxFreq))
            return ((logFreq - logMin) / (logMax - logMin)) * width
        }

        // Draw grid lines and labels
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.GRAY
            textSize = 10.sp.toPx()
            textAlign = android.graphics.Paint.Align.CENTER
        }

        val overlayPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.DKGRAY
            textSize = 9.sp.toPx()
            textAlign = android.graphics.Paint.Align.CENTER
            isFakeBoldText = true
        }

        overlayBands.forEach { band ->
            val startX = getXForFreq(band.startHz)
            val endX = getXForFreq(band.endHz)
            val bandWidth = endX - startX
            if (bandWidth <= 0f) return@forEach

            drawRect(
                color = band.color.copy(alpha = 0.12f),
                topLeft = Offset(startX, 0f),
                size = androidx.compose.ui.geometry.Size(bandWidth, drawHeight)
            )
            drawLine(
                color = band.color.copy(alpha = 0.35f),
                start = Offset(startX, 0f),
                end = Offset(startX, drawHeight),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = band.color.copy(alpha = 0.35f),
                start = Offset(endX, 0f),
                end = Offset(endX, drawHeight),
                strokeWidth = 1.dp.toPx()
            )

            if (bandWidth > 36.dp.toPx()) {
                drawContext.canvas.nativeCanvas.drawText(
                    band.label,
                    startX + (bandWidth / 2f),
                    overlayLabelY,
                    overlayPaint
                )
            }
        }

        labels.forEach { (freq, label) ->
            val x = getXForFreq(freq)
            drawLine(
                color = Color.Gray.copy(alpha = 0.2f),
                start = Offset(x, 0f),
                end = Offset(x, drawHeight),
                strokeWidth = 1.dp.toPx()
            )
            drawContext.canvas.nativeCanvas.drawText(label, x, drawHeight + labelArea - 4f, paint)
        }

        val numBars = 64
        val barWidth = width / numBars
        val globalMax = frequencies.maxOrNull() ?: 1f

        for (i in 0 until numBars) {
            val xStart = i * barWidth
            val xEnd = (i + 1) * barWidth

            fun getFreqForX(x: Float): Float {
                val logMin = log10(minFreq)
                val logMax = log10(maxFreq)
                val normalizedX = (x / width).coerceIn(0f, 1f)
                val logFreq = normalizedX * (logMax - logMin) + logMin
                return 10.0.pow(logFreq.toDouble()).toFloat()
            }

            val freqStart = getFreqForX(xStart)
            val freqEnd = getFreqForX(xEnd)

            val binSize = maxFreq / frequencies.size
            val startIndex = (freqStart / binSize).toInt().coerceIn(0, frequencies.size - 1)
            val endIndex = (freqEnd / binSize).toInt().coerceIn(0, frequencies.size - 1)

            var maxMagnitude = 0f
            for (j in startIndex..endIndex) {
                if (frequencies[j] > maxMagnitude) maxMagnitude = frequencies[j]
            }

            val barHeight = if (globalMax <= 0f) 0f else (maxMagnitude / globalMax) * drawHeight

            val freqColor = when {
                freqStart < 250 -> bassColor
                freqStart < 4000 -> midsColor
                else -> highsColor
            }

            drawRect(
                color = freqColor,
                topLeft = Offset(i * barWidth, drawHeight - barHeight),
                size = androidx.compose.ui.geometry.Size(barWidth - 2f, barHeight)
            )
        }
    }
}

@Composable
fun SettingsDialog(
    currentOffset: Float,
    currentThreshold: Float,
    onOffsetChange: (Float) -> Unit,
    onThresholdChange: (Float) -> Unit,
    onDismiss: () -> Unit,
    onOpenCalibration: (() -> Unit)? = null
) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.mic_calibration)) },
        text = {
            Column {
                Text(
                    text = String.format(Locale.getDefault(), stringResource(id = R.string.db_offset_label), currentOffset),
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = currentOffset,
                    onValueChange = onOffsetChange,
                    valueRange = 0f..100f
                )
                Text(
                    text = stringResource(id = R.string.db_offset_help),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = String.format(Locale.getDefault(), stringResource(id = R.string.noise_gate_label), currentThreshold),
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = currentThreshold,
                    onValueChange = onThresholdChange,
                    valueRange = 0f..60f
                )
                Text(
                    text = stringResource(id = R.string.noise_gate_help),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        dismissButton = {
            if (onOpenCalibration != null) {
                TextButton(onClick = {
                    val prefs = ctx.getSharedPreferences("audio_prefs", android.content.Context.MODE_PRIVATE)
                    prefs.edit()
                        .putFloat("db_offset", currentOffset)
                        .putFloat("noise_threshold", currentThreshold)
                        .apply()
                    onDismiss()
                    onOpenCalibration()
                }) {
                    Text(stringResource(id = R.string.open_calibration))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val prefs = ctx.getSharedPreferences("audio_prefs", android.content.Context.MODE_PRIVATE)
                prefs.edit()
                    .putFloat("db_offset", currentOffset)
                    .putFloat("noise_threshold", currentThreshold)
                    .apply()
                onDismiss()
            }) {
                Text(stringResource(id = R.string.done))
            }
        }
    )
}

@Composable
fun MixingInfoDialog(onDismiss: () -> Unit) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Guide", "EQ Starting Points")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Mixing & EQ Reference")
                Text(
                    text = "Use this as a starting point, then trust the room, the source, and your ears.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                PrimaryTabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) }
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (selectedTab == 0) {
                        InfoSection(
                            title = "Ring Out Feedback Faster",
                            body = "Feedback usually shows up as a narrow, stubborn peak. Raise the source until it starts to ring, watch for the tallest bar or dominant frequency, then make a small cut on that band instead of taking out broad tone."
                        )
                        InfoSection(
                            title = "Use Overlays on Purpose",
                            body = "Choose the source you are actively shaping before you EQ. The overlay makes the common problem and target zones visible so you can look at the analyzer with intent instead of scanning the whole spectrum blindly."
                        )
                        InfoSection(
                            title = "Clear Mud Before You Boost",
                            body = "If a channel feels cloudy, work the low mids first. Cutting a little 200-400 Hz often creates more clarity than boosting top end, and it usually gives you more gain before feedback too."
                        )
                        InfoSection(
                            title = "Calibrate Expectations",
                            body = "A phone mic is a reference tool, not a lab-grade SPL meter. Use the calibration screen and compare against a known meter when accuracy matters, especially for volume policy or hearing safety decisions."
                        )
                        InfoSection(
                            title = "Watch Listener Fatigue",
                            body = "If the room sounds harsh or tiring, check both level and energy buildup around the upper mids. Loudness and an aggressive 2-5 kHz range together usually wear listeners out fastest."
                        )
                    } else {
                        eqReferenceEntries.forEach { entry ->
                            EqReferenceCard(entry = entry)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun InfoSection(title: String, body: String) {
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun EqReferenceCard(entry: EqReferenceEntry) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = entry.role,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
            )
            Text(text = "HPF: ${entry.highPass}", style = MaterialTheme.typography.bodySmall)
            Text(text = "Body: ${entry.body}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            Text(text = "Clarity: ${entry.presence}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            Text(text = "Watch: ${entry.watchOut}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            Text(text = "Start: ${entry.startingMove}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalibrationScreen(analyzer: AudioAnalyzer, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val currentOffset by analyzer.dbOffset.collectAsState()
    val dbLevel by analyzer.dbLevel.collectAsState()
    val dominant by analyzer.dominantFrequency.collectAsState()
    var targetDb by remember { mutableStateOf(85f) }
    var measuring by remember { mutableStateOf(false) }
    var measuredAvg by remember { mutableStateOf<Float?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(id = R.string.calibration_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(id = R.string.cancel))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier
            .padding(innerPadding)
            .padding(16.dp)) {
            Text(text = String.format(Locale.getDefault(), "%.1f dB", dbLevel), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
            Text(text = stringResource(id = R.string.freq_visualizer_semantics, dominant), style = MaterialTheme.typography.bodyMedium)

            Spacer(modifier = Modifier.height(16.dp))

            Text(text = String.format(Locale.getDefault(), stringResource(id = R.string.target_db_label), targetDb))
            Slider(value = targetDb, onValueChange = { targetDb = it }, valueRange = 0f..120f)

            Spacer(modifier = Modifier.height(8.dp))

            Row {
                Button(onClick = {
                    if (!measuring) {
                        measuring = true
                        measuredAvg = null
                        scope.launch {
                            val samples = mutableListOf<Float>()
                            val start = System.currentTimeMillis()
                            while (System.currentTimeMillis() - start < 3000L) {
                                samples.add(analyzer.dbLevel.value)
                                kotlinx.coroutines.delay(100L)
                            }
                            val avg = if (samples.isNotEmpty()) samples.average().toFloat() else 0f
                            measuredAvg = avg
                            measuring = false
                        }
                    }
                }) {
                    Text(stringResource(id = R.string.measure_button))
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(onClick = {
                    val prefs = ctx.getSharedPreferences("audio_prefs", android.content.Context.MODE_PRIVATE)
                    val base = measuredAvg ?: dbLevel
                    val newOffset = currentOffset + (targetDb - base)
                    analyzer.setDbOffset(newOffset)
                    prefs.edit().putFloat("db_offset", newOffset).apply()
                }) {
                    Text(stringResource(id = R.string.set_offset_button))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            measuredAvg?.let { Text(String.format(Locale.getDefault(), stringResource(id = R.string.measured_avg_label), it)) }

            Spacer(modifier = Modifier.height(16.dp))
            Row {
                Button(onClick = {
                    val prefs = ctx.getSharedPreferences("audio_prefs", android.content.Context.MODE_PRIVATE)
                    prefs.edit().putFloat("db_offset", analyzer.dbOffset.value).apply()
                    onClose()
                }) {
                    Text(stringResource(id = R.string.done))
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onClose) { Text(stringResource(id = R.string.cancel)) }
            }
        }
    }
}
