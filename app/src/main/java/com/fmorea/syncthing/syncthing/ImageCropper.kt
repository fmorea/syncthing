package com.fmorea.syncthing.syncthing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageCropper(
    imageFile: File,
    onCropDone: (File) -> Unit,
    onDismiss: () -> Unit
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var rotation by remember { mutableStateOf(0f) }
    val context = LocalContext.current

    LaunchedEffect(imageFile) {
        bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
    }

    if (bitmap == null) return

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ritaglia Immagine") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
                },
                actions = {
                    IconButton(onClick = { rotation = (rotation + 90f) % 360f }) {
                        Icon(Icons.Default.RotateRight, null)
                    }
                    IconButton(onClick = {
                        val rotatedBitmap = if (rotation != 0f) {
                            val matrix = Matrix().apply { postRotate(rotation) }
                            Bitmap.createBitmap(bitmap!!, 0, 0, bitmap!!.width, bitmap!!.height, matrix, true)
                        } else {
                            bitmap!!
                        }

                        val resultFile = File(context.cacheDir, "cropped_${System.currentTimeMillis()}.jpg")
                        FileOutputStream(resultFile).use { out ->
                            rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                        }
                        onCropDone(resultFile)
                    }) {
                        Icon(Icons.Default.Check, null)
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            val rotatedBitmap = remember(bitmap, rotation) {
                if (rotation == 0f) bitmap else {
                    val matrix = Matrix().apply { postRotate(rotation) }
                    Bitmap.createBitmap(bitmap!!, 0, 0, bitmap!!.width, bitmap!!.height, matrix, true)
                }
            }

            if (rotatedBitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = rotatedBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                
                // Simple visual guide for cropping (UI only for now)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(
                        color = Color.White,
                        topLeft = Offset(size.width * 0.1f, size.height * 0.2f),
                        size = Size(size.width * 0.8f, size.width * 0.8f),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }
        }
    }
}
