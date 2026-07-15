package com.fmorea.syncthing.syncthing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect as AndroidRect
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageCropper(
    imageFile: File,
    onCropDone: (File) -> Unit,
    onDismiss: () -> Unit
) {
    var rawBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var rotation by remember { mutableStateOf(0f) }
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    
    val context = LocalContext.current

    LaunchedEffect(imageFile) {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(imageFile.absolutePath, options)
        options.inSampleSize = calculateInSampleSize(options, 2000, 2000)
        options.inJustDecodeBounds = false
        rawBitmap = BitmapFactory.decodeFile(imageFile.absolutePath, options)
    }

    if (rawBitmap == null) return

    val rotatedBitmap = remember(rawBitmap, rotation) {
        if (rotation == 0f) rawBitmap else {
            val matrix = Matrix().apply { postRotate(rotation) }
            Bitmap.createBitmap(rawBitmap!!, 0, 0, rawBitmap!!.width, rawBitmap!!.height, matrix, true)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Ritaglia Foto") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
                    },
                    actions = {
                        IconButton(onClick = { 
                            rotation = (rotation + 90f) % 360f 
                            scale = 1f
                            offset = Offset.Zero
                        }) {
                            Icon(Icons.Default.RotateRight, null)
                        }
                        IconButton(onClick = {
                            val cropped = cropBitmap(rotatedBitmap!!, scale, offset, canvasSize)
                            if (cropped != null) {
                                val resultFile = File(context.cacheDir, "cropped_${System.currentTimeMillis()}.jpg")
                                FileOutputStream(resultFile).use { out ->
                                    cropped.compress(Bitmap.CompressFormat.JPEG, 90, out)
                                }
                                onCropDone(resultFile)
                            }
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
                    .background(Color.Black)
                    .onSizeChanged { canvasSize = it }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.5f, 5f)
                            offset += pan
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (rotatedBitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = rotatedBitmap!!.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            ),
                        contentScale = ContentScale.Fit
                    )
                    
                    ComposeCanvas(modifier = Modifier.fillMaxSize()) {
                        val radius = min(size.width, size.height) * 0.4f
                        val center = Offset(size.width / 2f, size.height / 2f)

                        // Draw circular mask (dim out outside)
                        drawRect(Color.Black.copy(alpha = 0.5f))
                        
                        // Guide circle
                        drawCircle(
                            color = Color.White,
                            radius = radius,
                            center = center,
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }
            }
        }
    }
}

private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
    val (height: Int, width: Int) = options.outHeight to options.outWidth
    var inSampleSize = 1
    if (height > reqHeight || width > reqWidth) {
        val halfHeight: Int = height / 2
        val halfWidth: Int = width / 2
        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}

private fun cropBitmap(bitmap: Bitmap, scale: Float, offset: Offset, canvasSize: IntSize): Bitmap? {
    if (canvasSize.width == 0 || canvasSize.height == 0) return null
    
    val viewWidth = canvasSize.width.toFloat()
    val viewHeight = canvasSize.height.toFloat()
    
    val bitmapWidth = bitmap.width.toFloat()
    val bitmapHeight = bitmap.height.toFloat()
    
    // Fit scale is what ContentScale.Fit uses
    val fitScale = min(viewWidth / bitmapWidth, viewHeight / bitmapHeight)
    
    // Circle in view coordinates
    val circleRadius = min(viewWidth, viewHeight) * 0.4f
    val circleCenter = Offset(viewWidth / 2f, viewHeight / 2f)
    
    // totalScale = fitScale * scale
    val totalScale = fitScale * scale
    
    // Bitmap center in view coordinates is (viewWidth/2, viewHeight/2) + offset
    // V = (B - Bcenter) * totalScale + Vcenter + offset
    // B = (V - Vcenter - offset) / totalScale + Bcenter
    
    val vCenter = Offset(viewWidth / 2f, viewHeight / 2f)
    val bCenter = Offset(bitmapWidth / 2f, bitmapHeight / 2f)
    
    fun viewToBitmap(v: Offset): Offset {
        return Offset(
            (v.x - vCenter.x - offset.x) / totalScale + bCenter.x,
            (v.y - vCenter.y - offset.y) / totalScale + bCenter.y
        )
    }
    
    val topLeft = viewToBitmap(Offset(circleCenter.x - circleRadius, circleCenter.y - circleRadius))
    val bottomRight = viewToBitmap(Offset(circleCenter.x + circleRadius, circleCenter.y + circleRadius))
    
    val srcRect = AndroidRect(
        topLeft.x.toInt().coerceIn(0, bitmap.width),
        topLeft.y.toInt().coerceIn(0, bitmap.height),
        bottomRight.x.toInt().coerceIn(0, bitmap.width),
        bottomRight.y.toInt().coerceIn(0, bitmap.height)
    )
    
    val outputSize = 512
    val output = Bitmap.createBitmap(outputSize, outputSize, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    canvas.drawCircle(outputSize / 2f, outputSize / 2f, outputSize / 2f, paint)
    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    
    canvas.drawBitmap(bitmap, srcRect, AndroidRect(0, 0, outputSize, outputSize), paint)
    
    return output
}
