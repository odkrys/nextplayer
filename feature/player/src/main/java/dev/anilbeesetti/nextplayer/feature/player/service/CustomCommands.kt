package dev.anilbeesetti.nextplayer.feature.player.service

import android.net.Uri
import android.os.Bundle
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import dev.anilbeesetti.nextplayer.core.model.DrcPreset
import kotlinx.coroutines.guava.await

enum class CustomCommands(val customAction: String) {
    ADD_SUBTITLE_TRACK(customAction = "ADD_SUBTITLE_TRACK"),
    SET_SKIP_SILENCE_ENABLED(customAction = "SET_SKIP_SILENCE_ENABLED"),
    GET_SKIP_SILENCE_ENABLED(customAction = "GET_SKIP_SILENCE_ENABLED"),
    SET_IS_SCRUBBING_MODE_ENABLED(customAction = "SET_IS_SCRUBBING_MODE_ENABLED"),
    GET_SUBTITLE_DELAY(customAction = "GET_SUBTITLE_DELAY"),
    SET_SUBTITLE_DELAY(customAction = "SET_SUBTITLE_DELAY"),
    GET_SUBTITLE_SPEED(customAction = "GET_SUBTITLE_SPEED"),
    SET_SUBTITLE_SPEED(customAction = "SET_SUBTITLE_SPEED"),
    STOP_PLAYER_SESSION(customAction = "STOP_PLAYER_SESSION"),
    IS_LOUDNESS_GAIN_SUPPORTED(customAction = "IS_LOUDNESS_GAIN_SUPPORTED"),
    SET_LOUDNESS_GAIN(customAction = "SET_LOUDNESS_GAIN"),
    GET_LOUDNESS_GAIN(customAction = "GET_LOUDNESS_GAIN"),
    SET_DRC_ENABLED(customAction = "SET_DRC_ENABLED"),
    SET_DRC_PRESET(customAction = "SET_DRC_PRESET"),
    IS_DRC_SUPPORTED(customAction = "IS_DRC_SUPPORTED"),
    SET_SKIP_INTRO_ENABLED(customAction = "SET_SKIP_INTRO_ENABLED"),
    GET_SKIP_INTRO_ENABLED(customAction = "GET_SKIP_INTRO_ENABLED"),
    SET_SKIP_INTRO_TIME(customAction = "SET_SKIP_INTRO_TIME"),
    GET_SKIP_INTRO_TIME(customAction = "GET_SKIP_INTRO_TIME"),
    SET_AB_REPEAT_A(customAction = "SET_AB_REPEAT_A"),
    SET_AB_REPEAT_B(customAction = "SET_AB_REPEAT_B"),
    CLEAR_AB_REPEAT(customAction = "CLEAR_AB_REPEAT"),
    GET_AB_REPEAT_STATE(customAction = "GET_AB_REPEAT_STATE"),
    SET_SLEEP_TIMER(customAction = "SET_SLEEP_TIMER"),
    CANCEL_SLEEP_TIMER(customAction = "CANCEL_SLEEP_TIMER"),
    GET_SLEEP_TIMER_REMAINING(customAction = "GET_SLEEP_TIMER_REMAINING"),
    SET_PAUSE_AT_END_ONCE(customAction = "SET_PAUSE_AT_END_ONCE"),
    ;

    val sessionCommand = SessionCommand(customAction, Bundle.EMPTY)

    companion object {
        fun fromSessionCommand(sessionCommand: SessionCommand): CustomCommands? {
            return entries.find { it.customAction == sessionCommand.customAction }
        }

        fun asSessionCommands(): List<SessionCommand> {
            return entries.map { it.sessionCommand }
        }

        const val SUBTITLE_TRACK_URI_KEY = "subtitle_track_uri"
        const val SKIP_SILENCE_ENABLED_KEY = "skip_silence_enabled"
        const val IS_SCRUBBING_MODE_ENABLED_KEY = "is_scrubbing_mode_enabled"
        const val SUBTITLE_DELAY_KEY = "subtitle_delay"
        const val SUBTITLE_SPEED_KEY = "subtitle_speed"
        const val LOUDNESS_GAIN_KEY = "loudness_gain"
        const val IS_LOUDNESS_GAIN_SUPPORTED_KEY = "is_loudness_gain_supported"
        const val DRC_ENABLED_KEY = "drc_enabled"
        const val DRC_PRESET_KEY = "drc_preset"
        const val IS_DRC_SUPPORTED_KEY = "is_drc_supported"
        const val SKIP_INTRO_ENABLED_KEY = "skip_intro_enabled"
        const val SKIP_INTRO_TIME_KEY = "skip_intro_time"
        const val AB_REPEAT_A_KEY = "ab_repeat_a_ms"
        const val AB_REPEAT_B_KEY = "ab_repeat_b_ms"
        const val SLEEP_TIMER_DURATION_KEY = "sleep_timer_duration_ms"
        const val SLEEP_TIMER_REMAINING_KEY = "sleep_timer_remaining_ms"
        const val PAUSE_AT_END_ONCE_KEY = "pause_at_end_once"
    }
}

fun MediaController.addSubtitleTrack(uri: Uri) {
    val args = Bundle().apply {
        putString(CustomCommands.SUBTITLE_TRACK_URI_KEY, uri.toString())
    }
    sendCustomCommand(CustomCommands.ADD_SUBTITLE_TRACK.sessionCommand, args)
}

suspend fun MediaController.setSkipSilenceEnabled(enabled: Boolean) {
    val args = Bundle().apply {
        putBoolean(CustomCommands.SKIP_SILENCE_ENABLED_KEY, enabled)
    }
    sendCustomCommand(CustomCommands.SET_SKIP_SILENCE_ENABLED.sessionCommand, args).await()
}

fun MediaController.setMediaControllerIsScrubbingModeEnabled(enabled: Boolean) {
    val args = Bundle().apply {
        putBoolean(CustomCommands.IS_SCRUBBING_MODE_ENABLED_KEY, enabled)
    }
    sendCustomCommand(CustomCommands.SET_IS_SCRUBBING_MODE_ENABLED.sessionCommand, args)
}

suspend fun MediaController.getSkipSilenceEnabled(): Boolean {
    val result = sendCustomCommand(CustomCommands.GET_SKIP_SILENCE_ENABLED.sessionCommand, Bundle.EMPTY)
    return result.await().extras.getBoolean(CustomCommands.SKIP_SILENCE_ENABLED_KEY, false)
}

fun MediaController.setSubtitleDelayMilliseconds(delayMillis: Long) {
    val args = Bundle().apply {
        putLong(CustomCommands.SUBTITLE_DELAY_KEY, delayMillis)
    }
    sendCustomCommand(CustomCommands.SET_SUBTITLE_DELAY.sessionCommand, args)
}

suspend fun MediaController.getSubtitleDelayMilliseconds(): Long {
    val result = sendCustomCommand(CustomCommands.GET_SUBTITLE_DELAY.sessionCommand, Bundle.EMPTY)
    return result.await().extras.getLong(CustomCommands.SUBTITLE_DELAY_KEY, 0L)
}

fun MediaController.setSubtitleSpeed(speed: Float) {
    val args = Bundle().apply {
        putFloat(CustomCommands.SUBTITLE_SPEED_KEY, speed)
    }
    sendCustomCommand(CustomCommands.SET_SUBTITLE_SPEED.sessionCommand, args)
}

suspend fun MediaController.getSubtitleSpeed(): Float {
    val result = sendCustomCommand(CustomCommands.GET_SUBTITLE_SPEED.sessionCommand, Bundle.EMPTY)
    return result.await().extras.getFloat(CustomCommands.SUBTITLE_SPEED_KEY, 1f)
}

fun MediaController.stopPlayerSession() {
    sendCustomCommand(CustomCommands.STOP_PLAYER_SESSION.sessionCommand, Bundle.EMPTY)
}

fun MediaController.setLoudnessGain(gain: Int) {
    val args = Bundle().apply {
        putInt(CustomCommands.LOUDNESS_GAIN_KEY, gain)
    }
    sendCustomCommand(CustomCommands.SET_LOUDNESS_GAIN.sessionCommand, args)
}

suspend fun MediaController.getLoudnessGain(): Int {
    val result = sendCustomCommand(CustomCommands.GET_LOUDNESS_GAIN.sessionCommand, Bundle.EMPTY)
    return result.await().extras.getInt(CustomCommands.LOUDNESS_GAIN_KEY, 0)
}

suspend fun MediaController.getIsLoudnessGainSupported(): Boolean {
    val result = sendCustomCommand(CustomCommands.IS_LOUDNESS_GAIN_SUPPORTED.sessionCommand, Bundle.EMPTY)
    return result.await().extras.getBoolean(CustomCommands.IS_LOUDNESS_GAIN_SUPPORTED_KEY, false)
}

fun MediaController.setDrcEnabled(enabled: Boolean) {
    val args = Bundle().apply {
        putBoolean(CustomCommands.DRC_ENABLED_KEY, enabled)
    }
    sendCustomCommand(CustomCommands.SET_DRC_ENABLED.sessionCommand, args)
}

fun MediaController.setDrcPreset(preset: DrcPreset) {
    val args = Bundle().apply {
        putString(CustomCommands.DRC_PRESET_KEY, preset.name)
    }
    sendCustomCommand(CustomCommands.SET_DRC_PRESET.sessionCommand, args)
}

suspend fun MediaController.getIsDrcSupported(): Boolean {
    val result = sendCustomCommand(CustomCommands.IS_DRC_SUPPORTED.sessionCommand, Bundle.EMPTY)
    return result.await().extras.getBoolean(CustomCommands.IS_DRC_SUPPORTED_KEY, false)
}

suspend fun MediaController.getSkipIntroEnabled(): Boolean {
    val result = sendCustomCommand(CustomCommands.GET_SKIP_INTRO_ENABLED.sessionCommand, Bundle.EMPTY)
    return result.await().extras.getBoolean(CustomCommands.SKIP_INTRO_ENABLED_KEY, false)
}

fun MediaController.setSkipIntroEnabled(enabled: Boolean) {
    val args = Bundle().apply {
        putBoolean(CustomCommands.SKIP_INTRO_ENABLED_KEY, enabled)
    }
    sendCustomCommand(CustomCommands.SET_SKIP_INTRO_ENABLED.sessionCommand, args)
}

suspend fun MediaController.getSkipIntroTime(): Int {
    val result = sendCustomCommand(CustomCommands.GET_SKIP_INTRO_TIME.sessionCommand, Bundle.EMPTY)
    return result.await().extras.getInt(CustomCommands.SKIP_INTRO_TIME_KEY, 0)
}

fun MediaController.setSkipIntroTime(timeSeconds: Int) {
    val args = Bundle().apply {
        putInt(CustomCommands.SKIP_INTRO_TIME_KEY, timeSeconds)
    }
    sendCustomCommand(CustomCommands.SET_SKIP_INTRO_TIME.sessionCommand, args)
}

fun MediaController.setAbRepeatA(positionMs: Long) {
    val args = Bundle().apply {
        putLong(CustomCommands.AB_REPEAT_A_KEY, positionMs)
    }
    sendCustomCommand(CustomCommands.SET_AB_REPEAT_A.sessionCommand, args)
}

fun MediaController.setAbRepeatB(positionMs: Long) {
    val args = Bundle().apply {
        putLong(CustomCommands.AB_REPEAT_B_KEY, positionMs)
    }
    sendCustomCommand(CustomCommands.SET_AB_REPEAT_B.sessionCommand, args)
}

fun MediaController.clearAbRepeat() {
    sendCustomCommand(CustomCommands.CLEAR_AB_REPEAT.sessionCommand, Bundle.EMPTY)
}

suspend fun MediaController.getAbRepeatState(): Pair<Long, Long> {
    val result = sendCustomCommand(CustomCommands.GET_AB_REPEAT_STATE.sessionCommand, Bundle.EMPTY).await()
    return Pair(
        result.extras.getLong(CustomCommands.AB_REPEAT_A_KEY, androidx.media3.common.C.TIME_UNSET),
        result.extras.getLong(CustomCommands.AB_REPEAT_B_KEY, androidx.media3.common.C.TIME_UNSET),
    )
}

fun MediaController.setSleepTimer(durationMs: Long) {
    val args = Bundle().apply {
        putLong(CustomCommands.SLEEP_TIMER_DURATION_KEY, durationMs)
    }
    sendCustomCommand(CustomCommands.SET_SLEEP_TIMER.sessionCommand, args)
}

fun MediaController.cancelSleepTimer() {
    sendCustomCommand(CustomCommands.CANCEL_SLEEP_TIMER.sessionCommand, Bundle.EMPTY)
}

suspend fun MediaController.getSleepTimerState(): Triple<Long, Long, Boolean> {
    val result = sendCustomCommand(CustomCommands.GET_SLEEP_TIMER_REMAINING.sessionCommand, Bundle.EMPTY).await()
    return Triple(
        result.extras.getLong(CustomCommands.SLEEP_TIMER_REMAINING_KEY, 0L),
        result.extras.getLong(CustomCommands.SLEEP_TIMER_DURATION_KEY, 0L),
        result.extras.getBoolean(CustomCommands.PAUSE_AT_END_ONCE_KEY, false)
    )
}

fun MediaController.setPauseAtEndOnce(enabled: Boolean) {
    sendCustomCommand(
        CustomCommands.SET_PAUSE_AT_END_ONCE.sessionCommand,
        Bundle().apply { putBoolean(CustomCommands.PAUSE_AT_END_ONCE_KEY, enabled) }
    )
}
