package com.excavplayer.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.excavplayer.domain.model.RepeatMode
import com.excavplayer.ui.components.ExcavTopBar
import com.excavplayer.ui.player.sheets.PlaybackSpeedBottomSheet
import com.excavplayer.ui.theme.BackgroundDark
import com.excavplayer.ui.theme.CyanAccent
import com.excavplayer.ui.theme.SurfaceBorder
import com.excavplayer.ui.theme.TextPrimary
import com.excavplayer.ui.theme.TextSecondary

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
            .background(BackgroundDark)
    ) {
        ExcavTopBar(
            title = "Settings",
            onBackClick = onBackClick
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Playback Section
            SectionHeader(title = "Playback")

            SettingSwitchRow(
                icon = Icons.Default.WatchLater,
                title = "Auto Resume",
                subtitle = "Automatically resume from last position",
                checked = settings.autoResume,
                onCheckedChange = { viewModel.toggleAutoResume(it) }
            )

            SettingClickableRow(
                icon = Icons.Default.Speed,
                title = "Default Playback Speed",
                value = "${settings.defaultPlaybackSpeed}x",
                onClick = { showSpeedDialog = true }
            )

            SettingClickableRow(
                icon = Icons.Default.Repeat,
                title = "Default Repeat Mode",
                value = when (settings.defaultRepeatMode) {
                    RepeatMode.OFF -> "Off"
                    RepeatMode.REPEAT_ONE -> "Repeat One"
                    RepeatMode.REPEAT_ALL -> "Repeat All"
                },
                onClick = {
                    val next = when (settings.defaultRepeatMode) {
                        RepeatMode.OFF -> RepeatMode.REPEAT_ALL
                        RepeatMode.REPEAT_ALL -> RepeatMode.REPEAT_ONE
                        RepeatMode.REPEAT_ONE -> RepeatMode.OFF
                    }
                    viewModel.setDefaultRepeatMode(next)
                }
            )

            SettingClickableRow(
                icon = Icons.Default.Timer,
                title = "Resume Threshold",
                value = "10 seconds",
                onClick = {}
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = SurfaceBorder, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(12.dp))

            // Audio & Subtitles Section
            SectionHeader(title = "Audio & Subtitles")

            SettingClickableRow(
                icon = Icons.Default.Language,
                title = "Default Subtitle Language",
                value = settings.preferredSubtitleLanguage ?: "English",
                onClick = {}
            )

            SettingSwitchRow(
                icon = Icons.Default.Subtitles,
                title = "Auto Enable Subtitles",
                subtitle = "When available",
                checked = settings.subtitlesEnabled,
                onCheckedChange = { viewModel.toggleSubtitles(it) }
            )

            SettingClickableRow(
                icon = Icons.Default.Language,
                title = "Default Audio Track",
                value = settings.preferredAudioLanguage ?: "System default",
                onClick = {}
            )

            SettingClickableRow(
                icon = Icons.Default.FormatSize,
                title = "Subtitle Text Size",
                value = "Normal",
                onClick = {}
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = SurfaceBorder, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(12.dp))

            // Gestures Section
            SectionHeader(title = "Gestures")

            SettingSwitchRow(
                icon = Icons.Default.BrightnessMedium,
                title = "Brightness Gesture",
                subtitle = "Swipe left side",
                checked = settings.gestureControlsEnabled,
                onCheckedChange = { viewModel.toggleGestureControls(it) }
            )

            SettingSwitchRow(
                icon = Icons.Default.VolumeUp,
                title = "Volume Gesture",
                subtitle = "Swipe right side",
                checked = settings.gestureControlsEnabled,
                onCheckedChange = { viewModel.toggleGestureControls(it) }
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = SurfaceBorder, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(12.dp))

            // Media Library Section
            SectionHeader(title = "Media Library")

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.rescanMedia() }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = CyanAccent,
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Rescan Media Library",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Scan device storage for new and removed videos",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }

                if (isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = CyanAccent,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
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

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        color = CyanAccent,
        modifier = Modifier.padding(top = 10.dp, bottom = 12.dp)
    )
}

@Composable
private fun SettingSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TextPrimary,
                modifier = Modifier.size(22.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = CyanAccent,
                checkedTrackColor = CyanAccent.copy(alpha = 0.3f),
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = SurfaceBorder
            )
        )
    }
}

@Composable
private fun SettingClickableRow(
    icon: ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TextPrimary,
            modifier = Modifier.size(22.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = value,
            fontSize = 14.sp,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.width(8.dp))

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(18.dp)
        )
    }
}
