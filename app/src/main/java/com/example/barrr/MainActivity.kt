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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
                        if (it != AppDestinations.SCANNER) {
                            scannedBarcode = null
                        }
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
                    currentDestination = AppDestinations.PROFILE
                }
                AppDestinations.PROFILE -> {
                    if (scannedBarcode != null) {
                        ProfileScreen(barcode = scannedBarcode!!, modifier = Modifier.padding(innerPadding))
                    } else {
                        Greeting("Profile", modifier = Modifier.padding(innerPadding))
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
    PROFILE("Profile", Icons.Filled.AccountBox),
}

data class DietaryNeed(val name: String, val isChecked: Boolean)

@Composable
fun DietaryNeedsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("dietary_prefs", Context.MODE_PRIVATE)
    }
    val allNeeds = remember {
        listOf(
            "Vegetarian",
            "Vegan",
            "Gluten-Free",
            "Dairy-Free",
            "Nut-Free"
        )
    }

    var dietaryNeeds by remember {
        val savedNeeds = prefs.getStringSet("dietary_needs", emptySet()) ?: emptySet()
        mutableStateOf(allNeeds.map { DietaryNeed(it, savedNeeds.contains(it)) })
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

@Composable
fun ProfileScreen(barcode: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "Scanned Barcode: $barcode")
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
