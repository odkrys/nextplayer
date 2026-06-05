package dev.anilbeesetti.nextplayer.feature.player.extensions

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import dev.anilbeesetti.nextplayer.core.common.Utils
import dev.anilbeesetti.nextplayer.core.model.AudioStreamInfo
import dev.anilbeesetti.nextplayer.core.model.SubtitleStreamInfo
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.model.VideoStreamInfo

@OptIn(UnstableApi::class)
fun Player.remoteVideoInfo(currentVideo: Video?): Video? {
    if (this.playbackState == Player.STATE_IDLE) return currentVideo

    var videoStreamInfo: VideoStreamInfo? = null
    val audioStreams = mutableListOf<AudioStreamInfo>()
    val subtitleStreams = mutableListOf<SubtitleStreamInfo>()

    var audioIndex = 0
    var subIndex = 0

    for (trackGroupInfo in this.currentTracks.groups) {
        val format = trackGroupInfo.getTrackFormat(0)
        val isSelected = trackGroupInfo.isSelected
        val simpleCodecName = getMimeTypeToCodecName(format.sampleMimeType)

        when (trackGroupInfo.type) {
            C.TRACK_TYPE_VIDEO -> {
                if (isSelected || videoStreamInfo == null) {
                    videoStreamInfo = VideoStreamInfo(
                        index = 0,
                        title = format.label?.takeIf { it.isNotBlank() },
                        codecName = simpleCodecName,
                        language = format.language,
                        disposition = format.selectionFlags,
                        bitRate = format.bitrate.toLong().takeIf { it > 0L } ?: 0L,
                        frameRate = format.frameRate.toDouble().takeIf { it > 0.0 } ?: 0.0,
                        frameWidth = format.width.takeIf { it > 0 } ?: 0,
                        frameHeight = format.height.takeIf { it > 0 } ?: 0,
                    )
                }
            }
            C.TRACK_TYPE_AUDIO -> {
                audioStreams.add(
                    AudioStreamInfo(
                        index = audioIndex++,
                        title = format.label?.takeIf { it.isNotBlank() },
                        codecName = simpleCodecName,
                        language = format.language,
                        disposition = format.selectionFlags,
                        bitRate = format.bitrate.toLong().takeIf { it > 0L } ?: 0L,
                        sampleFormat = null,
                        sampleRate = format.sampleRate.takeIf { it > 0 } ?: 0,
                        channels = format.channelCount.takeIf { it > 0 } ?: 0,
                        channelLayout = null,
                    ),
                )
            }
            C.TRACK_TYPE_TEXT -> {
                val isExternal = format.id?.contains("://") == true

                if (!isExternal) {
                    subtitleStreams.add(
                        SubtitleStreamInfo(
                            index = subIndex++,
                            title = format.label?.takeIf { it.isNotBlank() },
                            codecName = "Embedded",
                            language = format.language?.takeIf { it != "und" },
                            disposition = format.selectionFlags
                        )
                    )
                }
            }
        }
    }

    val resolvedDuration = this.duration.takeIf { it != C.TIME_UNSET }
    val mediaId = this.currentMediaItem?.mediaId ?: ""
    val decodedMediaId = try {
        java.net.URLDecoder.decode(mediaId, "UTF-8")
    } catch (e: Exception) {
        mediaId
    }

    val currentMediaName = decodedMediaId.substringAfterLast("/")

    val base = if (currentVideo?.nameWithExtension == currentMediaName) {
        currentVideo
    } else {
        Video(
            id = 0L,
            path = mediaId,
            parentPath = decodedMediaId.substringBeforeLast("/").takeIf { it.isNotEmpty() } ?: "Unknown",
            duration = resolvedDuration ?: 0L,
            formattedDuration = resolvedDuration?.let { Utils.formatDurationMillis(it) } ?: "",
            uriString = mediaId,
            nameWithExtension = currentMediaName.takeIf { it.isNotEmpty() } ?: "Unknown Streaming",
            width = 0,
            height = 0,
            size = 0L,
        )
    }

    val finalDuration = resolvedDuration ?: base.duration

    return base.copy(
        duration = finalDuration ,
        formattedDuration = if (finalDuration > 0L) Utils.formatDurationMillis(finalDuration) else base.formattedDuration,
        videoStream = videoStreamInfo ?: base.videoStream,
        audioStreams = audioStreams.ifEmpty { base.audioStreams },
        subtitleStreams = subtitleStreams.ifEmpty { base.subtitleStreams },
    )
}

fun getMimeTypeToCodecName(mimeType: String?): String {
    return when (mimeType) {
        // Video
        "video/3gpp" -> "H.263"
        "video/avc" -> "H.264"
        "video/hevc" -> "H.265"
        "video/av01" -> "AV1"
        "video/x-vnd.on2.vp9" -> "VP9"
        "video/x-vnd.on2.vp8" -> "VP8"
        "video/mp4v-es" -> "MPEG-4"
        "video/mpeg2" -> "MPEG-2"
        // Audio
        "audio/mp4a-latm" -> "AAC"
        "audio/ac3" -> "AC-3"
        "audio/eac3" -> "E-AC-3"
        "audio/opus" -> "Opus"
        "audio/vorbis" -> "Vorbis"
        "audio/mpeg" -> "MP3"
        "audio/flac" -> "FLAC"
        "audio/alac" -> "ALAC"
        "audio/raw" -> "PCM"
        "audio/vnd.dts" -> "DTS"
        "audio/vnd.dts.hd" -> "DTS-HD"
        "audio/true-hd" -> "TrueHD"
        else -> mimeType?.substringAfter("/")?.uppercase() ?: "UNKNOWN"
    }
}
