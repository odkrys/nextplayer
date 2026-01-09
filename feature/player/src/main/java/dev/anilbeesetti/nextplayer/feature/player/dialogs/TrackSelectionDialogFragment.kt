package dev.anilbeesetti.nextplayer.feature.player.dialogs

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.View
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
                        } else {
                            setMessage(getString(R.string.no_subtitle_tracks_found))
                        }
                        setNeutralButton("Subtitle Control") { _, _ ->
                            val controlDialog = MaterialAlertDialogBuilder(activity)
                                .setTitle("Subtitle Control")
                                .setPositiveButton(android.R.string.ok) { _, _ ->
                                    playerViewModel.applySubtitleOffset()
                                }
                                .setOnDismissListener {
                                    playerViewModel.clearPendingSubtitleOffset()
                                }
                                .create()

                            val controlView = createSubtitleControlView(
                                activity = activity,
                                playerViewModel = playerViewModel,
                                lifecycleOwner = this@TrackSelectionDialogFragment,
                                onAlphaChange = { alpha ->
                                    controlDialog.window?.decorView?.alpha = alpha
                                }
                            )
                            controlDialog.setView(controlView)
                            controlDialog.show()
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

    private fun createSubtitleControlView(
        activity: Context,
        playerViewModel: PlayerViewModel,
        lifecycleOwner: DialogFragment,
        onAlphaChange: (Float) -> Unit
    ): View {

        val positionText = TextView(activity).apply {
            text = "Position: "
            textSize = 14f
        }

        val positionSlider = Slider(activity).apply {
            valueFrom = 0f
            valueTo = 95f
            stepSize = 1f
        }

        var isPositionChanging = false

        val resetPositionButton = androidx.appcompat.widget.AppCompatImageButton(activity).apply {
            setImageResource(R.drawable.ic_reset)
            contentDescription = "Reset Position"
            setBackgroundResource(android.R.color.transparent)
            setOnClickListener {
                val newValue = 8f
                positionSlider.value = newValue
                playerViewModel.updateSubtitlePosition(newValue / 100f)
                positionText.text = "Position: ${newValue.roundToInt()}"
            }
        }

        positionSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                isPositionChanging = true
                val rounded = value.roundToInt().coerceIn(0, 95)
                positionText.text = "Position: $rounded"
                playerViewModel.updateSubtitlePosition(value / 100f)
            }
        }

        positionSlider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {
                isPositionChanging = true
                onAlphaChange(0.4f)
            }

            override fun onStopTrackingTouch(slider: Slider) {
                isPositionChanging = false
                onAlphaChange(1f)
                playerViewModel.applySubtitleOffset()
            }
        })

        val positionLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(positionSlider, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 3f))
            addView(resetPositionButton, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }

        val syncText = TextView(activity).apply {
            text = "Sync: "
            textSize = 14f
        }

        val syncSlider = Slider(activity).apply {
            valueFrom = -10f
            valueTo = 10f
            stepSize = 0.1f
        }

        var isSyncChanging = false

        val resetSyncButton = androidx.appcompat.widget.AppCompatImageButton(activity).apply {
            setImageResource(R.drawable.ic_reset)
            contentDescription = "Reset Sync"
            setBackgroundResource(android.R.color.transparent)
            setOnClickListener {
                val newValue = 0.0f
                syncSlider.value = newValue
                playerViewModel.setPendingSubtitleOffset((newValue * 1000).toLong())
                syncText.text = "Sync: %.1f s".format(newValue)
            }
        }

        syncSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                isSyncChanging = true
                syncText.text = "Sync: %.1f s".format(value)
                playerViewModel.setPendingSubtitleOffset((value * 1000).toLong())
            }
        }

        syncSlider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {
                isSyncChanging = true
            }

            override fun onStopTrackingTouch(slider: Slider) {
                isSyncChanging = false
            }
        })

        val syncLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(syncSlider, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 3f))
            addView(resetSyncButton, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }

        lifecycleOwner.lifecycleScope.launchWhenStarted {
            playerViewModel.preferencesFlow.collect { prefs ->
                if (!isPositionChanging && !isSyncChanging) {
                    val pos = (prefs.subtitlePosition * 100).roundToInt().coerceIn(0, 95)
                    val currentPending = playerViewModel.pendingSubtitleOffset.value
                    val offset = (currentPending ?: prefs.subtitleOffsetMs).toFloat() / 1000f

                    positionSlider.value = pos.toFloat()
                    syncSlider.value = offset

                    positionText.text = "Position: $pos"
                    syncText.text = "Sync: %.1f s".format(offset)
                }
            }
        }

        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
            addView(positionText)
            addView(positionLayout)
            addView(syncText)
            addView(syncLayout)
        }
    }
}
