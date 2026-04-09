package dev.anilbeesetti.nextplayer.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class SubtitleEdgeType(val value: Int) {
    NONE(0),         // CaptionStyleCompat.EDGE_TYPE_NONE
    OUTLINE(1),      // CaptionStyleCompat.EDGE_TYPE_OUTLINE
    DROP_SHADOW(2),  // CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
    RAISED(3),       // CaptionStyleCompat.EDGE_TYPE_RAISED
    DEPRESSED(4);    // CaptionStyleCompat.EDGE_TYPE_DEPRESSED
}
