package com.food.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.food.core.Barcodes
import com.food.core.Meal
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import java.time.LocalDate

/**
 * Camera preview that reads EAN-13 / EAN-8. A code counts only once it has been read the
 * same way twice in a row and its check digit is right, so a blurry frame can't log the
 * wrong product. The number can also be typed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(date: LocalDate, meal: Meal, onCode: (String) -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }

    var typing by rememberSaveable { mutableStateOf(false) }
    var typed by rememberSaveable { mutableStateOf("") }
    var torch by remember { mutableStateOf(false) }
    var controller by remember { mutableStateOf<LifecycleCameraController?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = { Text("Scan · ${meal.label} · ${Format.shortDate(date)}", style = MaterialTheme.typography.titleMedium) },
                actions = {
                    if (granted && !typing) TextButton(onClick = {
                        torch = !torch
                        controller?.enableTorch(torch)
                    }) { Text(if (torch) "Light off" else "Light") }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().imePadding()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when {
                    typing -> {}
                    granted -> {
                        CameraPreview(onCode = onCode, onController = { controller = it })
                        // Where to hold the barcode.
                        Box(
                            Modifier.width(280.dp).height(150.dp)
                                .border(2.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(12.dp)),
                        )
                    }
                    else -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Text("The camera is needed to read barcodes. Nothing is recorded.")
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { ask.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
                    }
                }
                if (typing) TypeNumber(typed, { typed = it }, onCode)
            }
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(16.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                TextButton(onClick = { typing = !typing }) {
                    Text(if (typing) "Use the camera" else "Type the number instead")
                }
            }
        }
    }
}

@Composable
private fun CameraPreview(onCode: (String) -> Unit, onController: (LifecycleCameraController) -> Unit) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current
    var done by remember { mutableStateOf(false) }

    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A)
                .build()
        )
    }
    val controller = remember {
        LifecycleCameraController(ctx).apply {
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
            val main = ContextCompat.getMainExecutor(ctx)
            var last: String? = null
            setImageAnalysisAnalyzer(main, MlKitAnalyzer(listOf(scanner), CameraController.COORDINATE_SYSTEM_VIEW_REFERENCED, main) { result ->
                if (done) return@MlKitAnalyzer
                val code = result.getValue(scanner)?.firstNotNullOfOrNull { b -> b.rawValue?.let(Barcodes::normalize) }
                if (code != null && code == last) {
                    done = true
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onCode(code)
                }
                last = code
            })
        }
    }

    DisposableEffect(owner) {
        controller.bindToLifecycle(owner)
        onController(controller)
        onDispose {
            controller.unbind()
            scanner.close()
        }
    }

    AndroidView(
        factory = { PreviewView(it).apply { this.controller = controller } },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun TypeNumber(typed: String, onTyped: (String) -> Unit, onCode: (String) -> Unit) {
    val code = Barcodes.normalize(typed)
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = typed,
            onValueChange = onTyped,
            label = { Text("Barcode number") },
            singleLine = true,
            isError = typed.length >= 8 && code == null,
            supportingText = {
                if (typed.length >= 8 && code == null) Text("Not a valid EAN-13 or EAN-8. Check the digits.")
                else Text("The 13 (or 8) digits under the bars.")
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = { code?.let(onCode) }, enabled = code != null, modifier = Modifier.fillMaxWidth()) { Text("Look it up") }
    }
}
