package com.excavplayer.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.excavplayer.ui.theme.ExcavPalette
import com.excavplayer.ui.theme.ExcavShapes
import com.excavplayer.update.GitHubAsset
import com.excavplayer.update.UpdateState
import java.io.File

@Composable
fun UpdateDialog(
    updateState: UpdateState,
    onDownload: (GitHubAsset) -> Unit,
    onInstall: (File) -> Unit,
    onDismiss: () -> Unit
) {
    when (updateState) {
        is UpdateState.UpdateAvailable -> {
            Dialog(
                onDismissRequest = onDismiss,
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                AnimatedDialogContainer(
                    modifier = Modifier
                        .fillMaxWidth(0.90f)
                        .wrapContentHeight()
                        .darkUltraThinBlur(
                            shape = RoundedCornerShape(26.dp),
                            backgroundColor = Color(0xF210131B),
                            strokeColor = Color.White.copy(alpha = 0.18f)
                        )
                        .padding(22.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Header with Icon, Title, and Pill Version Badge (matching UI monochrome glass palette)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SystemUpdate,
                                    contentDescription = null,
                                    tint = ExcavPalette.Text,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Update Available",
                                    color = ExcavPalette.Text,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                )
                                Spacer(Modifier.height(4.dp))
                                Surface(
                                    shape = ExcavShapes.Pill,
                                    color = Color.White.copy(alpha = 0.08f),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))
                                ) {
                                    Text(
                                        text = "v${updateState.currentVersion} → v${updateState.newVersion}",
                                        color = ExcavPalette.TextSecondary,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 11.5.sp
                                        ),
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 2.5.dp)
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(18.dp))

                        // Release Notes Section
                        Text(
                            text = "What's New",
                            color = ExcavPalette.TextSecondary,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp
                            )
                        )
                        Spacer(Modifier.height(8.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 180.dp)
                                .darkUltraThinBlur(
                                    shape = RoundedCornerShape(16.dp),
                                    backgroundColor = Color(0x66161920),
                                    strokeColor = Color.White.copy(alpha = 0.08f)
                                )
                                .padding(14.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                val bodyText = updateState.release.body?.trim()?.ifEmpty { "Performance improvements and bug fixes." }
                                    ?: "Performance improvements and bug fixes."
                                Text(
                                    text = bodyText,
                                    color = ExcavPalette.Text,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 13.sp,
                                        lineHeight = 19.sp
                                    )
                                )
                            }
                        }

                        Spacer(Modifier.height(20.dp))

                        // Perfectly Aligned Pill Action Buttons (Monochrome Glass & Titanium CTA)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Secondary "Later" Glassmorphic Pill Button
                            Surface(
                                modifier = Modifier
                                    .height(42.dp)
                                    .clip(ExcavShapes.Pill)
                                    .border(1.dp, Color.White.copy(alpha = 0.16f), ExcavShapes.Pill)
                                    .clickable(onClick = onDismiss),
                                color = Color.White.copy(alpha = 0.08f),
                                shape = ExcavShapes.Pill
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 18.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Later",
                                        color = ExcavPalette.TextSecondary,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.5.sp
                                        )
                                    )
                                }
                            }

                            Spacer(Modifier.width(10.dp))

                            val context = androidx.compose.ui.platform.LocalContext.current
                            // Primary "Update Now" Pill Button
                            Button(
                                onClick = {
                                    val asset = updateState.apkAsset
                                    if (asset != null) {
                                        onDownload(asset)
                                    } else {
                                        val url = updateState.release.htmlUrl.ifEmpty { "https://github.com/AyushSaha184/ExcavPlayer/releases" }
                                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
                                            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        context.startActivity(intent)
                                        onDismiss()
                                    }
                                },
                                modifier = Modifier.height(42.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ExcavPalette.Silver,
                                    contentColor = ExcavPalette.Ink
                                ),
                                shape = ExcavShapes.Pill,
                                contentPadding = PaddingValues(horizontal = 18.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = null,
                                    tint = ExcavPalette.Ink,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                val sizeLabel = updateState.apkAsset?.let { " (${formatFileSize(it.size)})" } ?: ""
                                Text(
                                    text = "Update Now$sizeLabel",
                                    color = ExcavPalette.Ink,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.5.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        is UpdateState.Downloading -> {
            Dialog(
                onDismissRequest = {},
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                AnimatedDialogContainer(
                    modifier = Modifier
                        .fillMaxWidth(0.88f)
                        .wrapContentHeight()
                        .darkUltraThinBlur(
                            shape = RoundedCornerShape(26.dp),
                            backgroundColor = Color(0xF210131B),
                            strokeColor = Color.White.copy(alpha = 0.18f)
                        )
                        .padding(22.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = ExcavPalette.Silver,
                                    strokeWidth = 2.5.dp
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "Downloading Update",
                                    color = ExcavPalette.Text,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp
                                    )
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "${formatFileSize(updateState.bytesDownloaded)} / ${formatFileSize(updateState.totalBytes)}",
                                    color = ExcavPalette.TextMuted,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
                                )
                            }
                        }

                        Spacer(Modifier.height(18.dp))

                        // Linear progress bar
                        LinearProgressIndicator(
                            progress = { updateState.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(ExcavShapes.Pill),
                            color = ExcavPalette.Silver,
                            trackColor = ExcavPalette.Line
                        )

                        Spacer(Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = ExcavShapes.Pill,
                                color = Color.White.copy(alpha = 0.08f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))
                            ) {
                                Text(
                                    text = "${(updateState.progress * 100).toInt()}%",
                                    color = ExcavPalette.Text,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    ),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }

                            Surface(
                                modifier = Modifier
                                    .clip(ExcavShapes.Pill)
                                    .border(1.dp, Color.White.copy(alpha = 0.14f), ExcavShapes.Pill)
                                    .clickable(onClick = onDismiss),
                                color = Color.White.copy(alpha = 0.06f),
                                shape = ExcavShapes.Pill
                            ) {
                                Text(
                                    text = "Cancel",
                                    color = ExcavPalette.TextMuted,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 12.sp
                                    ),
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        is UpdateState.ReadyToInstall -> {
            Dialog(
                onDismissRequest = onDismiss,
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                AnimatedDialogContainer(
                    modifier = Modifier
                        .fillMaxWidth(0.88f)
                        .wrapContentHeight()
                        .darkUltraThinBlur(
                            shape = RoundedCornerShape(26.dp),
                            backgroundColor = Color(0xF210131B),
                            strokeColor = Color.White.copy(alpha = 0.18f)
                        )
                        .padding(22.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.InstallMobile,
                                    contentDescription = null,
                                    tint = ExcavPalette.Text,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "Ready to Install",
                                    color = ExcavPalette.Text,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "Update downloaded successfully",
                                    color = ExcavPalette.TextMuted,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
                                )
                            }
                        }

                        Spacer(Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier
                                    .height(42.dp)
                                    .clip(ExcavShapes.Pill)
                                    .border(1.dp, Color.White.copy(alpha = 0.16f), ExcavShapes.Pill)
                                    .clickable(onClick = onDismiss),
                                color = Color.White.copy(alpha = 0.08f),
                                shape = ExcavShapes.Pill
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 18.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Later",
                                        color = ExcavPalette.TextSecondary,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.5.sp
                                        )
                                    )
                                }
                            }

                            Spacer(Modifier.width(10.dp))

                            Button(
                                onClick = { onInstall(updateState.apkFile) },
                                modifier = Modifier.height(42.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ExcavPalette.Silver,
                                    contentColor = ExcavPalette.Ink
                                ),
                                shape = ExcavShapes.Pill,
                                contentPadding = PaddingValues(horizontal = 18.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.InstallMobile,
                                    contentDescription = null,
                                    tint = ExcavPalette.Ink,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Install Now",
                                    color = ExcavPalette.Ink,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.5.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        else -> Unit
    }
}
