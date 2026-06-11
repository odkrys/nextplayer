package dev.anilbeesetti.nextplayer.feature.player.buttons

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.feature.player.extensions.formatted
import dev.anilbeesetti.nextplayer.feature.player.service.clearAbRepeat
import dev.anilbeesetti.nextplayer.feature.player.service.setAbRepeatA
import dev.anilbeesetti.nextplayer.feature.player.service.setAbRepeatB
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun AbRepeatPanelOverlay(
    player: Player,
    abRepeatA: Long,
    abRepeatB: Long,
    onStateChanged: (Long, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val controller = player as? MediaController ?: return
    val context = LocalContext.current

    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.width(IntrinsicSize.Max)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "A :",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 18.sp,
                            modifier = Modifier.padding(end = 6.dp),
                        )
                        Icon(
                            imageVector = NextIcons.RemoveCircleOutline,
                            contentDescription = "-",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier
                                .defaultMinSize(minWidth = 40.dp)
                                .clickable {
                                    if (abRepeatA != C.TIME_UNSET) {
                                        val newA = (abRepeatA - 1000L).coerceAtLeast(0L)
                                        controller.setAbRepeatA(newA)
                                        onStateChanged(newA, abRepeatB)
                                        player.seekTo(newA)
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(80.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.15f))
                            .clickable {
                                val currentPos = player.currentPosition
                                if (abRepeatB != C.TIME_UNSET && currentPos >= abRepeatB) {
                                    controller.setAbRepeatB(C.TIME_UNSET)
                                    controller.setAbRepeatA(currentPos)
                                    onStateChanged(currentPos, C.TIME_UNSET)
                                } else {
                                    controller.setAbRepeatA(currentPos)
                                    onStateChanged(currentPos, abRepeatB)
                                }
                            }
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (abRepeatA == C.TIME_UNSET) "Click" else abRepeatA.milliseconds.formatted(),
                            color = if (abRepeatA == C.TIME_UNSET) Color.Gray else Color.Yellow,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Icon(
                        imageVector = NextIcons.AddCircleOutline,
                        contentDescription = "+",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier
                            .defaultMinSize(minWidth = 40.dp)
                            .clickable {
                                if (abRepeatA != C.TIME_UNSET) {
                                    val maxA = if (abRepeatB != C.TIME_UNSET) abRepeatB - 1000L else player.duration
                                    val newA = (abRepeatA + 1000L).coerceAtMost(maxA)
                                    controller.setAbRepeatA(newA)
                                    onStateChanged(newA, abRepeatB)
                                    player.seekTo(newA)
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.2f), thickness = 1.dp)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "B :",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 18.sp,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        Icon(
                            imageVector = NextIcons.RemoveCircleOutline,
                            contentDescription = "-",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier
                                .defaultMinSize(minWidth = 40.dp)
                                .clickable {
                                    if (abRepeatB != C.TIME_UNSET) {
                                        val minB = if (abRepeatA != C.TIME_UNSET) abRepeatA + 1000L else 0L
                                        val newB = (abRepeatB - 1000L).coerceAtLeast(minB)
                                        controller.setAbRepeatB(newB)
                                        onStateChanged(abRepeatA, newB)
                                        player.seekTo(newB)
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(80.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.15f))
                            .clickable {
                                val currentPos = player.currentPosition
                                if (abRepeatA != C.TIME_UNSET && currentPos <= abRepeatA) {
                                    Toast.makeText(context, "Point B must be after Point A", Toast.LENGTH_SHORT).show()
                                } else {
                                    controller.setAbRepeatB(currentPos)
                                    onStateChanged(abRepeatA, currentPos)
                                }
                            }
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (abRepeatB == C.TIME_UNSET) "Click" else abRepeatB.milliseconds.formatted(),
                            color = if (abRepeatB == C.TIME_UNSET) Color.Gray else Color.Yellow,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Icon(
                        imageVector = NextIcons.AddCircleOutline,
                        contentDescription = "+",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier
                            .defaultMinSize(minWidth = 40.dp)
                            .clickable {
                                if (abRepeatB != C.TIME_UNSET) {
                                    val newB = (abRepeatB + 1000L).coerceAtMost(player.duration)
                                    controller.setAbRepeatB(newB)
                                    onStateChanged(abRepeatA, newB)
                                    player.seekTo(newB)
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }

            if (abRepeatA != C.TIME_UNSET || abRepeatB != C.TIME_UNSET) {
                Box(modifier = Modifier
                    .width(1.dp)
                    .height(48.dp)
                    .background(Color.White.copy(0.2f)))

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            controller.clearAbRepeat()
                            onStateChanged(C.TIME_UNSET, C.TIME_UNSET)
                        }
                        .padding(4.dp)
                ) {
                    Icon(
                        imageVector = NextIcons.Delete,
                        contentDescription = "Clear",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
