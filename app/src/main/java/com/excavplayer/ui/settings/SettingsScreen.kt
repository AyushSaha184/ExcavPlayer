package com.excavplayer.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.excavplayer.R
import com.excavplayer.domain.model.RepeatMode
import com.excavplayer.ui.ExcavViewModel
import com.excavplayer.ui.components.SleekRadioButton
import com.excavplayer.ui.components.SleekSwitch
import com.excavplayer.ui.theme.ExcavPalette
import com.excavplayer.ui.theme.ExcavShapes

private enum class SettingDialog {
    NONE, HARDWARE_ACCELERATION, SPEED, REPEAT, THRESHOLD, ORIENTATION, MEDIA_FIT, SUB_LANG, AUDIO_LANG
}

@Composable
fun SettingsScreen(
    vm: ExcavViewModel,
    onBack: (() -> Unit)? = null
) {
    val settings by vm.userSettings.collectAsState()
    var activeDialog by rememberSaveable { mutableStateOf(SettingDialog.NONE) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ExcavPalette.Ink)
            .statusBarsPadding()
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                        tint = ExcavPalette.Text
                    )
                }
                Spacer(Modifier.width(4.dp))
            } else {
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = stringResource(R.string.settings),
                color = ExcavPalette.Text,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Section 1: Playback
            SettingsSectionHeader(title = "Playback")
            SettingsCardContainer {
                SettingsSwitchRow(
                    icon = Icons.Default.PictureInPicture,
                    title = "Auto Resume",
                    subtitle = "Automatically resume from last position",
                    checked = settings.autoResume,
                    onCheckedChange = { vm.setAutoResume(it) }
                )
                SettingsDivider()
                SettingsSwitchRow(
                    icon = Icons.Default.SkipNext,
                    title = "Autoplay Next Video",
                    subtitle = "Automatically play next video in queue",
                    checked = settings.autoplayNextVideo,
                    onCheckedChange = { vm.setAutoplayNext(it) }
                )
                SettingsDivider()
                SettingsSwitchRow(
                    icon = Icons.Default.ScreenLockPortrait,
                    title = "Pause when Screen Turns Off",
                    subtitle = "Automatically stop playback when device screen locks",
                    checked = settings.stopOnScreenOff,
                    onCheckedChange = { vm.setStopOnScreenOff(it) }
                )
                SettingsDivider()
                SettingsNavRow(
                    icon = Icons.Default.Memory,
                    title = "Hardware Acceleration",
                    subtitle = settings.hardwareAccelerationMode,
                    onClick = { activeDialog = SettingDialog.HARDWARE_ACCELERATION }
                )
                SettingsDivider()
                SettingsSwitchRow(
                    icon = Icons.Default.Sync,
                    title = "Match Display Refresh Rate",
                    subtitle = "Seamless frame rate sync to eliminate 24fps motion judder on 60Hz/120Hz displays",
                    checked = settings.matchDisplayRefreshRate,
                    onCheckedChange = { vm.setMatchDisplayRefreshRate(it) }
                )
                SettingsDivider()
                SettingsNavRow(
                    icon = Icons.Default.Speed,
                    title = "Default Playback Speed",
                    subtitle = "${settings.defaultPlaybackSpeed}x",
                    onClick = { activeDialog = SettingDialog.SPEED }
                )
                SettingsDivider()
                SettingsNavRow(
                    icon = Icons.Default.Repeat,
                    title = "Default Repeat Mode",
                    subtitle = when (settings.defaultRepeatMode) {
                        RepeatMode.OFF -> "Off"
                        RepeatMode.REPEAT_ONE -> "Repeat One"
                        RepeatMode.REPEAT_ALL -> "Repeat All"
                    },
                    onClick = { activeDialog = SettingDialog.REPEAT }
                )
                SettingsDivider()
                SettingsNavRow(
                    icon = Icons.Default.Timer,
                    title = "Resume Threshold",
                    subtitle = when {
                        settings.resumeThresholdPercent >= 0.99f -> "5 seconds"
                        settings.resumeThresholdPercent >= 0.95f -> "10 seconds"
                        settings.resumeThresholdPercent >= 0.90f -> "30 seconds"
                        else -> "${(settings.resumeThresholdPercent * 100).toInt()}%"
                    },
                    onClick = { activeDialog = SettingDialog.THRESHOLD }
                )
                SettingsDivider()
                SettingsNavRow(
                    icon = Icons.Default.ScreenRotation,
                    title = "Default Screen Orientation",
                    subtitle = settings.defaultScreenOrientation,
                    onClick = { activeDialog = SettingDialog.ORIENTATION }
                )
                SettingsDivider()
                SettingsNavRow(
                    icon = Icons.Default.AspectRatio,
                    title = "Default Media Fit",
                    subtitle = settings.defaultMediaFit,
                    onClick = { activeDialog = SettingDialog.MEDIA_FIT }
                )
                SettingsDivider()
                SettingsSwitchRow(
                    icon = Icons.Default.History,
                    title = "Continue Watching",
                    subtitle = "Show in-progress videos on home screen",
                    checked = settings.continueWatchingEnabled,
                    onCheckedChange = { vm.setContinueWatching(it) }
                )
            }

            Spacer(Modifier.height(20.dp))

            // Section 2: Audio & Media
            SettingsSectionHeader(title = "Audio & Media")
            SettingsCardContainer {
                SettingsSwitchRow(
                    icon = Icons.Default.Headphones,
                    title = "Headset Detection",
                    subtitle = "Auto pause when disconnected / resume when connected",
                    checked = settings.headsetDetectionEnabled,
                    onCheckedChange = { vm.setHeadsetDetection(it) }
                )
                SettingsDivider()
                SettingsSwitchRow(
                    icon = Icons.Default.Timelapse,
                    title = "Auto Enable Subtitles",
                    subtitle = "When subtitles are available in media",
                    checked = settings.subtitlesEnabled,
                    onCheckedChange = { vm.setSubtitlesEnabled(it) }
                )
                SettingsDivider()
                SettingsNavRow(
                    icon = Icons.Default.Subtitles,
                    title = "Default Subtitle Language",
                    subtitle = settings.preferredSubtitleLanguage ?: "English",
                    onClick = { activeDialog = SettingDialog.SUB_LANG }
                )
                SettingsDivider()
                SettingsNavRow(
                    icon = Icons.Default.MusicNote,
                    title = "Default Audio Track",
                    subtitle = settings.preferredAudioLanguage ?: "System default",
                    onClick = { activeDialog = SettingDialog.AUDIO_LANG }
                )
                SettingsDivider()
                SettingsSwitchRow(
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    title = "Remember Volume",
                    subtitle = "Save volume level across sessions",
                    checked = settings.rememberVolume,
                    onCheckedChange = { vm.setRememberVolume(it) }
                )
            }

            Spacer(Modifier.height(20.dp))

            // Section 3: Gestures
            SettingsSectionHeader(title = "Gestures")
            SettingsCardContainer {
                SettingsSwitchRow(
                    icon = Icons.Default.Tune,
                    title = "Brightness Gesture",
                    subtitle = "Swipe left side of screen",
                    checked = settings.brightnessGestureEnabled,
                    onCheckedChange = { vm.setBrightnessGesture(it) }
                )
                SettingsDivider()
                SettingsSwitchRow(
                    icon = Icons.Default.ControlCamera,
                    title = "Volume Gesture",
                    subtitle = "Swipe right side of screen",
                    checked = settings.volumeGestureEnabled,
                    onCheckedChange = { vm.setVolumeGesture(it) }
                )
            }

            Spacer(Modifier.height(20.dp))

            // Section 4: System & Updates
            val context = LocalContext.current
            SettingsSectionHeader(title = "System & Updates")
            SettingsCardContainer {
                SettingsSwitchRow(
                    icon = Icons.Default.Sync,
                    title = "Auto Rescan on Startup",
                    subtitle = "Scan folders for new media on app launch",
                    checked = settings.autoRescanOnLaunch,
                    onCheckedChange = { vm.setAutoRescanOnLaunch(it) }
                )
                SettingsDivider()
                SettingsNavRow(
                    icon = Icons.Default.SystemUpdate,
                    title = "Check for Updates",
                    subtitle = "Current version: v${com.excavplayer.BuildConfig.VERSION_NAME}",
                    onClick = { vm.checkForUpdates(isManualCheck = true) }
                )
                SettingsDivider()
                SettingsNavRow(
                    icon = Icons.Default.Code,
                    title = "GitHub Repository",
                    subtitle = "View source code & issues",
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/AyushSaha184/ExcavPlayer"))
                        context.startActivity(intent)
                    }
                )
                SettingsDivider()
                SettingsNavRow(
                    icon = Icons.Default.Info,
                    title = "Releases & Changelog",
                    subtitle = "View version history & download APKs",
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/AyushSaha184/ExcavPlayer/releases"))
                        context.startActivity(intent)
                    }
                )
            }

            Spacer(Modifier.height(40.dp))
        }
    }

    // Dialogs
    when (activeDialog) {
        SettingDialog.HARDWARE_ACCELERATION -> {
            RadioChoiceDialog(
                title = "Hardware Acceleration",
                options = listOf("Automatic", "Full Acceleration", "Decoding Acceleration", "Disabled"),
                selected = settings.hardwareAccelerationMode,
                labelFor = { it },
                subtitleFor = {
                    when (it) {
                        "Automatic" -> "Recommended. Automatically selects best hardware acceleration with fallback"
                        "Full Acceleration" -> "Direct hardware decoding & GPU output for maximum battery savings"
                        "Decoding Acceleration" -> "Hardware accelerated video decoding with flexible frame processing"
                        "Disabled" -> "Pure software decoding using CPU for maximum compatibility"
                        else -> null
                    }
                },
                onSelect = {
                    vm.setHardwareAccelerationMode(it)
                    activeDialog = SettingDialog.NONE
                },
                onDismiss = { activeDialog = SettingDialog.NONE }
            )
        }
        SettingDialog.SPEED -> {
            RadioChoiceDialog(
                title = "Default Playback Speed",
                options = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f),
                selected = settings.defaultPlaybackSpeed,
                labelFor = { "${it}x" },
                onSelect = {
                    vm.setDefaultPlaybackSpeed(it)
                    activeDialog = SettingDialog.NONE
                },
                onDismiss = { activeDialog = SettingDialog.NONE }
            )
        }
        SettingDialog.REPEAT -> {
            RadioChoiceDialog(
                title = "Default Repeat Mode",
                options = listOf(RepeatMode.OFF, RepeatMode.REPEAT_ONE, RepeatMode.REPEAT_ALL),
                selected = settings.defaultRepeatMode,
                labelFor = {
                    when (it) {
                        RepeatMode.OFF -> "Off"
                        RepeatMode.REPEAT_ONE -> "Repeat One"
                        RepeatMode.REPEAT_ALL -> "Repeat All"
                    }
                },
                onSelect = {
                    vm.setDefaultRepeatMode(it)
                    activeDialog = SettingDialog.NONE
                },
                onDismiss = { activeDialog = SettingDialog.NONE }
            )
        }
        SettingDialog.THRESHOLD -> {
            RadioChoiceDialog(
                title = "Resume Threshold",
                options = listOf(0.99f, 0.95f, 0.90f, 0.80f),
                selected = settings.resumeThresholdPercent,
                labelFor = {
                    when {
                        it >= 0.99f -> "5 seconds (99%)"
                        it >= 0.95f -> "10 seconds (95%)"
                        it >= 0.90f -> "30 seconds (90%)"
                        else -> "1 minute (80%)"
                    }
                },
                onSelect = {
                    vm.setResumeThreshold(it)
                    activeDialog = SettingDialog.NONE
                },
                onDismiss = { activeDialog = SettingDialog.NONE }
            )
        }
        SettingDialog.ORIENTATION -> {
            RadioChoiceDialog(
                title = "Default Screen Orientation",
                options = listOf("Auto", "Landscape", "Portrait", "Sensor"),
                selected = settings.defaultScreenOrientation,
                labelFor = { it },
                onSelect = {
                    vm.setDefaultScreenOrientation(it)
                    activeDialog = SettingDialog.NONE
                },
                onDismiss = { activeDialog = SettingDialog.NONE }
            )
        }
        SettingDialog.MEDIA_FIT -> {
            RadioChoiceDialog(
                title = "Default Media Fit",
                options = listOf("Fit to Screen", "Stretch / Fill", "Crop / Zoom"),
                selected = settings.defaultMediaFit,
                labelFor = { it },
                onSelect = {
                    vm.setDefaultMediaFit(it)
                    activeDialog = SettingDialog.NONE
                },
                onDismiss = { activeDialog = SettingDialog.NONE }
            )
        }
        SettingDialog.SUB_LANG -> {
            RadioChoiceDialog(
                title = "Preferred Subtitle Language",
                options = listOf("English", "Spanish", "Japanese", "French", "German", "Korean", "None"),
                selected = settings.preferredSubtitleLanguage ?: "None",
                labelFor = { it },
                onSelect = {
                    vm.setPreferredSubtitleLanguage(if (it == "None") null else it)
                    activeDialog = SettingDialog.NONE
                },
                onDismiss = { activeDialog = SettingDialog.NONE }
            )
        }
        SettingDialog.AUDIO_LANG -> {
            RadioChoiceDialog(
                title = "Preferred Audio Language",
                options = listOf("System default", "English", "Japanese", "Spanish", "French", "German"),
                selected = settings.preferredAudioLanguage ?: "System default",
                labelFor = { it },
                onSelect = {
                    vm.setPreferredAudioLanguage(if (it == "System default") null else it)
                    activeDialog = SettingDialog.NONE
                },
                onDismiss = { activeDialog = SettingDialog.NONE }
            )
        }
        SettingDialog.NONE -> Unit
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        color = ExcavPalette.Blue,
        style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        ),
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
    )
}

@Composable
private fun SettingsCardContainer(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ExcavShapes.Card)
            .border(1.dp, ExcavPalette.Line, ExcavShapes.Card),
        color = ExcavPalette.SurfaceCard,
        content = { Column(content = content) }
    )
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        color = ExcavPalette.Line.copy(alpha = 0.5f),
        thickness = 0.5.dp,
        modifier = Modifier.padding(start = 58.dp)
    )
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, ExcavPalette.Line, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = ExcavPalette.Text,
                modifier = Modifier.size(19.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = ExcavPalette.Text,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp
                )
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = ExcavPalette.TextMuted,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp)
            )
        }
        Spacer(Modifier.width(8.dp))
        SleekSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun SettingsNavRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, ExcavPalette.Line, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = ExcavPalette.Text,
                modifier = Modifier.size(19.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = ExcavPalette.Text,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp
                )
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = ExcavPalette.TextMuted,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp)
            )
        }
    }
}

@Composable
private fun <T> RadioChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    labelFor: (T) -> String,
    subtitleFor: ((T) -> String?)? = null,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                color = ExcavPalette.Text,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                options.forEach { option ->
                    val isSelected = option == selected
                    val sub = subtitleFor?.invoke(option)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) ExcavPalette.Blue.copy(alpha = 0.12f) else Color.Transparent)
                            .clickable { onSelect(option) }
                            .padding(horizontal = 12.dp, vertical = if (sub != null) 10.dp else 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SleekRadioButton(
                            selected = isSelected,
                            onClick = { onSelect(option) }
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = labelFor(option),
                                color = if (isSelected) ExcavPalette.Blue else ExcavPalette.Text,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            )
                            if (sub != null) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = sub,
                                    color = ExcavPalette.TextMuted,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = ExcavPalette.TextMuted)
            }
        },
        containerColor = ExcavPalette.SurfaceCard,
        shape = ExcavShapes.Card
    )
}
