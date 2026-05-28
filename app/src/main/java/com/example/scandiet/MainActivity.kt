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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.annotation.StringRes
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

data class DietaryNeed(
    val key: String,
    @StringRes val nameRes: Int,
    val isChecked: Boolean,
    val labels: List<String>
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
    DietaryNeed("sugar", R.string.need_sugar, false, listOf("sugar")),
    DietaryNeed("sodium", R.string.need_sodium, false, listOf("sodium"))
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
        items(dietaryNeeds) { need ->
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { updateNeed(need, !need.isChecked) },
                colors = CardDefaults.elevatedCardColors(
                    containerColor = if (need.isChecked) 
                        MaterialTheme.colorScheme.primaryContainer 
                    else 
                        MaterialTheme.colorScheme.surfaceContainerLow
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
                        onCheckedChange = { isGranted -> updateNeed(need, isGranted) }
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = stringResource(need.nameRes),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (need.isChecked) 
                            MaterialTheme.colorScheme.onPrimaryContainer 
                        else 
                            MaterialTheme.colorScheme.onSurface
                    )
                }
            }
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
    val labelsToHighlight = remember(selectedNeeds) {
        selectedNeeds.flatMap { it.labels }.toSet()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(history) { item ->
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onItemClick(item) },
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
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
                    
                    val allergensFound = item.productInfo.labels.keys.filter { label ->
                        labelsToHighlight.any { it.equals(label, ignoreCase = true) }
                    }
                    
                    if (allergensFound.isNotEmpty()) {
                        Text(
                            text = "Contains: ${allergensFound.joinToString(", ")}",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        Text(
                            text = "No selected allergens found",
                            color = Color(0xFF2E7D32), // Success green
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.barcode,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun ScannerScreen(modifier: Modifier = Modifier, onBarcodeScanned: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraController = remember { LifecycleCameraController(context) }

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
                result.getValue(barcodeScanner)?.firstOrNull()?.rawValue?.let {
                    onBarcodeScanned(it)
                    Log.d("ScannerScreen", "Barcode raw value: $it")
                }
        }
    )

    cameraController.bindToLifecycle(lifecycleOwner)

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { context ->
                PreviewView(context).apply {
                    this.controller = cameraController
                }
            },
            modifier = Modifier.fillMaxSize()
        )
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

    val labelsToHighlight = remember(selectedNeeds) {
        selectedNeeds.flatMap { it.labels }.toSet()
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
                                if (labelsToHighlight.any { it.equals(label, ignoreCase = true) }) {
                                    spans.forEach { span ->
                                        if (span.size >= 2) {
                                            addStyle(
                                                style = SpanStyle(color = Color.White),
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

                        var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
                        Text(
                            text = annotatedString,
                            onTextLayout = { textLayoutResult = it },
                            style = MaterialTheme.typography.bodyLarge.copy(
                                lineHeight = 32.sp
                            ),
                            modifier = Modifier.drawBehind {
                                textLayoutResult?.let { layoutResult ->
                                    annotatedString.getStringAnnotations("HIGHLIGHT", 0, annotatedString.length)
                                        .forEach { annotation ->
                                            val start = annotation.start
                                            val end = annotation.end
                                            val startLine = layoutResult.getLineForOffset(start)
                                            val endLine = layoutResult.getLineForOffset(end)

                                            for (line in startLine..endLine) {
                                                val lineStart = if (line == startLine) start else layoutResult.getLineStart(line)
                                                val lineEnd = if (line == endLine) end else layoutResult.getLineEnd(line)

                                                val left = layoutResult.getHorizontalPosition(lineStart, true)
                                                val right = layoutResult.getHorizontalPosition(lineEnd, true)
                                                
                                                val baseline = layoutResult.getLineBaseline(line)
                                                val top = baseline - 18.sp.toPx()
                                                val bottom = baseline + 4.sp.toPx()

                                                drawRoundRect(
                                                    color = Color.Red,
                                                    topLeft = Offset(left, top),
                                                    size = Size(right - left, bottom - top),
                                                    cornerRadius = CornerRadius(4.dp.toPx())
                                                )
                                            }
                                        }
                                }
                            }
                        )
                    }
                }

                if (selectedNeeds.isNotEmpty()) {
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            val activeFiltersText = selectedNeeds.joinToString(", ") { context.getString(it.nameRes) }
                            Text(
                                text = stringResource(R.string.active_filters, activeFiltersText),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            val foundLabels = productInfo.labels.keys.filter { k -> labelsToHighlight.any { it.equals(k, ignoreCase = true) } }
                            if (foundLabels.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Alert: Matching allergens highlighted in red.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.no_matches_found),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF2E7D32)
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
