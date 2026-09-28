package com.excavplayer.ui.player.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.excavplayer.domain.model.AudioTrack
import com.excavplayer.ui.theme.BottomSheetShape
import com.excavplayer.ui.theme.CyanAccent
import com.excavplayer.ui.theme.SurfaceBorder
import com.excavplayer.ui.theme.SurfaceDarkElevated
import com.excavplayer.ui.theme.TextPrimary
import com.excavplayer.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioTracksBottomSheet(
    tracks: List<AudioTrack>,
    selectedTrackId: String?,
    onTrackSelected: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                    .background(SurfaceBorder)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Audio Tracks",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
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
            Spacer(modifier = Modifier.height(8.dp))

            if (tracks.isEmpty()) {
                Text(
                    text = "No audio tracks detected",
                    fontSize = 14.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                tracks.forEach { track ->
                    val isSelected = track.id == selectedTrackId || (selectedTrackId == null && track.isSelected)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onTrackSelected(track.id)
                                onDismiss()
                            }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.label.ifBlank { track.language ?: "Audio Track" },
                                fontSize = 16.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) CyanAccent else TextPrimary
                            )

                            val details = buildString {
                                track.mimeType?.substringAfterLast("/")?.uppercase()?.let { append(it) }
                                if (track.channelCount > 0) {
                                    if (isNotEmpty()) append(" ")
                                    append(if (track.channelCount == 6) "5.1" else if (track.channelCount == 8) "7.1" else "${track.channelCount}.0")
                                }
                                if (track.isSelected) {
                                    if (isNotEmpty()) append(" · ")
                                    append("Default")
                                }
                            }

                            if (details.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = details,
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                        }

                        RadioButton(
                            selected = isSelected,
                            onClick = {
                                onTrackSelected(track.id)
                                onDismiss()
                            },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = CyanAccent,
                                unselectedColor = TextSecondary
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
