package com.fmorea.syncthing.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.fmorea.syncthing.util.LocalActivity
import com.fmorea.syncthing.util.LocalResources

// SyncThing Brand Colors
private val SyncThingBlue = Color(0xFF03A9F4)
private val SyncThingBlueDark = Color(0xFF0288D1)
private val SyncThingLightBlue = Color(0xFFE1F5FE) // Soft azzurrino background

private val LightColorScheme = lightColorScheme(
    primary = SyncThingBlue,
    onPrimary = Color.White,
    primaryContainer = SyncThingLightBlue,
    onPrimaryContainer = SyncThingBlueDark,
    
    secondary = SyncThingBlueDark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB3E5FC),
    onSecondaryContainer = Color(0xFF01579B),
    
    background = Color(0xFFF1F8E9), // Light mint/gray for better eye comfort than pure white
    surface = Color.White,
    onBackground = Color(0xFF1B5E20),
    onSurface = Color(0xFF263238),
    
    surfaceVariant = Color(0xFFE1F5FE), // More blue for surfaces
    outline = Color(0xFF81D4FA)
)

private val DarkColorScheme = darkColorScheme(
    primary = SyncThingBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF01579B),
    onPrimaryContainer = SyncThingLightBlue,
    
    background = Color(0xFF102027),
    surface = Color(0xFF102027),
    onBackground = Color(0xFFECEFF1),
    onSurface = Color(0xFFECEFF1),
    
    surfaceVariant = Color(0xFF263238),
    outline = Color(0xFF455A64)
)

@Composable
fun ApplicationTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val isDarkTheme = isSystemInDarkTheme()
    val colorScheme = if (isDarkTheme) DarkColorScheme else LightColorScheme

    val activity = context.findActivity()
    val resources = context.resources

    CompositionLocalProvider(
        LocalActivity provides activity,
        LocalResources provides resources
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}

private fun Context.findActivity(): Activity {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    error("Context does not contain an Activity")
}
