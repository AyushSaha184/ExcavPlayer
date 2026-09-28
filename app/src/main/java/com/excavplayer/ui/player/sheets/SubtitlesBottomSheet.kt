package com.excavplayer.ui.player.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.excavplayer.domain.model.SubtitleTrack
import com.excavplayer.ui.theme.BottomSheetShape
import com.excavplayer.ui.theme.CardShape
import com.excavplayer.ui.theme.CyanAccent
import com.excavplayer.ui.theme.SurfaceBorder
import com.excavplayer.ui.theme.SurfaceDark
import com.excavplayer.ui.theme.SurfaceDarkElevated
import com.excavplayer.ui.theme.TextPrimary
import com.excavplayer.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitlesBottomSheet(
    tracks: List<SubtitleTrack>,
    selectedTrackId: String?,
    subtitlesEnabled: Boolean,
    subtitleDelayMs: Long,
    onToggleSubtitlesEnabled: (Boolean) -> Unit,
    onTrackSelected: (String?) -> Unit,
    onAddExternalSubtitleClick: () -> Unit,
    onSubtitleDelayChanged: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var currentDelaySlider by remember(subtitleDelayMs) { mutableFloatStateOf(subtitleDelayMs.toFloat()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = BottomSheetShape,
        containerColor = SurfaceDarkElevated,
        tonalElevation = 8.dp,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF475569))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 6.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Subtitles",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = SurfaceBorder, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(12.dp))

            // Enable Subtitles Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Enable Subtitles",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )

                Switch(
                    checked = subtitlesEnabled,
                    onCheckedChange = { onToggleSubtitlesEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = CyanAccent,
                        checkedTrackColor = CyanAccent.copy(alpha = 0.3f),
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = SurfaceBorder
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Option: Off
            val isOffSelected = !subtitlesEnabled || selectedTrackId == null
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .clickable {
                        onTrackSelected(null)
                    }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = isOffSelected,
                    onClick = { onTrackSelected(null) },
                    colors = RadioButtonDefaults.colors(
                        selectedColor = CyanAccent,
                        unselectedColor = TextSecondary
                    )
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Off",
                    fontSize = 15.sp,
                    color = if (isOffSelected) CyanAccent else TextPrimary,
                    fontWeight = if (isOffSelected) FontWeight.Bold else FontWeight.Normal
                )
            }

            // Subtitle Tracks List
            tracks.forEach { track ->
                val isSelected = subtitlesEnabled && (track.id == selectedTrackId || (selectedTrackId == null && track.isSelected))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CircleShape)
                        .clickable {
                            onTrackSelected(track.id)
                        }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { onTrackSelected(track.id) },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = CyanAccent,
                            unselectedColor = TextSecondary
                        )
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    val label = track.label.ifBlank { track.language ?: "Subtitle" }
                    val typeLabel = if (track.isEmbedded) "(Embedded)" else "(External)"
                    Text(
                        text = "$label $typeLabel",
                        fontSize = 15.sp,
                        color = if (isSelected) CyanAccent else TextPrimary,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Add External Subtitle File Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CardShape)
                    .background(SurfaceDark)
                    .clickable { onAddExternalSubtitleClick() }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Add External Subtitle File",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = ".srt, .vtt, .ass",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }

                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = "Add Subtitle",
                    tint = CyanAccent,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Subtitle Delay Slider
            Text(
                text = "Subtitle Delay: ${currentDelaySlider.toInt()} ms",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )

            Slider(
                value = currentDelaySlider,
                onValueChange = {
                    currentDelaySlider = it
                },
                onValueChangeFinished = {
                    onSubtitleDelayChanged(currentDelaySlider.toLong())
                },
                valueRange = -5000f..5000f,
                steps = 19,
                colors = SliderDefaults.colors(
                    thumbColor = CyanAccent,
                    activeTrackColor = CyanAccent,
                    inactiveTrackColor = SurfaceBorder
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "-5000 ms", fontSize = 11.sp, color = TextSecondary)
                Text(text = "0 ms", fontSize = 11.sp, color = TextSecondary)
                Text(text = "5000 ms", fontSize = 11.sp, color = TextSecondary)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
