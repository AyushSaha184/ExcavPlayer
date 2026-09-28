package com.excavplayer.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.excavplayer.domain.model.RepeatMode
import com.excavplayer.ui.components.ExcavSectionHeader
import com.excavplayer.ui.components.ExcavSwitch
import com.excavplayer.ui.components.ExcavTopBar
import com.excavplayer.ui.components.SettingsGroup
import com.excavplayer.ui.components.SettingsRow
import com.excavplayer.ui.player.sheets.PlaybackSpeedBottomSheet
import com.excavplayer.ui.theme.CyanAccent
import com.excavplayer.ui.theme.TextPrimary
import com.excavplayer.ui.theme.TextSecondary
import com.excavplayer.ui.theme.TextTertiary
import com.excavplayer.ui.theme.excavBackground

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settings.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val syncMessage by viewModel.syncMessage.collectAsState()

    var showSpeedDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(syncMessage) {
        syncMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSyncMessage()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .excavBackground()
    ) {
        ExcavTopBar(
            title = "Settings",
            onBackClick = onBackClick
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            // Playback Section
            ExcavSectionHeader(title = "Playback", fontSize = 15.sp)
            Spacer(modifier = Modifier.height(4.dp))
            SettingsGroup {
                SettingsRow(
                    icon = Icons.Default.WatchLater,
                    title = "Auto Resume",
                    subtitle = "Automatically resume from last position",
                    showDivider = true,
                    trailingContent = {
                        ExcavSwitch(
                            checked = settings.autoResume,
                            onCheckedChange = { viewModel.toggleAutoResume(it) }
                        )
                    }
                )

                SettingsRow(
                    icon = Icons.Default.Speed,
                    title = "Default Playback Speed",
                    showDivider = true,
                    onClick = { showSpeedDialog = true },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${settings.defaultPlaybackSpeed}x",
                                fontSize = 13.sp,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = TextTertiary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                )

                SettingsRow(
                    icon = Icons.Default.Repeat,
                    title = "Default Repeat Mode",
                    showDivider = true,
                    onClick = {
                        val next = when (settings.defaultRepeatMode) {
                            RepeatMode.OFF -> RepeatMode.REPEAT_ALL
                            RepeatMode.REPEAT_ALL -> RepeatMode.REPEAT_ONE
                            RepeatMode.REPEAT_ONE -> RepeatMode.OFF
                        }
                        viewModel.setDefaultRepeatMode(next)
                    },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = when (settings.defaultRepeatMode) {
                                    RepeatMode.OFF -> "Off"
                                    RepeatMode.REPEAT_ONE -> "Repeat One"
                                    RepeatMode.REPEAT_ALL -> "Repeat All"
                                },
                                fontSize = 13.sp,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = TextTertiary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                )

                SettingsRow(
                    icon = Icons.Default.Timer,
                    title = "Resume Threshold",
                    showDivider = false,
                    trailingContent = {
                        Text(
                            text = "10s",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Audio & Subtitles Section
            ExcavSectionHeader(title = "Audio & Subtitles", fontSize = 15.sp)
            Spacer(modifier = Modifier.height(4.dp))
            SettingsGroup {
                SettingsRow(
                    icon = Icons.Default.Language,
                    title = "Default Subtitle Language",
                    showDivider = true,
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = settings.preferredSubtitleLanguage ?: "English",
                                fontSize = 13.sp,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = TextTertiary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                )

                SettingsRow(
                    icon = Icons.Default.Subtitles,
                    title = "Auto Enable Subtitles",
                    subtitle = "When available in stream",
                    showDivider = true,
                    trailingContent = {
                        ExcavSwitch(
                            checked = settings.subtitlesEnabled,
                            onCheckedChange = { viewModel.toggleSubtitles(it) }
                        )
                    }
                )

                SettingsRow(
                    icon = Icons.Default.Language,
                    title = "Default Audio Track",
                    showDivider = true,
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = settings.preferredAudioLanguage ?: "System default",
                                fontSize = 13.sp,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = TextTertiary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                )

                SettingsRow(
                    icon = Icons.Default.FormatSize,
                    title = "Subtitle Text Size",
                    showDivider = false,
                    trailingContent = {
                        Text(
                            text = "Normal",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Gestures Section
            ExcavSectionHeader(title = "Gestures", fontSize = 15.sp)
            Spacer(modifier = Modifier.height(4.dp))
            SettingsGroup {
                SettingsRow(
                    icon = Icons.Default.BrightnessMedium,
                    title = "Brightness Gesture",
                    subtitle = "Vertical swipe on left half",
                    showDivider = true,
                    trailingContent = {
                        ExcavSwitch(
                            checked = settings.gestureControlsEnabled,
                            onCheckedChange = { viewModel.toggleGestureControls(it) }
                        )
                    }
                )

                SettingsRow(
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    title = "Volume Gesture",
                    subtitle = "Vertical swipe on right half",
                    showDivider = false,
                    trailingContent = {
                        ExcavSwitch(
                            checked = settings.gestureControlsEnabled,
                            onCheckedChange = { viewModel.toggleGestureControls(it) }
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Media Library Section
            ExcavSectionHeader(title = "Media Library", fontSize = 15.sp)
            Spacer(modifier = Modifier.height(4.dp))
            SettingsGroup {
                SettingsRow(
                    icon = Icons.Default.Refresh,
                    iconTint = CyanAccent,
                    title = "Rescan Media Library",
                    subtitle = "Index device storage for newly added or removed video files",
                    showDivider = false,
                    onClick = { viewModel.rescanMedia() },
                    trailingContent = {
                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = CyanAccent,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = TextTertiary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(28.dp))
        }

        SnackbarHost(hostState = snackbarHostState)
    }

    if (showSpeedDialog) {
        PlaybackSpeedBottomSheet(
            currentSpeed = settings.defaultPlaybackSpeed,
            onSpeedSelected = {
                viewModel.setPlaybackSpeed(it)
                showSpeedDialog = false
            },
            onDismiss = { showSpeedDialog = false }
        )
    }
}
