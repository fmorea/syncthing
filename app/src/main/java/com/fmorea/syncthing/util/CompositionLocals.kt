package com.fmorea.syncthing.util

import android.app.Activity
import android.content.res.Resources
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope

val LocalActivityScope = staticCompositionLocalOf<CoroutineScope> {
    error("No activity scope provided")
}

val LocalActivity = staticCompositionLocalOf<Activity> {
    error("No Activity provided")
}

val LocalResources = staticCompositionLocalOf<Resources> {
    error("No Resources provided")
}
