package com.example.scandiet

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController.COORDINATE_SYSTEM_VIEW_REFERENCED
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeRect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.scandiet.ui.theme.ScanDietTheme
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                // Permission is granted. Continue the action or workflow in your app.
            } else {
                // Explain to the user that the feature is unavailable because the
                // feature requires a permission that the user has denied. At the
                // same time, respect the user's decision. Don't link to system
                // settings in an effort to convince the user to change their
                // decision.
            }
        }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
        enableEdgeToEdge()
        setContent {
            ScanDietTheme {
                ScanDietApp()
            }
        }
    }
}

@PreviewScreenSizes
@Composable
fun ScanDietApp() {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.DIETARY) }
    var scannedBarcode by rememberSaveable { mutableStateOf<String?>(null) }
    var productInfo by remember { mutableStateOf<ProductInfo?>(null) }

    val context = LocalContext.current
    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.filter { it.showInNavBar }.forEach {
                item(
                    icon = {
                        Icon(
                            it.icon,
                            contentDescription = stringResource(it.labelRes)
                        )
                    },
                    label = { Text(stringResource(it.labelRes)) },
                    selected = it == currentDestination,
                    onClick = {
                        currentDestination = it
                    }
                )
            }
        }
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            when (currentDestination) {
                AppDestinations.DIETARY -> DietaryNeedsScreen(modifier = Modifier.padding(innerPadding))
                AppDestinations.SCANNER -> ScannerScreen(modifier = Modifier.padding(innerPadding)) {
                    if (it != scannedBarcode) {
                        scannedBarcode = it
                        productInfo = null
                    }
                    currentDestination = AppDestinations.INFO
                }
                AppDestinations.HISTORY -> HistoryScreen(
                    modifier = Modifier.padding(innerPadding),
                    onItemClick = { item ->
                        scannedBarcode = item.barcode
                        productInfo = item.productInfo
                        currentDestination = AppDestinations.INFO
                    }
                )
                AppDestinations.INFO -> {
                    if (scannedBarcode != null) {
                        InfoScreen(
                            barcode = scannedBarcode!!,
                            productInfo = productInfo,
                            onProductInfoLoaded = { loadedInfo ->
                                productInfo = loadedInfo
                                val prefs = context.getSharedPreferences("history_prefs", Context.MODE_PRIVATE)
                                val historyJson = prefs.getString("history", "[]") ?: "[]"
                                val history = try {
                                    jsonConfig.decodeFromString<List<HistoryItem>>(historyJson).toMutableList()
                                } catch (e: Exception) {
                                    mutableListOf()
                                }
                                history.removeAll { it.barcode == scannedBarcode!! }
                                history.add(0, HistoryItem(scannedBarcode!!, loadedInfo))
                                prefs.edit().putString("history", jsonConfig.encodeToString(history.take(50))).apply()
                            },
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.scan_prompt))
                        }
                    }
                }
            }
        }
    }
}

enum class AppDestinations(
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    val showInNavBar: Boolean = true
) {
    DIETARY(R.string.nav_dietary, Icons.Filled.Checklist),
    SCANNER(R.string.nav_scanner, Icons.Filled.QrCodeScanner),
    HISTORY(R.string.nav_history, Icons.Filled.History),
    INFO(R.string.nav_info, Icons.Filled.Info, showInNavBar = false),
}

@Serializable
data class HistoryItem(
    val barcode: String,
    val productInfo: ProductInfo,
    val timestamp: Long = System.currentTimeMillis()
)

enum class HistoryFilter {
    ALL, SAFE, UNSAFE
}

data class DietaryNeed(
    val key: String,
    @StringRes val nameRes: Int,
    val isChecked: Boolean,
    val labels: List<String>,
    val isAllergen: Boolean = true
)

val allNeeds = listOf(
    DietaryNeed("gluten", R.string.need_gluten, false, listOf("gluten")),
    DietaryNeed("lactose", R.string.need_lactose, false, listOf("lactose")),
    DietaryNeed("tree_nut", R.string.need_tree_nut, false, listOf("tree_nut")),
    DietaryNeed("peanut", R.string.need_peanut, false, listOf("peanut")),
    DietaryNeed("soy", R.string.need_soy, false, listOf("soy")),
    DietaryNeed("shellfish", R.string.need_shellfish, false, listOf("shellfish")),
    DietaryNeed("egg", R.string.need_egg, false, listOf("egg")),
    DietaryNeed("fish", R.string.need_fish, false, listOf("fish")),
    DietaryNeed("sesame", R.string.need_sesame, false, listOf("sesame")),
    DietaryNeed("vegetarian", R.string.need_vegetarian, false, listOf("meat", "fish")),
    DietaryNeed("vegan", R.string.need_vegan, false, listOf("meat", "fish", "egg", "lactose")),
    DietaryNeed("sugar", R.string.need_sugar, false, listOf("sugar"), isAllergen = false),
    DietaryNeed("sodium", R.string.need_sodium, false, listOf("sodium"), isAllergen = false)
)

@Composable
fun DietaryNeedsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("dietary_prefs", Context.MODE_PRIVATE)
    }

    var dietaryNeeds by remember {
        val savedNeeds = prefs.getStringSet("dietary_needs", emptySet()) ?: emptySet()
        mutableStateOf(allNeeds.map { it.copy(isChecked = savedNeeds.contains(it.key)) })
    }

    fun updateNeed(need: DietaryNeed, isChecked: Boolean) {
        val updatedNeeds = dietaryNeeds.map {
            if (it.key == need.key) {
                it.copy(isChecked = isChecked)
            } else {
                it
            }
        }
        dietaryNeeds = updatedNeeds
        val newSavedNeeds = updatedNeeds.filter { it.isChecked }.map { it.key }.toSet()
        prefs.edit().putStringSet("dietary_needs", newSavedNeeds).apply()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val allergens = dietaryNeeds.filter { it.isAllergen }
        val additives = dietaryNeeds.filter { !it.isAllergen }

        if (additives.isNotEmpty()) {
            item {
                Text(
                    text = "Additives",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            items(additives) { need ->
                DietaryNeedItem(need = need, onCheckedChange = { updateNeed(need, it) })
            }
        }

        if (allergens.isNotEmpty()) {
            item {
                if (additives.isNotEmpty()) Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Allergens",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            items(allergens) { need ->
                DietaryNeedItem(need = need, onCheckedChange = { updateNeed(need, it) })
            }
        }
    }
}

@Composable
fun DietaryNeedItem(need: DietaryNeed, onCheckedChange: (Boolean) -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onCheckedChange(!need.isChecked) },
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth()
        ) {
            Checkbox(
                checked = need.isChecked,
                onCheckedChange = onCheckedChange
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = stringResource(need.nameRes),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier,
    onItemClick: (HistoryItem) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("history_prefs", Context.MODE_PRIVATE) }
    val dietaryPrefs = remember { context.getSharedPreferences("dietary_prefs", Context.MODE_PRIVATE) }

    val history = remember {
        val json = prefs.getString("history", "[]") ?: "[]"
        try {
            jsonConfig.decodeFromString<List<HistoryItem>>(json)
        } catch (e: Exception) {
            emptyList()
        }
    }

    val selectedNeeds = remember {
        val savedNeeds = dietaryPrefs.getStringSet("dietary_needs", emptySet()) ?: emptySet()
        allNeeds.filter { savedNeeds.contains(it.key) }
    }
    var selectedFilter by rememberSaveable { mutableStateOf(HistoryFilter.ALL) }

    val filteredHistory = remember(history, selectedFilter, selectedNeeds) {
        when (selectedFilter) {
            HistoryFilter.ALL -> history
            HistoryFilter.SAFE -> history.filter { item ->
                selectedNeeds.none { need ->
                    item.productInfo.labels.keys.any { label ->
                        need.labels.any { it.equals(label, ignoreCase = true) }
                    }
                }
            }
            HistoryFilter.UNSAFE -> history.filter { item ->
                selectedNeeds.any { need ->
                    item.productInfo.labels.keys.any { label ->
                        need.labels.any { it.equals(label, ignoreCase = true) }
                    }
                }
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(HistoryFilter.entries.toTypedArray()) { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { selectedFilter = filter },
                    label = { Text(filter.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    leadingIcon = if (selectedFilter == filter) {
                        {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                            )
                        }
                    } else null
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(filteredHistory) { item ->
                val foundNeeds = selectedNeeds.filter { need ->
                    item.productInfo.labels.keys.any { label ->
                        need.labels.any { it.equals(label, ignoreCase = true) }
                    }
                }
                val foundAllergens = foundNeeds.filter { it.isAllergen }
                val foundAdditives = foundNeeds.filter { !it.isAllergen }
                
                val hasAllergens = foundAllergens.isNotEmpty()
                val hasAdditives = foundAdditives.isNotEmpty()
                
                val isDark = isSystemInDarkTheme()
                val warningColor = if (isDark) Color(0xFFFFDF91) else Color(0xFF7A5900)
                val borderColor = when {
                    hasAllergens -> if (isDark) Color(0xFFE57373) else Color(0xFFD32F2F)
                    hasAdditives -> warningColor
                    else -> if (isDark) Color(0xFF4CAF50) else Color(0xFF2E7D32)
                }

                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onItemClick(item) },
                    border = BorderStroke(2.dp, borderColor),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = item.productInfo.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        if (hasAllergens || hasAdditives) {
                            val allFoundLabels = item.productInfo.labels.keys.filter { label ->
                                selectedNeeds.any { need -> need.labels.any { it.equals(label, ignoreCase = true) } }
                            }
                            Text(
                                text = "Contains: ${allFoundLabels.joinToString(", ")}",
                                color = if (hasAllergens) borderColor else warningColor,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        } else {
                            Text(
                                text = "No selected allergens found",
                                color = borderColor,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = item.barcode,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

class BarcodeUiState(
    val value: String,
    initialRect: Rect,
    private val scope: CoroutineScope
) {
    var lastSeen by mutableLongStateOf(System.currentTimeMillis())
    var isFadingOut by mutableStateOf(false)

    val offset = Animatable(Offset(initialRect.left, initialRect.top), Offset.VectorConverter)
    val size = Animatable(Size(initialRect.width, initialRect.height), Size.VectorConverter)
    val alpha = Animatable(0f)

    fun update(newRect: Rect) {
        lastSeen = System.currentTimeMillis()
        if (!isFadingOut) {
            scope.launch {
                offset.animateTo(
                    Offset(newRect.left, newRect.top),
                    spring(stiffness = Spring.StiffnessMedium)
                )
            }
            scope.launch {
                size.animateTo(
                    Size(newRect.width, newRect.height),
                    spring(stiffness = Spring.StiffnessMedium)
                )
            }
        }
    }

    fun fadeIn() {
        scope.launch {
            alpha.animateTo(1f)
        }
    }

    fun fadeOut(onFinished: () -> Unit) {
        isFadingOut = true
        scope.launch {
            alpha.animateTo(0f)
            onFinished()
        }
    }
}

@Composable
fun ScannerScreen(modifier: Modifier = Modifier, onBarcodeScanned: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraController = remember { LifecycleCameraController(context) }
    val scope = rememberCoroutineScope()

    var detectedBarcodes by remember { mutableStateOf<List<Barcode>>(emptyList()) }
    val activeBarcodes = remember { mutableStateMapOf<String, BarcodeUiState>() }
    var selectedBarcodeValue by remember { mutableStateOf<String?>(null) }

    val options = remember {
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
            .build()
    }
    val barcodeScanner = remember { BarcodeScanning.getClient(options) }

    cameraController.setImageAnalysisAnalyzer(
        ContextCompat.getMainExecutor(context),
        MlKitAnalyzer(
            listOf(barcodeScanner),
            COORDINATE_SYSTEM_VIEW_REFERENCED,
            ContextCompat.getMainExecutor(context)
        ) { result: MlKitAnalyzer.Result ->
            detectedBarcodes = result.getValue(barcodeScanner) ?: emptyList()
        }
    )

    cameraController.bindToLifecycle(lifecycleOwner)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val centerX = constraints.maxWidth / 2f
        val centerY = constraints.maxHeight / 2f

        // Selection stability logic
        val candidate = remember(detectedBarcodes) {
            detectedBarcodes.minByOrNull { barcode ->
                barcode.boundingBox?.let { box ->
                    val boxCenterX = box.centerX()
                    val boxCenterY = box.centerY()
                    sqrt(((boxCenterX - centerX) * (boxCenterX - centerX) + (boxCenterY - centerY) * (boxCenterY - centerY)).toDouble())
                } ?: Double.MAX_VALUE
            }
        }

        LaunchedEffect(candidate?.rawValue) {
            if (candidate?.rawValue != selectedBarcodeValue) {
                delay(250)
                selectedBarcodeValue = candidate?.rawValue
            }
        }

        // Bounding box animation and throttling logic
        LaunchedEffect(Unit) {
            while (true) {
                val now = System.currentTimeMillis()
                val currentDetected = detectedBarcodes

                // Update existing or add new
                currentDetected.forEach { barcode ->
                    val value = barcode.rawValue ?: return@forEach
                    val rect = barcode.boundingBox?.toComposeRect() ?: return@forEach

                    val state = activeBarcodes[value]
                    if (state == null) {
                        val newState = BarcodeUiState(value, rect, scope)
                        activeBarcodes[value] = newState
                        newState.fadeIn()
                    } else {
                        state.update(rect)
                    }
                }

                // Clean up old barcodes
                val toRemove = activeBarcodes.entries.filter {
                    now - it.value.lastSeen > 300 && !it.value.isFadingOut
                }
                toRemove.forEach { (value, state) ->
                    state.fadeOut {
                        activeBarcodes.remove(value)
                    }
                }

                delay(40) // Update target positions at ~25fps for better responsiveness
            }
        }

        AndroidView(
            factory = { context ->
                PreviewView(context).apply {
                    this.controller = cameraController
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Subtle scanner guides (rounded corners)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val boxSize = 250.dp.toPx()
            val left = (size.width - boxSize) / 2
            val top = (size.height - boxSize) / 2
            val rect = Rect(left, top, left + boxSize, top + boxSize)
            
            val color = Color.White.copy(alpha = 0.3f)
            val strokeWidth = 2.dp.toPx()
            val radius = 24.dp.toPx()
            val arcSize = Size(radius * 2, radius * 2)

            // Top-left
            drawArc(
                color = color,
                startAngle = 180f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(rect.left, rect.top),
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            
            // Top-right
            drawArc(
                color = color,
                startAngle = 270f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(rect.right - radius * 2, rect.top),
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            
            // Bottom-right
            drawArc(
                color = color,
                startAngle = 0f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(rect.right - radius * 2, rect.bottom - radius * 2),
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            
            // Bottom-left
            drawArc(
                color = color,
                startAngle = 90f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(rect.left, rect.bottom - radius * 2),
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            activeBarcodes.values.forEach { state ->
                val isPrioritized = state.value == selectedBarcodeValue
                drawRoundRect(
                    color = if (isPrioritized) Color.Yellow.copy(alpha = state.alpha.value)
                    else Color.White.copy(alpha = 0.5f * state.alpha.value),
                    topLeft = state.offset.value,
                    size = state.size.value,
                    cornerRadius = CornerRadius(4.dp.toPx()),
                    style = Stroke(width = if (isPrioritized) 3.dp.toPx() else 1.dp.toPx())
                )
            }
        }

        // Top barcode "pillow"
        selectedBarcodeValue?.let { barcodeValue ->
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 48.dp)
                    .wrapContentSize(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
                tonalElevation = 4.dp
            ) {
                Text(
                    text = barcodeValue,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        // Bottom "Shutter" button with background
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp)
                .size(92.dp)
                .background(Color.Black.copy(alpha = 0.25f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .background(
                        color = if (selectedBarcodeValue != null)
                            MaterialTheme.colorScheme.primary
                        else
                            Color.White.copy(alpha = 0.3f),
                        shape = CircleShape
                    )
                    .clickable(
                        enabled = selectedBarcodeValue != null,
                        onClick = { selectedBarcodeValue?.let { onBarcodeScanned(it) } }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Scan",
                    modifier = Modifier.size(36.dp),
                    tint = if (selectedBarcodeValue != null)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        Color.White.copy(alpha = 0.6f)
                )
            }
        }
    }
}

private val jsonConfig = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

@Serializable
data class ProductInfo(
    val name: String,
    val ingredients: String,
    val labels: Map<String, List<List<Int>>> = emptyMap()
)

@Composable
fun InfoScreen(
    barcode: String,
    productInfo: ProductInfo?,
    onProductInfoLoaded: (ProductInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    val selectedNeeds = remember(barcode, productInfo) {
        val prefs = context.getSharedPreferences("dietary_prefs", Context.MODE_PRIVATE)
        val savedNeeds = prefs.getStringSet("dietary_needs", emptySet()) ?: emptySet()
        allNeeds.filter { savedNeeds.contains(it.key) }
    }

    LaunchedEffect(barcode) {
        if (productInfo != null) return@LaunchedEffect
        val client = HttpClient(CIO)
        try {
            val responseText = client.get("${BuildConfig.API_BASE_URL}/barcode/$barcode").bodyAsText()
            Log.d("InfoScreen", "Response: $responseText")
            val decoded = jsonConfig.decodeFromString<ProductInfo>(responseText)
            onProductInfoLoaded(decoded)
            client.close()
        } catch (e: Exception) {
            Log.e("InfoScreen", "Error fetching/parsing", e)
            error = context.getString(R.string.error_prefix, e.message ?: context.getString(R.string.unknown_error))
            client.close()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        when {
            productInfo != null -> {
                Text(
                    text = productInfo.name,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Ingredients",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        val annotatedString = buildAnnotatedString {
                            append(productInfo.ingredients)
                            productInfo.labels.forEach { (label, spans) ->
                                val matchingNeed = selectedNeeds.find { need -> 
                                    need.labels.any { it.equals(label, ignoreCase = true) }
                                }
                                if (matchingNeed != null) {
                                    val isDark = isSystemInDarkTheme()
                                    val (bgColor, textColor) = if (matchingNeed.isAllergen) {
                                        MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
                                    } else {
                                        if (isDark) {
                                            Color(0xFF574500) to Color(0xFFFFDF91) // M3 Dark Warning
                                        } else {
                                            Color(0xFFFFDF91) to Color(0xFF241A00) // M3 Light Warning
                                        }
                                    }
                                    spans.forEach { span ->
                                        if (span.size >= 2) {
                                            addStyle(
                                                style = SpanStyle(
                                                    color = textColor,
                                                    background = bgColor
                                                ),
                                                start = span[0],
                                                end = span[1]
                                            )
                                            addStringAnnotation(
                                                tag = "HIGHLIGHT",
                                                annotation = label,
                                                start = span[0],
                                                end = span[1]
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Text(
                            text = annotatedString,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                lineHeight = 32.sp
                            )
                        )
                    }
                }

                if (selectedNeeds.isNotEmpty()) {
                    val foundNeeds = selectedNeeds.filter { need ->
                        productInfo.labels.keys.any { label -> 
                            need.labels.any { it.equals(label, ignoreCase = true) }
                        }
                    }
                    val foundAllergens = foundNeeds.filter { it.isAllergen }
                    val foundAdditives = foundNeeds.filter { !it.isAllergen }
                    
                    val isDark = isSystemInDarkTheme()
                    val warningColor = if (isDark) Color(0xFFFFDF91) else Color(0xFF7A5900)
                    val borderColor = when {
                        foundAllergens.isNotEmpty() -> if (isDark) Color(0xFFE57373) else Color(0xFFD32F2F)
                        foundAdditives.isNotEmpty() -> warningColor
                        else -> if (isDark) Color(0xFF4CAF50) else Color(0xFF2E7D32)
                    }

                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(2.dp, borderColor),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            val activeFiltersText = selectedNeeds.joinToString(", ") { context.getString(it.nameRes) }
                            Text(
                                text = stringResource(R.string.active_filters, activeFiltersText),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            if (foundAllergens.isNotEmpty() || foundAdditives.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                val alertText = buildString {
                                    if (foundAllergens.isNotEmpty()) append("Alert: Matching allergens found and highlighted. ")
                                    if (foundAdditives.isNotEmpty()) append("Note: Matching additives found and highlighted.")
                                }
                                Text(
                                    text = alertText,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (foundAllergens.isNotEmpty()) borderColor else warningColor
                                )
                            } else {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.no_matches_found),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = borderColor
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = stringResource(R.string.no_needs_selected),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                Text(
                    text = "Barcode: $barcode",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            error != null -> {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = error!!,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            else -> {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(text = stringResource(R.string.loading), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}


@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    ScanDietTheme {
        Greeting("Android")
    }
}
