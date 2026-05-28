package dev.anilbeesetti.nextplayer.core.media.services

import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.activity.ComponentActivity

interface MediaService {
    fun initialize(activity: ComponentActivity)
    suspend fun deleteMedia(uris: List<Uri>): Boolean
    suspend fun renameMedia(uri: Uri, to: String): Boolean
    suspend fun shareMedia(uris: List<Uri>)

    companion object {
        fun willSystemAsksForDeleteConfirmation(): Boolean {
            //return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                !Environment.isExternalStorageManager()
            } else {
                false
            }
        }
    }
}
