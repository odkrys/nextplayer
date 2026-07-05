package dev.anilbeesetti.nextplayer.core.media.services

import kotlinx.coroutines.flow.Flow

data class ScannerSettings(
    val scanNomedia: Boolean,
    val scanHidden: Boolean,
    val trigger: Long
)

interface ScannerSettingsProvider {
    fun observeSettings(): Flow<ScannerSettings>
}
