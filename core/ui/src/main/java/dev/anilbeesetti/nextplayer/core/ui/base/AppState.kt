package dev.anilbeesetti.nextplayer.core.ui.base

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf

val LocalBottomBarVisibility = compositionLocalOf<MutableState<Boolean>> {
    error("BottomBarVisibility not provided")
}
