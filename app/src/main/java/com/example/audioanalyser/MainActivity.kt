package com.example.audioanalyser

import java.util.Locale
import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
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
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    if (hasPermission) {
        AudioAnalyserContent(analyzer)
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Microphone access is required for analysis")
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { launcher.launch(Manifest.permission.RECORD_AUDIO) }) {
                    Text("Allow Microphone Access")
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
    
    var showInfoDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    LaunchedEffect(Unit) {
        analyzer.startAnalyzing()
    }

    DisposableEffect(Unit) {
        onDispose {
            analyzer.stop()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Audio Analyser", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = { analyzer.resetStats() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset Stats")
                    }
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(Icons.Default.Info, contentDescription = "Mixing Info")
                    }
                }
            )
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        if (isLandscape) {
            LandscapeLayout(
                dbLevel = dbLevel,
                minDb = minDb,
                maxDb = maxDb,
                avgDb = avgDb,
                dbHistory = dbHistory,
                frequencies = frequencies,
                modifier = Modifier.padding(innerPadding)
            )
        } else {
            PortraitLayout(
                dbLevel = dbLevel,
                minDb = minDb,
                maxDb = maxDb,
                avgDb = avgDb,
                dbHistory = dbHistory,
                frequencies = frequencies,
                modifier = Modifier.padding(innerPadding)
            )
        }
    }

    if (showSettingsDialog) {
        SettingsDialog(
            currentOffset = dbOffset,
            currentThreshold = noiseThreshold,
            onOffsetChange = { analyzer.setDbOffset(it) },
            onThresholdChange = { analyzer.setNoiseThreshold(it) },
            onDismiss = { showSettingsDialog = false }
        )
    }

    if (showInfoDialog) {
        MixingInfoDialog(onDismiss = { 
            showInfoDialog = false 
        })
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
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
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
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
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

    ElevatedCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Sound Level",
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
                    text = if (dbLevel > 85) "TOO LOUD" else "SAFE LEVEL",
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
                StatItem("Min", if (minDb == Float.MAX_VALUE) 0f else minDb)
                StatItem("Avg", avgDb)
                StatItem("Max", maxDb)
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
        val maxVal = 100f // Scale sparkline to 100dB max
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
fun VisualizerCard(frequencies: FloatArray, modifier: Modifier = Modifier) {
    ElevatedCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxSize()
        ) {
            Text(
                text = "Frequency Spectrum",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            FrequencyVisualizer(
                frequencies = frequencies,
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
                Text("Bass", style = MaterialTheme.typography.labelSmall, color = Color(0xFF2196F3))
                Text("Mids", style = MaterialTheme.typography.labelSmall, color = Color(0xFF4CAF50))
                Text("Highs", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFFC107))
            }
        }
    }
}

@Composable
fun FrequencyVisualizer(frequencies: FloatArray, modifier: Modifier = Modifier) {
    val labels = listOf(
        60f to "60",
        250f to "250",
        1000f to "1k",
        4000f to "4k",
        16000f to "16k"
    )
    
    val minFreq = 20f
    val maxFreq = 22050f 

    Canvas(modifier = modifier) {
        if (frequencies.isEmpty()) return@Canvas

        val width = size.width
        val height = size.height
        
        // Helper to map frequency to X coordinate
        fun getXForFreq(freq: Float): Float {
            val logMin = log10(minFreq)
            val logMax = log10(maxFreq)
            val logFreq = log10(freq.coerceIn(minFreq, maxFreq))
            return ((logFreq - logMin) / (logMax - logMin)) * width
        }

        // Draw grid lines
        labels.forEach { (freq, label) ->
            val x = getXForFreq(freq)
            drawLine(
                color = Color.Gray.copy(alpha = 0.2f),
                start = Offset(x, 0f),
                end = Offset(x, height),
                strokeWidth = 1.dp.toPx()
            )
            drawContext.canvas.nativeCanvas.drawText(
                label,
                x,
                height + 30f,
                android.graphics.Paint().apply {
                    color = android.graphics.Color.GRAY
                    textSize = 10.sp.toPx()
                    textAlign = android.graphics.Paint.Align.CENTER
                }
            )
        }

        val numBars = 64
        val barWidth = width / numBars
        
        for (i in 0 until numBars) {
            // Map bar index to frequency range
            val xStart = i * barWidth
            val xEnd = (i + 1) * barWidth
            
            // Reverse mapping from X to Frequency
            fun getFreqForX(x: Float): Float {
                val logMin = log10(minFreq)
                val logMax = log10(maxFreq)
                val normalizedX = x / width
                val logFreq = normalizedX * (logMax - logMin) + logMin
                return 10.0.pow(logFreq.toDouble()).toFloat()
            }

            val freqStart = getFreqForX(xStart)
            val freqEnd = getFreqForX(xEnd)
            
            // Map frequency to FFT index
            val binSize = maxFreq / frequencies.size
            val startIndex = (freqStart / binSize).toInt().coerceIn(0, frequencies.size - 1)
            val endIndex = (freqEnd / binSize).toInt().coerceIn(0, frequencies.size - 1)
            
            var maxMagnitude = 0f
            for (j in startIndex..endIndex) {
                if (frequencies[j] > maxMagnitude) maxMagnitude = frequencies[j]
            }

            val dbScale = (20 * log10(maxMagnitude.toDouble() + 1.0) / 100.0).toFloat()
            val barHeight = (dbScale * height).coerceIn(2.dp.toPx(), height)
            
            val color = when {
                freqStart < 250 -> Color(0xFF2196F3) // Bass
                freqStart < 4000 -> Color(0xFF4CAF50) // Mids
                else -> Color(0xFFFFC107) // Highs
            }

            drawRect(
                color = color,
                topLeft = Offset(i * barWidth, height - barHeight),
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
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mic Calibration") },
        text = {
            Column {
                Text(
                    text = String.format(Locale.getDefault(), "dB Offset: %.0f", currentOffset),
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = currentOffset,
                    onValueChange = onOffsetChange,
                    valueRange = 0f..100f,
                    steps = 100
                )
                Text(
                    text = "Adjust if the app reads too high or low compared to a real meter.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = String.format(Locale.getDefault(), "Noise Gate: %.0f dB", currentThreshold),
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = currentThreshold,
                    onValueChange = onThresholdChange,
                    valueRange = 0f..60f,
                    steps = 60
                )
                Text(
                    text = "Increse this to cut out background static/hiss in quiet rooms.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
fun MixingInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mixing & Feedback Guide") },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp)
            ) {
                InfoSection(
                    title = "Eliminating Feedback",
                    body = "Feedback usually appears as a single, tall, sharp spike in the 'Highs' or 'Upper Mids'. If you hear ringing, check the visualizer. Find the spike's frequency (e.g., 4k) and slightly lower that band on your soundboard."
                )
                InfoSection(
                    title = "Fixing 'Muddy' Sound",
                    body = "If vocals or instruments sound 'boomy' or unclear, check the 'Bass' and low-mid section (60-250Hz). If those bars are consistently high, apply a High-Pass Filter (HPF) to those channels on the mixer."
                )
                InfoSection(
                    title = "Vocal Presence",
                    body = "Vocals typically sit in the 1kHz to 4kHz range. If a singer is getting lost, look for a dip in the 'Mids' section. Boosting this range slightly can help them cut through the mix."
                )
                InfoSection(
                    title = "Safe Volume Levels",
                    body = "Church services should ideally stay between 75dB and 85dB. If the meter turns Red (>90dB), you are likely causing listener fatigue and should lower the master volume."
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Got it")
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
