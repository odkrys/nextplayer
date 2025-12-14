package dev.anilbeesetti.nextplayer.feature.player.dialogs

import android.app.Dialog
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.feature.player.PlayerViewModel
import dev.anilbeesetti.nextplayer.feature.player.extensions.getName
import kotlin.math.roundToInt

@UnstableApi
class TrackSelectionDialogFragment(
    private val type: @C.TrackType Int,
    private val tracks: Tracks,
    private val onTrackSelected: (trackIndex: Int) -> Unit,
    private val onOpenLocalTrackClicked: () -> Unit = {},
    private val playerViewModel: PlayerViewModel,
) : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        when (type) {
            C.TRACK_TYPE_AUDIO -> {
                return activity?.let { activity ->
                    val audioTracks = tracks.groups
                        .filter { it.type == C.TRACK_TYPE_AUDIO && it.isSupported }

                    val trackNames = audioTracks.mapIndexed { index, trackGroup ->
                        trackGroup.mediaTrackGroup.getName(type, index)
                    }.toTypedArray()

                    val selectedTrackIndex = audioTracks
                        .indexOfFirst { it.isSelected }.takeIf { it != -1 } ?: audioTracks.size

                    MaterialAlertDialogBuilder(activity).apply {
                        setTitle(getString(R.string.select_audio_track))
                        if (trackNames.isNotEmpty()) {
                            setSingleChoiceItems(
                                arrayOf(*trackNames, getString(R.string.disable)),
                                selectedTrackIndex,
                            ) { dialog, trackIndex ->
                                onTrackSelected(trackIndex.takeIf { it < trackNames.size } ?: -1)
                                dialog.dismiss()
                            }
                        } else {
                            setMessage(getString(R.string.no_audio_tracks_found))
                        }
                    }.create()
                } ?: throw IllegalStateException("Activity cannot be null")
            }

            C.TRACK_TYPE_TEXT -> {
                return activity?.let { activity ->
                    val textTracks = tracks.groups
                        .filter { it.type == C.TRACK_TYPE_TEXT && it.isSupported }

                    val trackNames = textTracks.mapIndexed { index, trackGroup ->
                        trackGroup.mediaTrackGroup.getName(type, index)
                    }.toTypedArray()

                    val selectedTrackIndex = textTracks
                        .indexOfFirst { it.isSelected }.takeIf { it != -1 } ?: textTracks.size

                    val initialPosition = 0.08f
                    val positionText = TextView(activity).apply {
                        text = "Subtitle Position: ${(initialPosition * 100).toInt()}"
                        textSize = 16f
                    }

                    val slider = Slider(activity).apply {
                        valueFrom = 0f
                        valueTo = 95f
                        stepSize = 1f
                        value = initialPosition * 100
                    }

                    var isUserChanging = false

                    slider.addOnChangeListener { _, value, fromUser ->
                        if (fromUser) {
                            isUserChanging = true
                            val rounded = value.roundToInt().coerceIn(0, 95)
                            positionText.text = "Subtitle Position: $rounded"
                            playerViewModel.updateSubtitlePosition(value / 100f)
                        }
                    }

                    slider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
                        override fun onStartTrackingTouch(slider: Slider) {
                            isUserChanging = true
                            dialog?.window?.decorView?.alpha = 0.4f
                        }

                        override fun onStopTrackingTouch(slider: Slider) {
                            isUserChanging = false
                            dialog?.window?.decorView?.alpha = 1f
                        }
                    })

                    lifecycleScope.launchWhenStarted {
                        playerViewModel.preferencesFlow.collect { prefs ->
                            if (!isUserChanging) {
                                val pos = (prefs.subtitlePosition * 100).roundToInt().coerceIn(0, 95)
                                slider.value = pos.toFloat()
                                positionText.text = "Subtitle Position: $pos"
                            }
                        }
                    }

                    val sliderLayout = LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(40, 40, 40, 0)
                        addView(positionText)
                        addView(slider)
                    }

                    MaterialAlertDialogBuilder(activity).apply {
                        setTitle(getString(R.string.select_subtitle_track))
                        if (trackNames.isNotEmpty()) {
                            setSingleChoiceItems(
                                arrayOf(*trackNames, getString(R.string.disable)),
                                selectedTrackIndex,
                            ) { dialog, trackIndex ->
                                onTrackSelected(trackIndex.takeIf { it < trackNames.size } ?: -1)
                                dialog.dismiss()
                            }
                            setCustomTitle(sliderLayout)
                        } else {
                            setMessage(getString(R.string.no_subtitle_tracks_found))
                            setView(sliderLayout)
                        }
                        setPositiveButton(getString(R.string.open_subtitle)) { dialog, _ ->
                            dialog.dismiss()
                            onOpenLocalTrackClicked()
                        }
                    }.create()
                } ?: throw IllegalStateException("Activity cannot be null")
            }

            else -> {
                throw IllegalArgumentException(
                    "Track type not supported. Track type must be either TRACK_TYPE_AUDIO or TRACK_TYPE_TEXT",
                )
            }
        }
    }
}
