package dev.anilbeesetti.nextplayer.feature.player.dialogs

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.session.MediaController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.anilbeesetti.nextplayer.core.common.extensions.round
import dev.anilbeesetti.nextplayer.core.common.extensions.roundToNearestStep
import dev.anilbeesetti.nextplayer.core.ui.R as coreUiR
import dev.anilbeesetti.nextplayer.feature.player.databinding.PlaybackSpeedBinding
import dev.anilbeesetti.nextplayer.feature.player.service.getSkipSilenceEnabled
import dev.anilbeesetti.nextplayer.feature.player.service.setSkipSilenceEnabled
import dev.anilbeesetti.nextplayer.feature.player.service.setSpeed
import kotlinx.coroutines.launch

class PlaybackSpeedControlsDialogFragment(
    private val mediaController: MediaController,
) : DialogFragment() {

    private lateinit var binding: PlaybackSpeedBinding

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = PlaybackSpeedBinding.inflate(layoutInflater)

        return activity?.let { activity ->
            binding.apply {
                val currentSpeed = mediaController.playbackParameters.speed
                speedText.text = "%.2f".format(currentSpeed)
                speed.value = currentSpeed.roundToNearestStep(0.05f)
                lifecycleScope.launch {
                    skipSilence.isChecked = mediaController.getSkipSilenceEnabled()
                }

                speed.addOnChangeListener { _, _, _ ->
                    val newSpeed = speed.value.roundToNearestStep(0.05f)
                    mediaController.setSpeed(newSpeed)
                    speedText.text = "%.2f".format(newSpeed)
                }
                incSpeed.setOnClickListener {
                    if (speed.value < 4.0f) {
                        speed.value = (speed.value + 0.05f).roundToNearestStep(0.05f)
                    }
                }
                decSpeed.setOnClickListener {
                    if (speed.value > 0.2f) {
                        speed.value = (speed.value - 0.05f).roundToNearestStep(0.05f)
                    }
                }
                resetSpeed.setOnClickListener { speed.value = 1.0f.roundToNearestStep(0.05f) }
                button025x.setOnClickListener { speed.value = 0.25f.roundToNearestStep(0.05f) }
                button05x.setOnClickListener { speed.value = 0.5f.roundToNearestStep(0.05f) }
                button075x.setOnClickListener { speed.value = 0.75f.roundToNearestStep(0.05f) }
                button10x.setOnClickListener { speed.value = 1.0f.roundToNearestStep(0.05f)}
                button125x.setOnClickListener { speed.value = 1.25f.roundToNearestStep(0.05f) }
                button15x.setOnClickListener { speed.value = 1.5f.roundToNearestStep(0.05f) }
                button175x.setOnClickListener { speed.value = 1.75f.roundToNearestStep(0.05f) }
                button20x.setOnClickListener { speed.value = 2.0f.roundToNearestStep(0.05f) }

                skipSilence.setOnCheckedChangeListener { _, isChecked ->
                    mediaController.setSkipSilenceEnabled(isChecked)
                }
            }

            val builder = MaterialAlertDialogBuilder(activity)
            builder.setTitle(getString(coreUiR.string.select_playback_speed))
                .setView(binding.root)
                .create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }
}
