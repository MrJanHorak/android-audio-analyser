package com.example.audioanalyser

import java.util.Locale
import android.Manifest
import android.app.Activity
import android.content.Context
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.ui.graphics.toArgb
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
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private val audioAnalyzer = AudioAnalyzer()
    private val signalGenerator = SignalGenerator()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AudioAnalyserTheme {
                MainScreen(audioAnalyzer, signalGenerator)
            }
        }
    }
}

@Composable
fun GeneratorPanel(generator: SignalGenerator, modifier: Modifier = Modifier) {
    val modes = SignalGenerator.Mode.values()
    var selectedModeIndex by rememberSaveable { mutableStateOf(0) }
    var freq by rememberSaveable { mutableStateOf(1000f) }
    var level by rememberSaveable { mutableStateOf(0.25f) }
    val isRunning by generator.isRunning.collectAsState()

    Column(modifier = modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(text = "Signal generator", style = MaterialTheme.typography.labelLarge)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            modes.forEachIndexed { idx, m ->
                FilterChip(
                    selected = selectedModeIndex == idx,
                    onClick = { selectedModeIndex = idx },
                    label = { Text(m.name) }
                )
            }
        }

        if (modes[selectedModeIndex] == SignalGenerator.Mode.SINE) {
            Text(text = "Frequency: ${freq.toInt()} Hz", modifier = Modifier.padding(top = 8.dp))
            Slider(value = freq, onValueChange = { freq = it }, valueRange = 20f..20000f)
        }

        Text(text = "Level", modifier = Modifier.padding(top = 8.dp))
        Slider(value = level, onValueChange = { level = it }, valueRange = 0f..1f)

        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { generator.start(modes[selectedModeIndex], freq, level) }, enabled = !isRunning) {
                Text("Start")
            }
            Button(onClick = { generator.stop() }, enabled = isRunning) {
                Text("Stop")
            }
        }
    }
}


data class SpectrumSnapshot(
    val frequencies: FloatArray,
    val dominantFrequency: Float,
    val dbLevel: Float
)

private const val AUDIO_PREFS_NAME = "audio_prefs"
private const val PREF_SELECTED_OVERLAY_ID = "selected_overlay_id"
private const val PREF_SELECTED_TARGET_CURVE_ID = "selected_target_curve_id"
private const val PREF_SAVED_TARGET_CURVES_JSON = "saved_target_curves_json"
private const val PREF_FEEDBACK_HUNT_ENABLED = "feedback_hunt_enabled"
private const val PREF_BIG_GRAPH_MODE = "big_graph_mode"
private const val PREF_WATERFALL_MIN_PERCENTILE = "waterfall_min_percentile"
private const val PREF_WATERFALL_MAX_PERCENTILE = "waterfall_max_percentile"
private const val PREF_WATERFALL_GAMMA = "waterfall_gamma"
private const val PREF_WATERFALL_COLORMAP = "waterfall_colormap"
private const val ANALYZER_BACKUP_FILE_NAME = "audio-analyser-backup.json"
private const val ANALYZER_BACKUP_VERSION = 1

private data class AnalyzerPreferenceState(
    val dbOffset: Float = 30f,
    val noiseThreshold: Float = 25f,
    val feedbackHuntEnabled: Boolean = false,
    val bigGraphModeEnabled: Boolean = true,
    val selectedOverlayId: String = analyzerOverlayProfiles.first().id,
    val selectedTargetCurveId: String = analyzerTargetCurveProfiles.first().id
)

private data class AnalyzerBackupPayload(
    val preferences: AnalyzerPreferenceState,
    val savedTargetCurves: List<SavedTargetCurvePreset>
)

private fun parseSavedTargetCurvePresets(jsonArray: JSONArray): List<SavedTargetCurvePreset> {
    val defaults = defaultCustomTargetCurveSettings()

    return buildList {
        for (index in 0 until jsonArray.length()) {
            val item = jsonArray.optJSONObject(index) ?: continue
            val id = item.optString("id")
            if (id.isBlank()) continue
            val pointsJson = item.optJSONArray("points")
            val points = customTargetCurveFrequencies.indices.map { pointIndex ->
                pointsJson?.optDouble(pointIndex, defaults.points.getOrElse(pointIndex) { 0f }.toDouble())?.toFloat()
                    ?: defaults.points.getOrElse(pointIndex) { 0f }
            }

            add(
                SavedTargetCurvePreset(
                    id = id,
                    settings = CustomTargetCurveSettings(
                        name = item.optString("name").ifBlank { defaults.name },
                        points = points
                    ),
                    venueName = item.optString("venueName"),
                    venueCategory = item.optString("venueCategory"),
                    deskName = item.optString("deskName"),
                    paSystem = item.optString("paSystem"),
                    roomSize = item.optString("roomSize"),
                    problemBands = item.optString("problemBands"),
                    lastUsedAtEpochMillis = item.optLong("lastUsedAtEpochMillis", item.optLong("lastUsedAt", 0L)),
                    notes = item.optString("generalNotes").ifBlank { item.optString("notes") }
                )
            )
        }
    }
}

private fun savedTargetCurvePresetsToJsonArray(presets: List<SavedTargetCurvePreset>): JSONArray =
    JSONArray().apply {
        presets.forEach { preset ->
            put(
                JSONObject().apply {
                    put("id", preset.id)
                    put("name", preset.settings.name)
                    put("venueName", preset.venueName)
                    put("venueCategory", preset.venueCategory)
                    put("deskName", preset.deskName)
                    put("paSystem", preset.paSystem)
                    put("roomSize", preset.roomSize)
                    put("problemBands", preset.problemBands)
                    put("lastUsedAtEpochMillis", preset.lastUsedAtEpochMillis)
                    put("lastUsedAt", preset.lastUsedAtEpochMillis)
                    put("generalNotes", preset.notes)
                    put("notes", preset.notes)
                    put(
                        "points",
                        JSONArray().apply {
                            preset.settings.points.forEach { put(it.toDouble()) }
                        }
                    )
                }
            )
        }
    }

private fun sortSavedTargetCurvePresets(presets: List<SavedTargetCurvePreset>): List<SavedTargetCurvePreset> =
    presets.sortedWith(
        compareByDescending<SavedTargetCurvePreset> { it.lastUsedAtEpochMillis }
            .thenBy { it.venueCategory.lowercase(Locale.getDefault()) }
            .thenBy { it.displayName().lowercase(Locale.getDefault()) }
    )

private fun markSavedTargetCurvePresetUsed(
    presets: List<SavedTargetCurvePreset>,
    presetId: String,
    nowMillis: Long = System.currentTimeMillis()
): List<SavedTargetCurvePreset> {
    var found = false
    val updatedPresets = presets.map { preset ->
        if (preset.id == presetId) {
            found = true
            preset.markUsed(nowMillis)
        } else {
            preset
        }
    }

    return if (found) sortSavedTargetCurvePresets(updatedPresets) else presets
}

private fun loadSavedTargetCurvePresets(context: Context): List<SavedTargetCurvePreset> {
    val prefs = context.getSharedPreferences(AUDIO_PREFS_NAME, Context.MODE_PRIVATE)
    val rawJson = prefs.getString(PREF_SAVED_TARGET_CURVES_JSON, null) ?: return emptyList()
    return runCatching {
        sortSavedTargetCurvePresets(parseSavedTargetCurvePresets(JSONArray(rawJson)))
    }.getOrDefault(emptyList())
}

private fun saveSavedTargetCurvePresets(context: Context, presets: List<SavedTargetCurvePreset>) {
    val prefs = context.getSharedPreferences(AUDIO_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putString(PREF_SAVED_TARGET_CURVES_JSON, savedTargetCurvePresetsToJsonArray(presets).toString()).apply()
}

private fun loadAnalyzerPreferenceState(context: Context): AnalyzerPreferenceState {
    val prefs = context.getSharedPreferences(AUDIO_PREFS_NAME, Context.MODE_PRIVATE)
    return AnalyzerPreferenceState(
        dbOffset = prefs.getFloat("db_offset", 30f),
        noiseThreshold = prefs.getFloat("noise_threshold", 25f),
        feedbackHuntEnabled = prefs.getBoolean(PREF_FEEDBACK_HUNT_ENABLED, false),
        bigGraphModeEnabled = prefs.getBoolean(PREF_BIG_GRAPH_MODE, true),
        selectedOverlayId = prefs.getString(PREF_SELECTED_OVERLAY_ID, analyzerOverlayProfiles.first().id)
            ?: analyzerOverlayProfiles.first().id,
        selectedTargetCurveId = prefs.getString(PREF_SELECTED_TARGET_CURVE_ID, analyzerTargetCurveProfiles.first().id)
            ?: analyzerTargetCurveProfiles.first().id
    )
}

private fun persistCalibrationPreferences(context: Context, dbOffset: Float, noiseThreshold: Float) {
    context.getSharedPreferences(AUDIO_PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putFloat("db_offset", dbOffset)
        .putFloat("noise_threshold", noiseThreshold)
        .apply()
}

private fun buildAnalyzerBackupJson(
    preferenceState: AnalyzerPreferenceState,
    savedTargetCurves: List<SavedTargetCurvePreset>
): String =
    JSONObject().apply {
        put("formatVersion", ANALYZER_BACKUP_VERSION)
        put(
            "preferences",
            JSONObject().apply {
                put("dbOffset", preferenceState.dbOffset.toDouble())
                put("noiseThreshold", preferenceState.noiseThreshold.toDouble())
                put("feedbackHuntEnabled", preferenceState.feedbackHuntEnabled)
                put("bigGraphModeEnabled", preferenceState.bigGraphModeEnabled)
                put("selectedOverlayId", preferenceState.selectedOverlayId)
                put("selectedTargetCurveId", preferenceState.selectedTargetCurveId)
            }
        )
        put("savedTargetCurves", savedTargetCurvePresetsToJsonArray(savedTargetCurves))
    }.toString(2)

private fun parseAnalyzerBackup(rawJson: String): AnalyzerBackupPayload? = runCatching {
    val root = JSONObject(rawJson)
    val preferencesJson = root.optJSONObject("preferences")
    val defaultPreferences = AnalyzerPreferenceState()
    val savedCurvesJson = root.optJSONArray("savedTargetCurves")
        ?: root.optJSONArray("savedCurves")
        ?: JSONArray()

    AnalyzerBackupPayload(
        preferences = AnalyzerPreferenceState(
            dbOffset = preferencesJson?.optDouble("dbOffset", defaultPreferences.dbOffset.toDouble())?.toFloat()
                ?: defaultPreferences.dbOffset,
            noiseThreshold = preferencesJson?.optDouble("noiseThreshold", defaultPreferences.noiseThreshold.toDouble())?.toFloat()
                ?: defaultPreferences.noiseThreshold,
            feedbackHuntEnabled = preferencesJson?.optBoolean("feedbackHuntEnabled", defaultPreferences.feedbackHuntEnabled)
                ?: defaultPreferences.feedbackHuntEnabled,
            bigGraphModeEnabled = preferencesJson?.optBoolean("bigGraphModeEnabled", defaultPreferences.bigGraphModeEnabled)
                ?: defaultPreferences.bigGraphModeEnabled,
            selectedOverlayId = preferencesJson?.optString("selectedOverlayId").orEmpty()
                .ifBlank { defaultPreferences.selectedOverlayId },
            selectedTargetCurveId = preferencesJson?.optString("selectedTargetCurveId").orEmpty()
                .ifBlank { defaultPreferences.selectedTargetCurveId }
        ),
        savedTargetCurves = sortSavedTargetCurvePresets(parseSavedTargetCurvePresets(savedCurvesJson))
    )
}.getOrNull()

private fun readTextFromUri(context: Context, uri: Uri): String? {
    val inputStream = context.contentResolver.openInputStream(uri) ?: return null
    return runCatching {
        inputStream.bufferedReader().use { it.readText() }
    }.getOrNull()
}

private fun writeTextToUri(context: Context, uri: Uri, contents: String): Boolean {
    val outputStream = context.contentResolver.openOutputStream(uri) ?: return false
    return runCatching {
        outputStream.bufferedWriter().use { it.write(contents) }
        true
    }.getOrDefault(false)
}

@Composable
fun MainScreen(analyzer: AudioAnalyzer, generator: SignalGenerator) {
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
        AudioAnalyserContent(analyzer, generator)
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
fun AudioAnalyserContent(analyzer: AudioAnalyzer, generator: SignalGenerator) {
    val dbLevel by analyzer.dbLevel.collectAsState()
    val dbLevelA by analyzer.dbLevelA.collectAsState()
    val dbLevelC by analyzer.dbLevelC.collectAsState()
    val dbLevelZ by analyzer.dbLevelZ.collectAsState()
    val minDb by analyzer.minDb.collectAsState()
    val maxDb by analyzer.maxDb.collectAsState()
    val avgDb by analyzer.avgDb.collectAsState()
    val dbHistory by analyzer.dbHistory.collectAsState()
    val frequencies by analyzer.frequencies.collectAsState()
    val spectrogram by analyzer.spectrogram.collectAsState(initial = emptyList())
    val dbOffset by analyzer.dbOffset.collectAsState()
    val noiseThreshold by analyzer.noiseThreshold.collectAsState()
    val dominantFrequency by analyzer.dominantFrequency.collectAsState()
    val feedbackPeaks by analyzer.feedbackPeaks.collectAsState()
    val error by analyzer.error.collectAsState()

    var showInfoDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showCalibrationScreen by remember { mutableStateOf(false) }
    var feedbackHuntEnabled by rememberSaveable { mutableStateOf(false) }
    var bigGraphModeEnabled by rememberSaveable { mutableStateOf(true) }
    var spectrumSnapshot by remember { mutableStateOf<SpectrumSnapshot?>(null) }
    var snapshotCompareEnabled by rememberSaveable { mutableStateOf(false) }
    var preferencesLoaded by remember { mutableStateOf(false) }
    var settingsBackupMessage by remember { mutableStateOf<String?>(null) }
    val overlayProfiles = remember { analyzerOverlayProfiles }
    var selectedOverlayId by rememberSaveable { mutableStateOf(overlayProfiles.first().id) }
    val selectedOverlay = overlayProfiles.firstOrNull { it.id == selectedOverlayId } ?: overlayProfiles.first()
    var savedTargetCurves by remember { mutableStateOf(emptyList<SavedTargetCurvePreset>()) }
    val targetCurveProfiles = remember(savedTargetCurves) { buildAnalyzerTargetCurveProfiles(savedTargetCurves) }
    var selectedTargetCurveId by rememberSaveable { mutableStateOf(analyzerTargetCurveProfiles.first().id) }
    val selectedTargetCurve = targetCurveProfiles.firstOrNull { it.id == selectedTargetCurveId } ?: targetCurveProfiles.first()

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Load persisted calibration values (if any)
    val ctx = LocalContext.current

    fun selectTargetCurve(profile: AnalyzerTargetCurveProfile) {
        selectedTargetCurveId = profile.id

        if (savedTargetCurves.none { it.id == profile.id }) {
            return
        }

        val updatedPresets = markSavedTargetCurvePresetUsed(savedTargetCurves, profile.id)
        if (updatedPresets != savedTargetCurves) {
            savedTargetCurves = updatedPresets
            saveSavedTargetCurvePresets(ctx, updatedPresets)
        }
    }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        settingsBackupMessage = when {
            uri == null -> "Backup export cancelled."
            writeTextToUri(
                context = ctx,
                uri = uri,
                contents = buildAnalyzerBackupJson(
                    preferenceState = AnalyzerPreferenceState(
                        dbOffset = dbOffset,
                        noiseThreshold = noiseThreshold,
                        feedbackHuntEnabled = feedbackHuntEnabled,
                        bigGraphModeEnabled = bigGraphModeEnabled,
                        selectedOverlayId = selectedOverlayId,
                        selectedTargetCurveId = selectedTargetCurveId
                    ),
                    savedTargetCurves = savedTargetCurves
                )
            ) -> "Backup exported to the selected file."
            else -> "Couldn't export the backup file."
        }
    }
    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            settingsBackupMessage = "Backup import cancelled."
        } else {
            val importedBackup = readTextFromUri(ctx, uri)?.let(::parseAnalyzerBackup)
            if (importedBackup == null) {
                settingsBackupMessage = "Couldn't read that backup file."
            } else {
                val importedPreferences = importedBackup.preferences
                val importedCurves = sortSavedTargetCurvePresets(importedBackup.savedTargetCurves)
                val importedProfiles = buildAnalyzerTargetCurveProfiles(importedCurves)

                analyzer.setDbOffset(importedPreferences.dbOffset)
                analyzer.setNoiseThreshold(importedPreferences.noiseThreshold)
                persistCalibrationPreferences(ctx, importedPreferences.dbOffset, importedPreferences.noiseThreshold)

                savedTargetCurves = importedCurves
                saveSavedTargetCurvePresets(ctx, importedCurves)
                feedbackHuntEnabled = importedPreferences.feedbackHuntEnabled
                bigGraphModeEnabled = importedPreferences.bigGraphModeEnabled
                selectedOverlayId = importedPreferences.selectedOverlayId
                    .takeIf { savedId -> overlayProfiles.any { it.id == savedId } }
                    ?: overlayProfiles.first().id
                selectedTargetCurveId = importedPreferences.selectedTargetCurveId
                    .takeIf { savedId -> importedProfiles.any { it.id == savedId } }
                    ?: importedProfiles.first().id
                spectrumSnapshot = null
                snapshotCompareEnabled = false
                settingsBackupMessage = "Backup imported. Saved curves and preferences updated."
            }
        }
    }

    LaunchedEffect(Unit) {
        val savedPreferences = loadAnalyzerPreferenceState(ctx)
        val loadedSavedTargetCurves = loadSavedTargetCurvePresets(ctx)
        val loadedTargetCurveProfiles = buildAnalyzerTargetCurveProfiles(loadedSavedTargetCurves)

        analyzer.setDbOffset(savedPreferences.dbOffset)
        analyzer.setNoiseThreshold(savedPreferences.noiseThreshold)
        feedbackHuntEnabled = savedPreferences.feedbackHuntEnabled
        bigGraphModeEnabled = savedPreferences.bigGraphModeEnabled
        savedTargetCurves = loadedSavedTargetCurves
        selectedOverlayId = savedPreferences.selectedOverlayId
            ?.takeIf { savedId -> overlayProfiles.any { it.id == savedId } }
            ?: overlayProfiles.first().id
        selectedTargetCurveId = savedPreferences.selectedTargetCurveId
            .takeIf { savedId -> loadedTargetCurveProfiles.any { it.id == savedId } }
            ?: loadedTargetCurveProfiles.first().id
        preferencesLoaded = true
    }

    LaunchedEffect(targetCurveProfiles, selectedTargetCurveId) {
        if (targetCurveProfiles.none { it.id == selectedTargetCurveId }) {
            selectedTargetCurveId = targetCurveProfiles.first().id
        }
    }

    LaunchedEffect(preferencesLoaded, selectedOverlayId) {
        if (!preferencesLoaded) return@LaunchedEffect
        ctx.getSharedPreferences(AUDIO_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_SELECTED_OVERLAY_ID, selectedOverlayId)
            .apply()
    }

    LaunchedEffect(preferencesLoaded, selectedTargetCurveId) {
        if (!preferencesLoaded) return@LaunchedEffect
        ctx.getSharedPreferences(AUDIO_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_SELECTED_TARGET_CURVE_ID, selectedTargetCurveId)
            .apply()
    }

    LaunchedEffect(preferencesLoaded, feedbackHuntEnabled) {
        if (!preferencesLoaded) return@LaunchedEffect
        ctx.getSharedPreferences(AUDIO_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_FEEDBACK_HUNT_ENABLED, feedbackHuntEnabled)
            .apply()
    }

    LaunchedEffect(preferencesLoaded, bigGraphModeEnabled) {
        if (!preferencesLoaded) return@LaunchedEffect
        ctx.getSharedPreferences(AUDIO_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_BIG_GRAPH_MODE, bigGraphModeEnabled)
            .apply()
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
                    IconButton(onClick = {
                        settingsBackupMessage = null
                        showSettingsDialog = true
                    }) {
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
                    dbLevelA = dbLevelA,
                    dbLevelC = dbLevelC,
                    dbLevelZ = dbLevelZ,
                    spectrogram = spectrogram,
                    minDb = minDb,
                    maxDb = maxDb,
                    avgDb = avgDb,
                    dbHistory = dbHistory,
                    frequencies = frequencies,
                    dominantFrequency = dominantFrequency,
                    feedbackPeaks = feedbackPeaks,
                    feedbackHuntEnabled = feedbackHuntEnabled,
                    bigGraphModeEnabled = bigGraphModeEnabled,
                    spectrumSnapshot = spectrumSnapshot,
                    snapshotCompareEnabled = snapshotCompareEnabled,
                    selectedOverlay = selectedOverlay,
                    selectedTargetCurve = selectedTargetCurve,
                    savedTargetCurves = savedTargetCurves,
                    overlayProfiles = overlayProfiles,
                    targetCurveProfiles = targetCurveProfiles,
                    onOverlaySelected = { selectedOverlayId = it.id },
                    onTargetCurveSelected = ::selectTargetCurve,
                    onUpsertSavedTargetCurve = { preset ->
                        val presetToStore = preset.markUsed()
                        val updatedPresets = sortSavedTargetCurvePresets(savedTargetCurves.filterNot { it.id == presetToStore.id } + presetToStore)
                        savedTargetCurves = updatedPresets
                        saveSavedTargetCurvePresets(ctx, updatedPresets)
                        selectedTargetCurveId = presetToStore.id
                    },
                    onDeleteSavedTargetCurve = { presetId ->
                        val updatedPresets = savedTargetCurves.filterNot { it.id == presetId }
                        savedTargetCurves = updatedPresets
                        saveSavedTargetCurvePresets(ctx, updatedPresets)
                        if (selectedTargetCurveId == presetId) {
                            selectedTargetCurveId = analyzerTargetCurveProfiles.first().id
                        }
                    },
                    onFeedbackHuntEnabledChange = { feedbackHuntEnabled = it },
                    onBigGraphModeEnabledChange = { bigGraphModeEnabled = it },
                    onCaptureSnapshot = {
                        spectrumSnapshot = SpectrumSnapshot(
                            frequencies = frequencies.copyOf(),
                            dominantFrequency = dominantFrequency,
                            dbLevel = dbLevel
                        )
                        snapshotCompareEnabled = true
                    },
                    onSnapshotCompareEnabledChange = { snapshotCompareEnabled = it },
                    onClearSnapshot = {
                        spectrumSnapshot = null
                        snapshotCompareEnabled = false
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    generator = generator
                )
            } else {
                PortraitLayout(
                    dbLevel = dbLevel,
                    dbLevelA = dbLevelA,
                    dbLevelC = dbLevelC,
                    dbLevelZ = dbLevelZ,
                    spectrogram = spectrogram,
                    minDb = minDb,
                    maxDb = maxDb,
                    avgDb = avgDb,
                    dbHistory = dbHistory,
                    frequencies = frequencies,
                    dominantFrequency = dominantFrequency,
                    feedbackPeaks = feedbackPeaks,
                    feedbackHuntEnabled = feedbackHuntEnabled,
                    bigGraphModeEnabled = bigGraphModeEnabled,
                    spectrumSnapshot = spectrumSnapshot,
                    snapshotCompareEnabled = snapshotCompareEnabled,
                    selectedOverlay = selectedOverlay,
                    selectedTargetCurve = selectedTargetCurve,
                    savedTargetCurves = savedTargetCurves,
                    overlayProfiles = overlayProfiles,
                    targetCurveProfiles = targetCurveProfiles,
                    onOverlaySelected = { selectedOverlayId = it.id },
                    onTargetCurveSelected = ::selectTargetCurve,
                    onUpsertSavedTargetCurve = { preset ->
                        val presetToStore = preset.markUsed()
                        val updatedPresets = sortSavedTargetCurvePresets(savedTargetCurves.filterNot { it.id == presetToStore.id } + presetToStore)
                        savedTargetCurves = updatedPresets
                        saveSavedTargetCurvePresets(ctx, updatedPresets)
                        selectedTargetCurveId = presetToStore.id
                    },
                    onDeleteSavedTargetCurve = { presetId ->
                        val updatedPresets = savedTargetCurves.filterNot { it.id == presetId }
                        savedTargetCurves = updatedPresets
                        saveSavedTargetCurvePresets(ctx, updatedPresets)
                        if (selectedTargetCurveId == presetId) {
                            selectedTargetCurveId = analyzerTargetCurveProfiles.first().id
                        }
                    },
                    onFeedbackHuntEnabledChange = { feedbackHuntEnabled = it },
                    onBigGraphModeEnabledChange = { bigGraphModeEnabled = it },
                    onCaptureSnapshot = {
                        spectrumSnapshot = SpectrumSnapshot(
                            frequencies = frequencies.copyOf(),
                            dominantFrequency = dominantFrequency,
                            dbLevel = dbLevel
                        )
                        snapshotCompareEnabled = true
                    },
                    onSnapshotCompareEnabledChange = { snapshotCompareEnabled = it },
                    onClearSnapshot = {
                        spectrumSnapshot = null
                        snapshotCompareEnabled = false
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    generator = generator
                )
            }
        }
    }

    if (showSettingsDialog) {
        SettingsDialog(
            currentOffset = dbOffset,
            currentThreshold = noiseThreshold,
            importExportMessage = settingsBackupMessage,
            onOffsetChange = { analyzer.setDbOffset(it) },
            onThresholdChange = { analyzer.setNoiseThreshold(it) },
            onExportBackup = { exportBackupLauncher.launch(ANALYZER_BACKUP_FILE_NAME) },
            onImportBackup = { importBackupLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
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
    dbLevelA: Float,
    dbLevelC: Float,
    dbLevelZ: Float,
    spectrogram: List<FloatArray>,
    minDb: Float,
    maxDb: Float,
    avgDb: Float,
    dbHistory: List<Float>,
    frequencies: FloatArray,
    dominantFrequency: Float,
    feedbackPeaks: List<FeedbackPeak>,
    feedbackHuntEnabled: Boolean,
    bigGraphModeEnabled: Boolean,
    spectrumSnapshot: SpectrumSnapshot?,
    snapshotCompareEnabled: Boolean,
    selectedOverlay: AnalyzerOverlayProfile,
    selectedTargetCurve: AnalyzerTargetCurveProfile,
    savedTargetCurves: List<SavedTargetCurvePreset>,
    overlayProfiles: List<AnalyzerOverlayProfile>,
    targetCurveProfiles: List<AnalyzerTargetCurveProfile>,
    onOverlaySelected: (AnalyzerOverlayProfile) -> Unit,
    onTargetCurveSelected: (AnalyzerTargetCurveProfile) -> Unit,
    onUpsertSavedTargetCurve: (SavedTargetCurvePreset) -> Unit,
    onDeleteSavedTargetCurve: (String) -> Unit,
    onFeedbackHuntEnabledChange: (Boolean) -> Unit,
    onBigGraphModeEnabledChange: (Boolean) -> Unit,
    onCaptureSnapshot: () -> Unit,
    onSnapshotCompareEnabledChange: (Boolean) -> Unit,
    onClearSnapshot: () -> Unit,
    modifier: Modifier = Modifier,
    generator: SignalGenerator
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        DbMeterCard(
            dbLevel = dbLevel,
            dbLevelA = dbLevelA,
            dbLevelC = dbLevelC,
            dbLevelZ = dbLevelZ,
            minDb = minDb,
            maxDb = maxDb,
            avgDb = avgDb,
            dbHistory = dbHistory,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        VisualizerCard(
            dbLevel = dbLevel,
            frequencies = frequencies,
            dominantFrequency = dominantFrequency,
            feedbackPeaks = feedbackPeaks,
            feedbackHuntEnabled = feedbackHuntEnabled,
            bigGraphModeEnabled = bigGraphModeEnabled,
            spectrumSnapshot = spectrumSnapshot,
            snapshotCompareEnabled = snapshotCompareEnabled,
            selectedOverlay = selectedOverlay,
            selectedTargetCurve = selectedTargetCurve,
            savedTargetCurves = savedTargetCurves,
            overlayProfiles = overlayProfiles,
            targetCurveProfiles = targetCurveProfiles,
            onOverlaySelected = onOverlaySelected,
            onTargetCurveSelected = onTargetCurveSelected,
            onUpsertSavedTargetCurve = onUpsertSavedTargetCurve,
            onDeleteSavedTargetCurve = onDeleteSavedTargetCurve,
            onFeedbackHuntEnabledChange = onFeedbackHuntEnabledChange,
            onBigGraphModeEnabledChange = onBigGraphModeEnabledChange,
            onCaptureSnapshot = onCaptureSnapshot,
            onSnapshotCompareEnabledChange = onSnapshotCompareEnabledChange,
            onClearSnapshot = onClearSnapshot,
            generator = generator,
            spectrogram = spectrogram,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
    }
}

@Composable
fun LandscapeLayout(
    dbLevel: Float,
    dbLevelA: Float,
    dbLevelC: Float,
    dbLevelZ: Float,
    spectrogram: List<FloatArray>,
    minDb: Float,
    maxDb: Float,
    avgDb: Float,
    dbHistory: List<Float>,
    frequencies: FloatArray,
    dominantFrequency: Float,
    feedbackPeaks: List<FeedbackPeak>,
    feedbackHuntEnabled: Boolean,
    bigGraphModeEnabled: Boolean,
    spectrumSnapshot: SpectrumSnapshot?,
    snapshotCompareEnabled: Boolean,
    selectedOverlay: AnalyzerOverlayProfile,
    selectedTargetCurve: AnalyzerTargetCurveProfile,
    savedTargetCurves: List<SavedTargetCurvePreset>,
    overlayProfiles: List<AnalyzerOverlayProfile>,
    targetCurveProfiles: List<AnalyzerTargetCurveProfile>,
    onOverlaySelected: (AnalyzerOverlayProfile) -> Unit,
    onTargetCurveSelected: (AnalyzerTargetCurveProfile) -> Unit,
    onUpsertSavedTargetCurve: (SavedTargetCurvePreset) -> Unit,
    onDeleteSavedTargetCurve: (String) -> Unit,
    onFeedbackHuntEnabledChange: (Boolean) -> Unit,
    onBigGraphModeEnabledChange: (Boolean) -> Unit,
    onCaptureSnapshot: () -> Unit,
    onSnapshotCompareEnabledChange: (Boolean) -> Unit,
    onClearSnapshot: () -> Unit,
    modifier: Modifier = Modifier,
    generator: SignalGenerator
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        DbMeterCard(
            dbLevel = dbLevel,
            dbLevelA = dbLevelA,
            dbLevelC = dbLevelC,
            dbLevelZ = dbLevelZ,
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
            dbLevel = dbLevel,
            frequencies = frequencies,
            dominantFrequency = dominantFrequency,
            feedbackPeaks = feedbackPeaks,
            feedbackHuntEnabled = feedbackHuntEnabled,
            bigGraphModeEnabled = bigGraphModeEnabled,
            spectrumSnapshot = spectrumSnapshot,
            snapshotCompareEnabled = snapshotCompareEnabled,
            selectedOverlay = selectedOverlay,
            selectedTargetCurve = selectedTargetCurve,
            savedTargetCurves = savedTargetCurves,
            overlayProfiles = overlayProfiles,
            targetCurveProfiles = targetCurveProfiles,
            onOverlaySelected = onOverlaySelected,
            onTargetCurveSelected = onTargetCurveSelected,
            onUpsertSavedTargetCurve = onUpsertSavedTargetCurve,
            onDeleteSavedTargetCurve = onDeleteSavedTargetCurve,
            onFeedbackHuntEnabledChange = onFeedbackHuntEnabledChange,
            onBigGraphModeEnabledChange = onBigGraphModeEnabledChange,
            onCaptureSnapshot = onCaptureSnapshot,
            onSnapshotCompareEnabledChange = onSnapshotCompareEnabledChange,
            onClearSnapshot = onClearSnapshot,
            generator = generator,
            spectrogram = spectrogram,
            modifier = Modifier
                .weight(0.6f)
                .fillMaxHeight()
        )
    }
}

@Composable
fun DbMeterCard(
    dbLevel: Float,
    dbLevelA: Float = 0f,
    dbLevelC: Float = 0f,
    dbLevelZ: Float = 0f,
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
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Text(text = String.format(Locale.getDefault(), "dB(A): %.1f", dbLevelA), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = String.format(Locale.getDefault(), "dB(C): %.1f", dbLevelC), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = String.format(Locale.getDefault(), "dB(Z): %.1f", dbLevelZ), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisualizerCard(
    dbLevel: Float,
    frequencies: FloatArray,
    dominantFrequency: Float,
    feedbackPeaks: List<FeedbackPeak>,
    feedbackHuntEnabled: Boolean,
    bigGraphModeEnabled: Boolean,
    spectrumSnapshot: SpectrumSnapshot?,
    snapshotCompareEnabled: Boolean,
    selectedOverlay: AnalyzerOverlayProfile,
    selectedTargetCurve: AnalyzerTargetCurveProfile,
    savedTargetCurves: List<SavedTargetCurvePreset>,
    overlayProfiles: List<AnalyzerOverlayProfile>,
    targetCurveProfiles: List<AnalyzerTargetCurveProfile>,
    onOverlaySelected: (AnalyzerOverlayProfile) -> Unit,
    onTargetCurveSelected: (AnalyzerTargetCurveProfile) -> Unit,
    onUpsertSavedTargetCurve: (SavedTargetCurvePreset) -> Unit,
    onDeleteSavedTargetCurve: (String) -> Unit,
    onFeedbackHuntEnabledChange: (Boolean) -> Unit,
    onBigGraphModeEnabledChange: (Boolean) -> Unit,
    onCaptureSnapshot: () -> Unit,
    onSnapshotCompareEnabledChange: (Boolean) -> Unit,
    onClearSnapshot: () -> Unit,
    modifier: Modifier = Modifier,
    generator: SignalGenerator,
    spectrogram: List<FloatArray>
) {
    val ctx = LocalContext.current
    // Waterfall settings (persisted in shared prefs)
    var waterfallMinPercentile by rememberSaveable { mutableStateOf(0.05f) }
    var waterfallMaxPercentile by rememberSaveable { mutableStateOf(0.95f) }
    var waterfallGamma by rememberSaveable { mutableStateOf(0.6f) }

    LaunchedEffect(Unit) {
        val prefs = ctx.getSharedPreferences(AUDIO_PREFS_NAME, Context.MODE_PRIVATE)
        waterfallMinPercentile = prefs.getFloat(PREF_WATERFALL_MIN_PERCENTILE, 0.05f)
        waterfallMaxPercentile = prefs.getFloat(PREF_WATERFALL_MAX_PERCENTILE, 0.95f)
        waterfallGamma = prefs.getFloat(PREF_WATERFALL_GAMMA, 0.6f)
    }

    LaunchedEffect(waterfallMinPercentile) {
        val prefs = ctx.getSharedPreferences(AUDIO_PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putFloat(PREF_WATERFALL_MIN_PERCENTILE, waterfallMinPercentile).apply()
    }
    LaunchedEffect(waterfallMaxPercentile) {
        val prefs = ctx.getSharedPreferences(AUDIO_PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putFloat(PREF_WATERFALL_MAX_PERCENTILE, waterfallMaxPercentile).apply()
    }
    LaunchedEffect(waterfallGamma) {
        val prefs = ctx.getSharedPreferences(AUDIO_PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putFloat(PREF_WATERFALL_GAMMA, waterfallGamma).apply()
    }
    var waterfallColormap by rememberSaveable { mutableStateOf("inferno") }
    LaunchedEffect(Unit) {
        val prefs = ctx.getSharedPreferences(AUDIO_PREFS_NAME, Context.MODE_PRIVATE)
        waterfallColormap = prefs.getString(PREF_WATERFALL_COLORMAP, "inferno") ?: "inferno"
    }
    LaunchedEffect(waterfallColormap) {
        val prefs = ctx.getSharedPreferences(AUDIO_PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(PREF_WATERFALL_COLORMAP, waterfallColormap).apply()
    }

    // Export waterfall sample (JSON) launcher
    val exportSampleJson = remember { mutableStateOf<String?>(null) }
    val exportMessage = remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) {
            exportMessage.value = "Export cancelled"
            exportSampleJson.value = null
        } else {
            val json = exportSampleJson.value ?: "{}"
            val success = writeTextToUri(ctx, uri, json)
            exportMessage.value = if (success) "Waterfall sample exported." else "Export failed."
            exportSampleJson.value = null
        }
    }

    fun exportCurrentWaterfall() {
        // Build JSON from spectrogram
        val root = JSONObject()
        root.put("formatVersion", 1)
        root.put("timestamp", System.currentTimeMillis())
        root.put("rows", spectrogram.size)
        root.put("cols", if (spectrogram.isNotEmpty()) spectrogram[0].size else 0)
        val frames = JSONArray()
        spectrogram.forEach { frame ->
            val arr = JSONArray()
            frame.forEach { f -> arr.put(f.toDouble()) }
            frames.put(arr)
        }
        root.put("frames", frames)
        exportSampleJson.value = root.toString(2)
        exportLauncher.launch("spectrogram-sample-${System.currentTimeMillis()}.json")
    }
    var showAnalyzerTools by remember { mutableStateOf(false) }
    var showSavedCurvesDialog by remember { mutableStateOf(false) }
    var showWaterfall by rememberSaveable { mutableStateOf(false) }
    val matchedBand = selectedOverlay.bandFor(dominantFrequency)
    val primaryFeedbackPeak = feedbackPeaks.firstOrNull()
    val snapshotDeltaHz = spectrumSnapshot?.let { dominantFrequency - it.dominantFrequency }
    val peakFocusSummary = when {
        matchedBand != null -> "Peak focus: ${matchedBand.label} (${formatFrequencyRange(matchedBand.startHz, matchedBand.endHz)})"
        selectedOverlay.bands.isNotEmpty() -> "Peak focus: outside ${selectedOverlay.label} guide bands"
        else -> "No source guide selected."
    }
    val snapshotSummary = spectrumSnapshot?.let { snapshot ->
        if (snapshotCompareEnabled) {
            "Snapshot delta ${formatSignedFrequencyDelta(snapshotDeltaHz ?: 0f)} and ${formatSignedDbDelta(dbLevel - snapshot.dbLevel)}."
        } else {
            "Snapshot saved at ${formatFrequencyLabel(snapshot.dominantFrequency)} and ${String.format(Locale.getDefault(), "%.1f dB", snapshot.dbLevel)}."
        }
    }
    val semanticsDesc = buildString {
        append(String.format(Locale.getDefault(), "Frequency visualizer. Dominant frequency %.0f Hz. ", dominantFrequency))
        append(peakFocusSummary)
        if (bigGraphModeEnabled) {
            append(" Big graph mode enabled.")
        }
        if (selectedTargetCurve.isEnabled) {
            append(" Target curve ${selectedTargetCurve.label} selected.")
        }
        if (feedbackHuntEnabled && primaryFeedbackPeak != null) {
            append(" Feedback hunt suggests ${formatFrequencyLabel(primaryFeedbackPeak.suggestedCutHz)}.")
        }
        if (snapshotCompareEnabled && spectrumSnapshot != null) {
            append(" Snapshot compare enabled.")
        }
    }

    ElevatedCard(modifier = modifier.semantics { contentDescription = semanticsDesc }) {
        if (bigGraphModeEnabled) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxSize()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "FOH graph mode",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatFrequencyLabel(dominantFrequency),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        if (feedbackHuntEnabled && primaryFeedbackPeak != null) {
                            Text(
                                text = "Watch ${formatFrequencyLabel(primaryFeedbackPeak.suggestedCutHz)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }

                    TextButton(onClick = { showAnalyzerTools = true }) {
                        Text("Tools")
                    }
                }

                if (showWaterfall) {
                    WaterfallVisualizer(
                        spectrogram = spectrogram,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .heightIn(min = 320.dp)
                            .weight(1f),
                        minPercentile = waterfallMinPercentile,
                        maxPercentile = waterfallMaxPercentile,
                                gamma = waterfallGamma,
                                colormapName = waterfallColormap
                    )
                } else {
                    FrequencyVisualizer(
                        dominantFrequency = dominantFrequency,
                        frequencies = frequencies,
                        overlayBands = selectedOverlay.bands,
                        feedbackPeaks = feedbackPeaks,
                        showFeedbackMarkers = feedbackHuntEnabled,
                        snapshotFrequencies = spectrumSnapshot?.frequencies,
                        showSnapshotOverlay = snapshotCompareEnabled,
                        selectedTargetCurve = selectedTargetCurve,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .heightIn(min = 320.dp)
                            .weight(1f)
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxSize()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(id = R.string.frequency_spectrum),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatFrequencyLabel(dominantFrequency),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        Text(
                            text = peakFocusSummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    TextButton(onClick = { showAnalyzerTools = true }) {
                        Text("Tools")
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = onCaptureSnapshot,
                        label = { Text(if (spectrumSnapshot == null) "Capture" else "Refresh") }
                    )
                    AssistChip(
                        onClick = { showAnalyzerTools = true },
                        label = { Text("Source: ${selectedOverlay.label}") }
                    )
                    AssistChip(
                        onClick = { showAnalyzerTools = true },
                        label = { Text("Curve: ${selectedTargetCurve.label}") }
                    )
                    if (feedbackHuntEnabled) {
                        AssistChip(
                            onClick = { showAnalyzerTools = true },
                            label = { Text("Feedback on") }
                        )
                    }
                    if (spectrumSnapshot != null) {
                        AssistChip(
                            onClick = { showAnalyzerTools = true },
                            label = { Text(if (snapshotCompareEnabled) "Compare on" else "Snapshot saved") }
                        )
                    }
                }

                if (snapshotSummary != null) {
                    Text(
                        text = snapshotSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                } else if (selectedTargetCurve.isEnabled) {
                    Text(
                        text = "${selectedTargetCurve.badge}: ${selectedTargetCurve.useCase}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }

                if (showWaterfall) {
                    WaterfallVisualizer(
                        spectrogram = spectrogram,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .heightIn(min = 230.dp)
                            .weight(1f, fill = false),
                        minPercentile = waterfallMinPercentile,
                        maxPercentile = waterfallMaxPercentile,
                        gamma = waterfallGamma,
                        colormapName = waterfallColormap
                    )
                } else {
                    FrequencyVisualizer(
                        dominantFrequency = dominantFrequency,
                        frequencies = frequencies,
                        overlayBands = selectedOverlay.bands,
                        feedbackPeaks = feedbackPeaks,
                        showFeedbackMarkers = feedbackHuntEnabled,
                        snapshotFrequencies = spectrumSnapshot?.frequencies,
                        showSnapshotOverlay = snapshotCompareEnabled,
                        selectedTargetCurve = selectedTargetCurve,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .heightIn(min = 230.dp)
                            .weight(1f, fill = false)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Live bars",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (snapshotCompareEnabled && spectrumSnapshot != null) {
                        Text(
                            text = "Snapshot line",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF00ACC1)
                        )
                    }
                    if (selectedTargetCurve.isEnabled) {
                        Text(
                            text = "Target curve",
                            style = MaterialTheme.typography.labelSmall,
                            color = selectedTargetCurve.color
                        )
                    }
                }

                if (feedbackHuntEnabled) {
                    FeedbackHuntSection(
                        feedbackPeaks = feedbackPeaks,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    )
                }

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

    if (showAnalyzerTools) {
        AnalyzerToolsSheet(
            selectedOverlay = selectedOverlay,
            selectedTargetCurve = selectedTargetCurve,
            savedTargetCurves = savedTargetCurves,
            overlayProfiles = overlayProfiles,
            targetCurveProfiles = targetCurveProfiles,
            feedbackHuntEnabled = feedbackHuntEnabled,
            bigGraphModeEnabled = bigGraphModeEnabled,
            spectrumSnapshot = spectrumSnapshot,
            snapshotCompareEnabled = snapshotCompareEnabled,
            onOverlaySelected = onOverlaySelected,
            onTargetCurveSelected = onTargetCurveSelected,
            onFeedbackHuntEnabledChange = onFeedbackHuntEnabledChange,
            onBigGraphModeEnabledChange = onBigGraphModeEnabledChange,
            onCaptureSnapshot = onCaptureSnapshot,
            onSnapshotCompareEnabledChange = onSnapshotCompareEnabledChange,
            onClearSnapshot = onClearSnapshot,
            onManageSavedCurves = { showSavedCurvesDialog = true },
            onDismiss = { showAnalyzerTools = false },
            showWaterfall = showWaterfall,
            onShowWaterfallChange = { showWaterfall = it },
            waterfallMinPercentile = waterfallMinPercentile,
            onWaterfallMinPercentileChange = { waterfallMinPercentile = it },
            waterfallMaxPercentile = waterfallMaxPercentile,
            onWaterfallMaxPercentileChange = { waterfallMaxPercentile = it },
            waterfallGamma = waterfallGamma,
            onWaterfallGammaChange = { waterfallGamma = it },
            waterfallColormap = waterfallColormap,
            onWaterfallColormapChange = { waterfallColormap = it },
            onExportWaterfallSample = { exportCurrentWaterfall() },
            generator = generator
        )
    }

    if (showSavedCurvesDialog) {
        SavedTargetCurvesDialog(
            savedTargetCurves = savedTargetCurves,
            initialSelectedCurveId = savedTargetCurves.firstOrNull { it.id == selectedTargetCurve.id }?.id,
            onDismiss = { showSavedCurvesDialog = false },
            onUpsertPreset = { preset ->
                onUpsertSavedTargetCurve(preset)
                showSavedCurvesDialog = false
            },
            onDeletePreset = { presetId ->
                onDeleteSavedTargetCurve(presetId)
                showSavedCurvesDialog = false
            }
        )
    }
    if (exportMessage.value != null) {
        AlertDialog(
            onDismissRequest = { exportMessage.value = null },
            confirmButton = {
                TextButton(onClick = { exportMessage.value = null }) {
                    Text("OK")
                }
            },
            title = { Text(stringResource(id = R.string.app_name)) },
            text = { Text(exportMessage.value ?: "") }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyzerToolsSheet(
    selectedOverlay: AnalyzerOverlayProfile,
    selectedTargetCurve: AnalyzerTargetCurveProfile,
    savedTargetCurves: List<SavedTargetCurvePreset>,
    overlayProfiles: List<AnalyzerOverlayProfile>,
    targetCurveProfiles: List<AnalyzerTargetCurveProfile>,
    feedbackHuntEnabled: Boolean,
    bigGraphModeEnabled: Boolean,
    spectrumSnapshot: SpectrumSnapshot?,
    snapshotCompareEnabled: Boolean,
    onOverlaySelected: (AnalyzerOverlayProfile) -> Unit,
    onTargetCurveSelected: (AnalyzerTargetCurveProfile) -> Unit,
    onFeedbackHuntEnabledChange: (Boolean) -> Unit,
    onBigGraphModeEnabledChange: (Boolean) -> Unit,
    onCaptureSnapshot: () -> Unit,
    onSnapshotCompareEnabledChange: (Boolean) -> Unit,
    onClearSnapshot: () -> Unit,
    onManageSavedCurves: () -> Unit,
    onDismiss: () -> Unit,
    showWaterfall: Boolean,
    onShowWaterfallChange: (Boolean) -> Unit,
    waterfallMinPercentile: Float,
    onWaterfallMinPercentileChange: (Float) -> Unit,
    waterfallMaxPercentile: Float,
    onWaterfallMaxPercentileChange: (Float) -> Unit,
    waterfallGamma: Float,
    onWaterfallGammaChange: (Float) -> Unit,
    waterfallColormap: String,
    onWaterfallColormapChange: (String) -> Unit,
    onExportWaterfallSample: () -> Unit,
    generator: SignalGenerator
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Source", "Room", "Snapshot")
    val selectedSavedTargetCurve = savedTargetCurves.firstOrNull { it.id == selectedTargetCurve.id }
    val selectedSavedTargetCurveDetails = selectedSavedTargetCurve?.venueDetailRows().orEmpty()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "Analyzer tools",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Less-used controls and explanations live here so the spectrum stays readable on stage or at FOH.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )

            PrimaryTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            Column(modifier = Modifier.padding(top = 16.dp, bottom = 24.dp)) {
                when (selectedTab) {
                    0 -> {
                        Text(text = stringResource(id = R.string.overlay_selector), style = MaterialTheme.typography.labelLarge)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(top = 8.dp),
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
                        Text(
                            text = selectedOverlay.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                        if (selectedOverlay.bands.isNotEmpty()) {
                            selectedOverlay.bands.forEach { band ->
                                Text(
                                    text = "${band.label}: ${formatFrequencyRange(band.startHz, band.endHz)} - ${band.note}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                            }
                        }

                        // Signal generator controls
                        GeneratorPanel(generator = generator)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Feedback hunt",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Shows repeated narrow peaks and suggested cut frequencies.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = feedbackHuntEnabled,
                                onCheckedChange = onFeedbackHuntEnabledChange
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Waterfall (spectrogram)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Show time-based waterfall view of the spectrum.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = showWaterfall,
                                onCheckedChange = onShowWaterfallChange
                            )
                        }

                        // Waterfall settings: percentiles and gamma
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                            Text(text = "Waterfall settings", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(text = "Min percentile: ${ (waterfallMinPercentile * 100).roundToInt() }%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Slider(
                                value = waterfallMinPercentile,
                                onValueChange = { v ->
                                    val nv = v.coerceIn(0f, (waterfallMaxPercentile - 0.01f).coerceAtLeast(0f))
                                    onWaterfallMinPercentileChange(nv)
                                },
                                valueRange = 0f..0.49f
                            )

                            Text(text = "Max percentile: ${ (waterfallMaxPercentile * 100).roundToInt() }%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Slider(
                                value = waterfallMaxPercentile,
                                onValueChange = { v ->
                                    val nv = v.coerceIn((waterfallMinPercentile + 0.01f).coerceAtLeast(0f), 1f)
                                    onWaterfallMaxPercentileChange(nv)
                                },
                                valueRange = 0.51f..1f
                            )

                            Text(text = "Gamma: ${"%.2f".format(Locale.getDefault(), waterfallGamma)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Slider(
                                value = waterfallGamma,
                                onValueChange = { v -> onWaterfallGammaChange(v.coerceIn(0.25f, 2.0f)) },
                                valueRange = 0.25f..2.0f
                            )
                        }

                        // Colormap selection
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                            Text(text = "Colormap", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val palettes = listOf("inferno" to "Inferno", "viridis" to "Viridis", "plasma" to "Plasma", "magma" to "Magma")
                                palettes.forEach { (id, label) ->
                                    FilterChip(
                                        selected = waterfallColormap == id,
                                        onClick = { onWaterfallColormapChange(id) },
                                        label = { Text(label) }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = { onExportWaterfallSample() }) {
                                Text(text = "Export waterfall sample (JSON)")
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Big graph mode",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Keeps the live screen focused on the graph, dominant frequency, and marker overlays.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = bigGraphModeEnabled,
                                onCheckedChange = onBigGraphModeEnabledChange
                            )
                        }
                    }

                    1 -> {
                        Text(text = "Target curve", style = MaterialTheme.typography.labelLarge)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            targetCurveProfiles.forEach { profile ->
                                val savedPreset = savedTargetCurves.firstOrNull { it.id == profile.id }
                                FilterChip(
                                    selected = profile.id == selectedTargetCurve.id,
                                    onClick = { onTargetCurveSelected(profile) },
                                    label = { Text(savedPreset?.displayName() ?: profile.label) }
                                )
                            }
                        }
                        if (selectedTargetCurve.isEnabled) {
                            Text(
                                text = "${selectedSavedTargetCurve?.displayName() ?: selectedTargetCurve.label} ${selectedTargetCurve.badge}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 10.dp)
                            )
                            Text(
                                text = selectedTargetCurve.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            Text(
                                text = "Best for: ${selectedTargetCurve.useCase}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                            Text(
                                text = "Caution: ${selectedTargetCurve.caution}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        if (selectedSavedTargetCurveDetails.isNotEmpty()) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Venue recall",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    selectedSavedTargetCurveDetails.forEachIndexed { index, (label, value) ->
                                        DetailRow(
                                            label = label,
                                            value = value,
                                            modifier = Modifier.padding(top = if (index == 0) 8.dp else 10.dp)
                                        )
                                    }
                                }
                            }
                        }
                        OutlinedButton(
                            onClick = onManageSavedCurves,
                            modifier = Modifier.padding(top = 16.dp)
                        ) {
                            Text("Manage saved venue curves")
                        }
                    }

                    else -> {
                        Text(text = "Snapshots", style = MaterialTheme.typography.labelLarge)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledTonalButton(onClick = onCaptureSnapshot) {
                                Text(if (spectrumSnapshot == null) "Capture snapshot" else "Refresh snapshot")
                            }
                            if (spectrumSnapshot != null) {
                                FilterChip(
                                    selected = snapshotCompareEnabled,
                                    onClick = { onSnapshotCompareEnabledChange(!snapshotCompareEnabled) },
                                    label = { Text(if (snapshotCompareEnabled) "Compare on" else "Compare off") }
                                )
                                TextButton(onClick = onClearSnapshot) {
                                    Text("Clear")
                                }
                            }
                        }
                        Text(
                            text = spectrumSnapshot?.let { snapshot ->
                                if (snapshotCompareEnabled) {
                                    "Snapshot compare is active. Cyan line shows the saved reference over the live bars."
                                } else {
                                    "Saved snapshot at ${formatFrequencyLabel(snapshot.dominantFrequency)} and ${String.format(Locale.getDefault(), "%.1f dB", snapshot.dbLevel)}."
                                }
                            } ?: "No snapshot saved yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SavedTargetCurvesDialog(
    savedTargetCurves: List<SavedTargetCurvePreset>,
    initialSelectedCurveId: String?,
    onDismiss: () -> Unit,
    onUpsertPreset: (SavedTargetCurvePreset) -> Unit,
    onDeletePreset: (String) -> Unit
) {
    val defaults = defaultCustomTargetCurveSettings()
    var editingCurveId by remember(savedTargetCurves, initialSelectedCurveId) {
        mutableStateOf(initialSelectedCurveId?.takeIf { selectedId -> savedTargetCurves.any { it.id == selectedId } })
    }
    var curveName by remember { mutableStateOf(defaults.name) }
    var venueName by remember { mutableStateOf("") }
    var venueCategory by remember { mutableStateOf("") }
    var deskName by remember { mutableStateOf("") }
    var paSystem by remember { mutableStateOf("") }
    var roomSize by remember { mutableStateOf("") }
    var problemBands by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var pointValues by remember { mutableStateOf(defaults.points) }

    LaunchedEffect(editingCurveId, savedTargetCurves) {
        val preset = savedTargetCurves.firstOrNull { it.id == editingCurveId }
        curveName = preset?.settings?.name ?: defaults.name
        venueName = preset?.venueName.orEmpty()
        venueCategory = preset?.venueCategory.orEmpty()
        deskName = preset?.deskName.orEmpty()
        paSystem = preset?.paSystem.orEmpty()
        roomSize = preset?.roomSize.orEmpty()
        problemBands = preset?.problemBands.orEmpty()
        notes = preset?.notes.orEmpty()
        pointValues = customTargetCurveFrequencies.indices.map { index ->
            preset?.settings?.points?.getOrElse(index) { defaults.points.getOrElse(index) { 0f } }
                ?: defaults.points.getOrElse(index) { 0f }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Saved venue curves") },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(top = 8.dp)
            ) {
                Text(
                    text = "Saved venues are sorted by recent use, then category.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = editingCurveId == null,
                        onClick = { editingCurveId = null },
                        label = { Text("New") }
                    )
                    savedTargetCurves.forEach { preset ->
                        FilterChip(
                            selected = editingCurveId == preset.id,
                            onClick = { editingCurveId = preset.id },
                            label = {
                                Text(preset.displayName())
                            }
                        )
                    }
                }

                OutlinedTextField(
                    value = curveName,
                    onValueChange = { curveName = it },
                    label = { Text("Curve name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                )

                OutlinedTextField(
                    value = venueName,
                    onValueChange = { venueName = it },
                    label = { Text("Venue or organisation") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                )

                Text(
                    text = "Category",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 16.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = venueCategory.isBlank(),
                        onClick = { venueCategory = "" },
                        label = { Text("None") }
                    )
                    venueCategoryOptions.forEach { category ->
                        FilterChip(
                            selected = venueCategory == category,
                            onClick = { venueCategory = category },
                            label = { Text(category) }
                        )
                    }
                }
                if (editingCurveId != null) {
                    Text(
                        text = "Last used ${savedTargetCurves.firstOrNull { it.id == editingCurveId }?.lastUsedLabel() ?: "Not used yet"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }

                Text(
                    text = "Venue recall",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 16.dp)
                )
                Text(
                    text = "Store the practical details you normally need when you walk back into the room.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )

                OutlinedTextField(
                    value = deskName,
                    onValueChange = { deskName = it },
                    label = { Text("Desk or console") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                )

                OutlinedTextField(
                    value = paSystem,
                    onValueChange = { paSystem = it },
                    label = { Text("PA or speaker system") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                )

                OutlinedTextField(
                    value = roomSize,
                    onValueChange = { roomSize = it },
                    label = { Text("Room size or type") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                )

                OutlinedTextField(
                    value = problemBands,
                    onValueChange = { problemBands = it },
                    label = { Text("Known problem bands") },
                    placeholder = { Text("e.g. 315 Hz near stage left, 2.5 kHz on lectern mic") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    minLines = 2,
                    maxLines = 3
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("General notes") },
                    placeholder = { Text("Workflow reminders, patch quirks, or mix notes") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    minLines = 3,
                    maxLines = 5
                )

                Text(
                    text = "Save a reusable target for a venue, organisation, or recurring event mix.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
                )

                customTargetCurveFrequencies.forEachIndexed { index, frequencyHz ->
                    val value = pointValues.getOrElse(index) { defaults.points.getOrElse(index) { 0f } }
                    Text(
                        text = "${formatFrequencyLabel(frequencyHz)} ${formatRelativeCurveLevel(value)}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = if (index == 0) 0.dp else 12.dp)
                    )
                    Slider(
                        value = value,
                        onValueChange = { updatedValue ->
                            pointValues = pointValues.toMutableList().apply { set(index, updatedValue) }
                        },
                        valueRange = -12f..6f
                    )
                }
            }
        },
        dismissButton = {
            Row {
                if (editingCurveId != null) {
                    TextButton(onClick = { onDeletePreset(editingCurveId!!) }) {
                        Text("Delete")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(id = R.string.cancel))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val presetSettings = CustomTargetCurveSettings(
                    name = curveName.ifBlank { defaults.name },
                    points = pointValues
                )
                val preset = if (editingCurveId != null) {
                    SavedTargetCurvePreset(
                        id = editingCurveId!!,
                        settings = presetSettings,
                        venueName = venueName.trim(),
                        venueCategory = venueCategory.trim(),
                        deskName = deskName.trim(),
                        paSystem = paSystem.trim(),
                        roomSize = roomSize.trim(),
                        problemBands = problemBands.trim(),
                        lastUsedAtEpochMillis = savedTargetCurves.firstOrNull { it.id == editingCurveId }?.lastUsedAtEpochMillis ?: 0L,
                        notes = notes.trim()
                    )
                } else {
                    newSavedTargetCurvePreset(
                        settings = presetSettings,
                        venueName = venueName.trim(),
                        venueCategory = venueCategory.trim(),
                        deskName = deskName.trim(),
                        paSystem = paSystem.trim(),
                        roomSize = roomSize.trim(),
                        problemBands = problemBands.trim(),
                        notes = notes.trim()
                    )
                }
                onUpsertPreset(preset)
            }) {
                Text("Save")
            }
        }
    )
}

@Composable
fun FrequencyVisualizer(
    dominantFrequency: Float,
    frequencies: FloatArray,
    overlayBands: List<AnalyzerRangeBand> = emptyList(),
    feedbackPeaks: List<FeedbackPeak> = emptyList(),
    showFeedbackMarkers: Boolean = false,
    snapshotFrequencies: FloatArray? = null,
    showSnapshotOverlay: Boolean = false,
    selectedTargetCurve: AnalyzerTargetCurveProfile = analyzerTargetCurveProfiles.first(),
    modifier: Modifier = Modifier
) {
    val labels = listOf(
        63f to "63",
        125f to "125",
        250f to "250",
        1000f / 2f to "500",
        1000f to "1k",
        2000f to "2k",
        4000f to "4k",
        8000f to "8k",
        16000f to "16k"
    )

    val minFreq = 20f
    val maxFreq = 22050f 
    val bassColor = MaterialTheme.colorScheme.primary
    val midsColor = MaterialTheme.colorScheme.secondary
    val highsColor = MaterialTheme.colorScheme.tertiary
    val gridLineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
    val axisLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
    val overlayLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
    val dominantMarkerColor = MaterialTheme.colorScheme.inversePrimary
    val axisStripColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f)

    Canvas(modifier = modifier) {
        if (frequencies.isEmpty()) return@Canvas

        val width = size.width
        val height = size.height
        val labelArea = 32.dp.toPx()
        val drawHeight = height - labelArea
        val overlayLabelY = 16.dp.toPx()
        val logMin = log10(minFreq)
        val logMax = log10(maxFreq)

        // Helper to map frequency to X coordinate (log scale)
        fun getXForFreq(freq: Float): Float {
            val logFreq = log10(freq.coerceIn(minFreq, maxFreq))
            return ((logFreq - logMin) / (logMax - logMin)) * width
        }

        // Draw grid lines and labels
        val paint = android.graphics.Paint().apply {
            color = axisLabelColor.toArgb()
            textSize = 11.sp.toPx()
            textAlign = android.graphics.Paint.Align.CENTER
            isFakeBoldText = true
        }

        val overlayPaint = android.graphics.Paint().apply {
            color = overlayLabelColor.toArgb()
            textSize = 10.sp.toPx()
            textAlign = android.graphics.Paint.Align.CENTER
            isFakeBoldText = true
        }

        drawRect(
            color = axisStripColor,
            topLeft = Offset(0f, drawHeight),
            size = androidx.compose.ui.geometry.Size(width, labelArea)
        )
        drawLine(
            color = gridLineColor.copy(alpha = 0.38f),
            start = Offset(0f, drawHeight),
            end = Offset(width, drawHeight),
            strokeWidth = 1.dp.toPx()
        )

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
                color = gridLineColor,
                start = Offset(x, 0f),
                end = Offset(x, drawHeight),
                strokeWidth = 1.dp.toPx()
            )
            drawContext.canvas.nativeCanvas.drawText(label, x, drawHeight + labelArea - 4f, paint)
        }

        val numBars = 64
        val barWidth = width / numBars
        val snapshotSource = snapshotFrequencies?.takeIf { showSnapshotOverlay && it.isNotEmpty() }
        val snapshotMax = snapshotSource?.maxOrNull() ?: 0f
        val globalMax = maxOf(frequencies.maxOrNull() ?: 1f, snapshotMax).coerceAtLeast(1f)
        val snapshotPoints = mutableListOf<Offset>()

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

            if (snapshotSource != null) {
                val snapshotBinSize = maxFreq / snapshotSource.size
                val snapshotStartIndex = (freqStart / snapshotBinSize).toInt().coerceIn(0, snapshotSource.size - 1)
                val snapshotEndIndex = (freqEnd / snapshotBinSize).toInt().coerceIn(0, snapshotSource.size - 1)
                var snapshotMagnitude = 0f
                for (j in snapshotStartIndex..snapshotEndIndex) {
                    if (snapshotSource[j] > snapshotMagnitude) snapshotMagnitude = snapshotSource[j]
                }
                val snapshotBarHeight = (snapshotMagnitude / globalMax) * drawHeight
                snapshotPoints += Offset(
                    x = xStart + (barWidth / 2f),
                    y = drawHeight - snapshotBarHeight
                )
            }
        }

        if (dominantFrequency > 0f) {
            val dominantX = getXForFreq(dominantFrequency)
            drawLine(
                color = dominantMarkerColor.copy(alpha = 0.65f),
                start = Offset(dominantX, 0f),
                end = Offset(dominantX, drawHeight),
                strokeWidth = 2.dp.toPx()
            )
            drawCircle(
                color = dominantMarkerColor,
                radius = 4.dp.toPx(),
                center = Offset(dominantX, 14.dp.toPx())
            )
        }

        if (showSnapshotOverlay && snapshotPoints.size > 1) {
            for (index in 0 until snapshotPoints.lastIndex) {
                drawLine(
                    color = Color(0xFF00ACC1).copy(alpha = 0.95f),
                    start = snapshotPoints[index],
                    end = snapshotPoints[index + 1],
                    strokeWidth = 2.dp.toPx()
                )
            }
        }

        if (selectedTargetCurve.isEnabled) {
            val curveMinDb = -12f
            val curveMaxDb = 6f
            val curvePoints = selectedTargetCurve.points.map { point ->
                val normalizedLevel = ((point.relativeDb - curveMinDb) / (curveMaxDb - curveMinDb)).coerceIn(0f, 1f)
                Offset(
                    x = getXForFreq(point.frequencyHz),
                    y = drawHeight - normalizedLevel * drawHeight
                )
            }
            for (index in 0 until curvePoints.lastIndex) {
                drawLine(
                    color = selectedTargetCurve.color.copy(alpha = 0.92f),
                    start = curvePoints[index],
                    end = curvePoints[index + 1],
                    strokeWidth = 3.dp.toPx()
                )
            }
            curvePoints.forEachIndexed { index, point ->
                drawCircle(
                    color = selectedTargetCurve.color,
                    radius = if (index == curvePoints.lastIndex) 4.dp.toPx() else 3.dp.toPx(),
                    center = point
                )
            }
        }

        if (showFeedbackMarkers) {
            val markerColors = listOf(Color(0xFFD32F2F), Color(0xFFF57C00), Color(0xFFFBC02D))
            feedbackPeaks.take(3).forEachIndexed { index, peak ->
                val x = getXForFreq(peak.frequencyHz)
                val markerColor = markerColors.getOrElse(index) { Color.Red }
                drawLine(
                    color = markerColor.copy(alpha = 0.9f),
                    start = Offset(x, 0f),
                    end = Offset(x, drawHeight),
                    strokeWidth = 2.dp.toPx()
                )
                drawCircle(
                    color = markerColor,
                    radius = 4.dp.toPx(),
                    center = Offset(x, 10.dp.toPx())
                )
            }
        }
    }
}

@Composable
fun FeedbackHuntSection(feedbackPeaks: List<FeedbackPeak>, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.22f),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Feedback watch",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (feedbackPeaks.isEmpty()) {
                Text(
                    text = "No stable peaks yet. When a source starts to ring, the strongest repeated hotspots will show up here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    feedbackPeaks.forEach { peak ->
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = formatFrequencyLabel(peak.frequencyHz),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Cut ${formatFrequencyLabel(peak.suggestedCutHz)} ${feedbackPeakLabel(peak)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

fun feedbackPeakLabel(peak: FeedbackPeak): String = when {
    peak.stability >= 0.8f -> "Stable"
    peak.stability >= 0.45f -> "Building"
    else -> "Watch"
}

fun formatSignedFrequencyDelta(deltaHz: Float): String = when {
    kotlin.math.abs(deltaHz) >= 1000f -> String.format(Locale.getDefault(), "%+.1f kHz", deltaHz / 1000f)
    else -> String.format(Locale.getDefault(), "%+.0f Hz", deltaHz)
}

fun formatSignedDbDelta(deltaDb: Float): String =
    String.format(Locale.getDefault(), "%+.1f dB", deltaDb)

@Composable
fun SettingsDialog(
    currentOffset: Float,
    currentThreshold: Float,
    importExportMessage: String?,
    onOffsetChange: (Float) -> Unit,
    onThresholdChange: (Float) -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onDismiss: () -> Unit,
    onOpenCalibration: (() -> Unit)? = null
) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.mic_calibration)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
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

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Backup and restore",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Save venue curves plus app preferences to a file, or import them on another device. Import replaces the current saved curves and app preferences.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onExportBackup) {
                        Text("Export backup")
                    }
                    OutlinedButton(onClick = onImportBackup) {
                        Text("Import backup")
                    }
                }
                if (!importExportMessage.isNullOrBlank()) {
                    Text(
                        text = importExportMessage,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        dismissButton = {
            if (onOpenCalibration != null) {
                TextButton(onClick = {
                    persistCalibrationPreferences(ctx, currentOffset, currentThreshold)
                    onDismiss()
                    onOpenCalibration()
                }) {
                    Text(stringResource(id = R.string.open_calibration))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                persistCalibrationPreferences(ctx, currentOffset, currentThreshold)
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
    val tabs = listOf("Guide", "EQ Starting Points", "Features")

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
                    when (selectedTab) {
                        0 -> {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                            ) {
                                Text(
                                    text = "Quick live-sound reminders for ringing out monitors, shaping sources, and using the analyzer as a reference instead of a hard rule.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                            GuideTipCard(
                                title = "Ring Out Feedback Faster",
                                action = "Raise the source until it starts to ring, watch the tallest peak, then make a small targeted cut.",
                                whyItHelps = "Feedback usually shows up as a narrow, stubborn hotspot. Small cuts keep more tone than broad subtraction."
                            )
                            GuideTipCard(
                                title = "Use Overlays on Purpose",
                                action = "Select the source you are shaping before you start chasing the graph.",
                                whyItHelps = "The overlay narrows your attention to the zones that usually matter for that source instead of the whole spectrum."
                            )
                            GuideTipCard(
                                title = "Clear Mud Before You Boost",
                                action = "If a channel feels cloudy, work 200-400 Hz before reaching for top-end boosts.",
                                whyItHelps = "Small low-mid cuts often create more clarity and more gain before feedback than adding highs."
                            )
                            GuideTipCard(
                                title = "Calibrate Expectations",
                                action = "Treat the phone as a reference tool and calibrate it against a known meter when accuracy matters.",
                                whyItHelps = "That keeps the app useful for trends and recall without mistaking it for a lab-grade SPL meter."
                            )
                            GuideTipCard(
                                title = "Watch Listener Fatigue",
                                action = "If the room feels tiring, check both overall level and energy in the 2-5 kHz region.",
                                whyItHelps = "Listener fatigue usually comes from loudness plus aggressive upper mids, not only one or the other."
                            )
                        }
                        1 -> {
                            eqReferenceEntries.forEach { entry ->
                                EqReferenceCard(entry = entry)
                            }
                        }
                        2 -> {
                            // Features and help for new functionality
                            ElevatedCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Signal Generator",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Play test signals for tuning and calibration. Modes: SINE, WHITE (white noise), PINK (pink noise), and PULSE.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                    Text(
                                        text = "Use the generator from Tools → Signal generator. For safety stop playback before moving microphones or making system changes.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 6.dp)
                                    )
                                }
                            }

                            ElevatedCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Weighted SPL (dB(A), dB(C), dB(Z))",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "The main meter shows calibrated dB and the app computes A-, C-, and Z-weighted values from the spectrum. Use calibration to align the phone to a reference meter when accuracy is required.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }
                            }

                            ElevatedCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Waterfall (Spectrogram)",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "The waterfall shows recent spectrum frames over time. Enable it from Tools → Waterfall. Use the sliders to adjust the percentile autoscaling and gamma to make mid-level content more visible.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                    Text(
                                        text = "If the waterfall looks too dark or washed out, increase the Max percentile or lower Gamma. For performance the waterfall uses a bitmap-backed renderer — reduce history or bin count if you see slowdowns.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 6.dp)
                                    )
                                }
                            }

                            ElevatedCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Planned features",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Phase correlation meter and Delay calculator are planned; they require stereo input or special routing and will appear in a future update.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }
                            }
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
fun GuideTipCard(title: String, action: String, whyItHelps: String) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            DetailRow(
                label = "Do this",
                value = action,
                modifier = Modifier.padding(top = 10.dp)
            )
            DetailRow(
                label = "Why",
                value = whyItHelps,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}

@Composable
fun DetailRow(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label.uppercase(Locale.getDefault()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
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
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.padding(top = 6.dp)
            ) {
                Text(
                    text = entry.role,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
            DetailRow(label = "HPF", value = entry.highPass, modifier = Modifier.padding(top = 12.dp))
            DetailRow(label = "Body", value = entry.body, modifier = Modifier.padding(top = 10.dp))
            DetailRow(label = "Clarity", value = entry.presence, modifier = Modifier.padding(top = 10.dp))
            DetailRow(label = "Watch", value = entry.watchOut, modifier = Modifier.padding(top = 10.dp))
            DetailRow(label = "Start move", value = entry.startingMove, modifier = Modifier.padding(top = 10.dp))
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(id = R.string.cancel))
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
