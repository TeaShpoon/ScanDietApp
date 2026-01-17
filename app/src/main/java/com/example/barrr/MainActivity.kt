package com.example.barrr

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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.barrr.ui.theme.BarrrTheme
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
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
            BarrrTheme {
                BarrrApp()
            }
        }
    }
}

@PreviewScreenSizes
@Composable
fun BarrrApp() {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.DIETARY) }
    var scannedBarcode by rememberSaveable { mutableStateOf<String?>(null) }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach {
                item(
                    icon = {
                        Icon(
                            it.icon,
                            contentDescription = it.label
                        )
                    },
                    label = { Text(it.label) },
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
                    scannedBarcode = it
                    currentDestination = AppDestinations.INFO
                }
                AppDestinations.INFO -> {
                    if (scannedBarcode != null) {
                        InfoScreen(barcode = scannedBarcode!!, modifier = Modifier.padding(innerPadding))
                    } else {
                        Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                            Text("Scan a barcode to see information here.")
                        }
                    }
                }
            }
        }
    }
}

enum class AppDestinations(
    val label: String,
    val icon: ImageVector,
) {
    DIETARY("Dietary", Icons.Filled.Checklist),
    SCANNER("Scanner", Icons.Filled.QrCodeScanner),
    INFO("Info", Icons.Filled.Info),
}

data class DietaryNeed(val name: String, val isChecked: Boolean, val keywords: List<String>)

val allNeeds = listOf(
    DietaryNeed("Вегетарианство", false, listOf(
        "мясо", "мяса", "мясу", "мясом", "мясе", "мясной", "мясная", "мясное", "мясные", "мясного", "мясному", "мясным", "мясном", "мясную", "мясною",
        "курица", "курицы", "курице", "курицу", "курицей", "куриный", "куриная", "куриное", "куриные", "куриного", "куриному", "куриным", "курином", "куриную",
        "говядина", "говядины", "говядине", "говядину", "говядиной", "говяжий", "говяжья", "говяжье", "говяжьи", "говяжьего", "говяжьему", "говяжьим", "говяжьем", "говяжью",
        "свинина", "свинины", "свинине", "свинину", "свининой", "свиной", "свиная", "свиное", "свиные", "свиного", "свиному", "свиным", "свином", "свиную",
        "рыба", "рыбы", "рыбе", "рыбу", "рыбой", "рыбный", "рыбная", "рыбное", "рыбные", "рыбного", "рыбному", "рыбным", "рыбном", "рыбную", "рыбной",
        "желатин", "желатина", "желатину", "желатином", "желатине", "желатиновый", "желатиновая", "желатиновое", "желатиновые"
    )),
    DietaryNeed("Веганство", false, listOf(
        "мясо", "мяса", "мясу", "мясом", "мясе", "мясной", "мясная", "мясное", "мясные", "мясного", "мясному", "мясным", "мясном", "мясной", "мясную", "мясною",
        "курица", "курицы", "курице", "курицу", "курицей", "куриный", "куриная", "куриное", "куриные", "куриного", "куриному", "куриным", "курином", "куриную",
        "говядина", "говядины", "говядине", "говядину", "говядиной", "говяжий", "говяжья", "говяжье", "говяжьи", "говяжьего", "говяжьему", "говяжьим", "говяжьем", "говяжью",
        "свинина", "свинины", "свинине", "свинину", "свининой", "свиной", "свиная", "свиное", "свиные", "свиного", "свиному", "свиным", "свином", "свиную",
        "рыба", "рыбы", "рыбе", "рыбу", "рыбой", "рыбный", "рыбная", "рыбное", "рыбные", "рыбного", "рыбному", "рыбным", "рыбном", "рыбную", "рыбной",
        "желатин", "желатина", "желатину", "желатином", "желатине", "желатиновый", "желатиновая", "желатиновое", "желатиновые",
        "молоко", "молока", "молоку", "молоком", "молоке", "молочный", "молочная", "молочное", "молочные", "молочного", "молочному", "молочным", "молочном", "молочную", "молочной", "молочные продукты",
        "лактоза", "лактозы", "лактозе", "лактозу", "лактозой", "лактозный",
        "яйцо", "яйца", "яиц", "яйцам", "яйцами", "яйцах", "яичный", "яичная", "яичное", "яичные", "яичного", "яичному", "яичным", "яичном", "яичную", "яичной",
        "мед", "меда", "меду", "медом", "меде", "медовый", "медовая", "медовое", "медовые", "медового", "медовому", "медовым", "медовом", "медовую", "медовой"
    )),
    DietaryNeed("Без глютена", false, listOf(
        "глютен", "глютена", "глютену", "глютеном", "глютене", "глютеновый",
        "пшеница", "пшеницы", "пшенице", "пшеницу", "пшеницей", "пшеничный", "пшеничная", "пшеничное", "пшеничные", "пшеничного", "пшеничному", "пшеничным", "пшеничном", "пшеничную", "пшеничной",
        "ячмень", "ячменя", "ячменю", "ячменем", "ячмене", "ячменный", "ячменная", "ячменное", "ячменные", "ячменного", "ячменному", "ячменным", "ячменном", "ячменную", "ячменной",
        "рожь", "ржи", "рожью", "ржаной", "ржаная", "ржаное", "ржаные", "ржаного", "ржаному", "ржаным", "ржаном", "ржаную", "ржаной"
    )),
    DietaryNeed("Без молочных продуктов", false, listOf(
        "молоко", "молока", "молоку", "молоком", "молоке", "молочный", "молочная", "молочное", "молочные", "молочного", "молочному", "молочным", "молочном", "молочную", "молочной", "молочные продукты",
        "лактоза", "лактозы", "лактозе", "лактозу", "лактозой", "лактозный",
        "сыр", "сыра", "сыру", "сыром", "сыре", "сырный", "сырная", "сырное", "сырные", "сырного", "сырному", "сырным", "сырном", "сырную", "сырной",
        "масло", "масла", "маслу", "маслом", "масле", "масляный", "масляная", "масляное", "масляные",
        "йогурт", "йогурта", "йогурту", "йогуртом", "йогурте", "йогуртовый", "йогуртовая", "йогуртовое", "йогуртовые"
    )),
    DietaryNeed("Без орехов", false, listOf(
        "орех", "ореха", "ореху", "орехом", "орехе", "орехи", "орехов", "орехам", "орехами", "орехах", "ореховый", "ореховая", "ореховое", "ореховые",
        "арахис", "арахиса", "арахису", "арахисом", "арахисе", "арахисовый",
        "миндаль", "миндаля", "миндалю", "миндалем", "миндале", "миндальный",
        "кешью",
        "грецкий орех", "грецкого ореха", "грецкому ореху", "грецким орехом", "грецком орехе", "грецкие орехи"
    )),
    DietaryNeed("Пескетарианство", false, listOf(
        "мясо", "мяса", "мясу", "мясом", "мясе", "мясной", "мясная", "мясное", "мясные", "мясного", "мясному", "мясным", "мясном", "мясной", "мясную", "мясною",
        "курица", "курицы", "курице", "курицу", "курицей", "куриный", "куриная", "куриное", "куриные", "куриного", "куриному", "куриным", "курином", "куриную",
        "говядина", "говядины", "говядине", "говядину", "говядиной", "говяжий", "говяжья", "говяжье", "говяжьи", "говяжьего", "говяжьему", "говяжьим", "говяжьем", "говяжью",
        "свинина", "свинины", "свинине", "свинину", "свининой", "свиной", "свиная", "свиное", "свиные", "свиного", "свиному", "свиным", "свином", "свиную"
    )),
    DietaryNeed("Без сои", false, listOf(
        "соя", "сои", "сое", "сою", "соей", "соевый", "соевая", "соевое", "соевые", "соевого", "соевому", "соевым", "соевом", "соевую", "соевой",
        "бобы", "бобов", "бобам", "бобами", "бобах", "бобовый",
        "тофу"
    )),
    DietaryNeed("Без моллюсков", false, listOf(
        "моллюск", "моллюска", "моллюску", "моллюском", "моллюске", "моллюски", "моллюсков", "моллюскам", "моллюсками", "моллюсках",
        "креветка", "креветки", "креветок", "креветкам", "креветками", "креветках", "креветочный",
        "краб", "краба", "крабу", "крабом", "крабе", "крабовый",
        "лобстер", "лобстера", "лобстеру", "лобстером", "лобстере",
        "устрица", "устрицы", "устриц", "устрицам", "устрицами", "устрицах", "устричный",
        "мидия", "мидии", "мидий", "мидиям", "мидиями", "мидиях"
    ))
)

@Composable
fun DietaryNeedsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("dietary_prefs", Context.MODE_PRIVATE)
    }

    var dietaryNeeds by remember {
        val savedNeeds = prefs.getStringSet("dietary_needs", emptySet()) ?: emptySet()
        mutableStateOf(allNeeds.map { it.copy(isChecked = savedNeeds.contains(it.name)) })
    }

    fun updateNeed(need: DietaryNeed, isChecked: Boolean) {
        val updatedNeeds = dietaryNeeds.map {
            if (it.name == need.name) {
                it.copy(isChecked = isChecked)
            } else {
                it
            }
        }
        dietaryNeeds = updatedNeeds
        val newSavedNeeds = updatedNeeds.filter { it.isChecked }.map { it.name }.toSet()
        prefs.edit().putStringSet("dietary_needs", newSavedNeeds).apply()
    }

    LazyColumn(modifier = modifier.padding(16.dp)) {
        items(dietaryNeeds) { need ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { updateNeed(need, !need.isChecked) }
                    .padding(vertical = 8.dp)
            ) {
                Checkbox(
                    checked = need.isChecked,
                    onCheckedChange = { isChecked -> updateNeed(need, isChecked) }
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(text = need.name)
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

@Serializable
data class ProductInfo(val name: String, val ingredients: String)

@Composable
fun InfoScreen(barcode: String, modifier: Modifier = Modifier) {
    var productInfo by remember { mutableStateOf<ProductInfo?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    val keywordsToHighlight = remember {
        val prefs = context.getSharedPreferences("dietary_prefs", Context.MODE_PRIVATE)
        val savedNeeds = prefs.getStringSet("dietary_needs", emptySet()) ?: emptySet()
        allNeeds.filter { savedNeeds.contains(it.name) }.flatMap { it.keywords }.distinct()
    }

    LaunchedEffect(barcode) {
        val client = HttpClient(CIO)
        try {
            val responseText = client.get("http://127.0.0.1:3000/$barcode").bodyAsText()
            productInfo = Json.decodeFromString(responseText)
            client.close()
        } catch (e: Exception) {
            error = "Error: ${e.message}"
            client.close()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        when {
            productInfo != null -> {
                Column {
                    Text(text = productInfo!!.name, style = MaterialTheme.typography.headlineLarge)
                    Spacer(modifier = Modifier.height(16.dp))
                    val annotatedString = buildAnnotatedString {
                        append(productInfo!!.ingredients)
                        keywordsToHighlight.forEach { keyword ->
                            var startIndex = productInfo!!.ingredients.indexOf(keyword, ignoreCase = true)
                            while (startIndex != -1) {
                                addStyle(
                                    style = SpanStyle(color = Color.Red),
                                    start = startIndex,
                                    end = startIndex + keyword.length
                                )
                                startIndex = productInfo!!.ingredients.indexOf(keyword, startIndex + 1, ignoreCase = true)
                            }
                        }
                    }
                    Text(text = annotatedString)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = barcode, style = MaterialTheme.typography.labelSmall)
                }
            }
            error != null -> {
                Text(text = error!!)
            }
            else -> {
                Text(text = "Loading...")
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
    BarrrTheme {
        Greeting("Android")
    }
}
