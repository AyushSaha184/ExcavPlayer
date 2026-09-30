package com.excavplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.excavplayer.R
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
            Dialog(onDismissRequest = onDismiss) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .clip(ExcavShapes.Card)
                        .border(1.dp, ExcavPalette.BlueGlow, ExcavShapes.Card),
                    color = ExcavPalette.InkElevated,
                    shadowElevation = 24.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(22.dp)
                    ) {
                        // Header
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(ExcavPalette.Blue.copy(alpha = 0.18f))
                                    .border(1.dp, ExcavPalette.Blue.copy(alpha = 0.4f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SystemUpdate,
                                    contentDescription = null,
                                    tint = ExcavPalette.Blue,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "Update Available",
                                    color = ExcavPalette.Text,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 19.sp
                                    )
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "v${updateState.currentVersion} → v${updateState.newVersion}",
                                    color = ExcavPalette.Blue,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Release Notes
                        Text(
                            text = "What's New:",
                            color = ExcavPalette.TextSecondary,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(Modifier.height(6.dp))

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, ExcavPalette.Line, RoundedCornerShape(12.dp)),
                            color = ExcavPalette.SurfaceCard
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                                    .padding(14.dp)
                            ) {
                                val bodyText = updateState.release.body?.trim()?.ifEmpty { "Performance improvements and bug fixes." }
                                    ?: "Performance improvements and bug fixes."
                                Text(
                                    text = bodyText,
                                    color = ExcavPalette.Text,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 19.sp)
                                )
                            }
                        }

                        Spacer(Modifier.height(20.dp))

                        // Actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = onDismiss) {
                                Text(
                                    text = "Later",
                                    color = ExcavPalette.TextMuted,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            val context = androidx.compose.ui.platform.LocalContext.current
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
                                colors = ButtonDefaults.buttonColors(containerColor = ExcavPalette.Blue),
                                shape = ExcavShapes.Pill
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
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }
            }
        }

        is UpdateState.Downloading -> {
            Dialog(onDismissRequest = {}) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .clip(ExcavShapes.Card)
                        .border(1.dp, ExcavPalette.BlueGlow, ExcavShapes.Card),
                    color = ExcavPalette.InkElevated,
                    shadowElevation = 24.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(22.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = ExcavPalette.Blue,
                                strokeWidth = 2.5.dp
                            )
                            Spacer(Modifier.width(14.dp))
                            Text(
                                text = "Downloading Update...",
                                color = ExcavPalette.Text,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            )
                        }

                        Spacer(Modifier.height(18.dp))

                        // Linear progress bar
                        LinearProgressIndicator(
                            progress = { updateState.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(ExcavShapes.Pill),
                            color = ExcavPalette.Blue,
                            trackColor = ExcavPalette.Line
                        )

                        Spacer(Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${(updateState.progress * 100).toInt()}%",
                                color = ExcavPalette.Blue,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "${formatFileSize(updateState.bytesDownloaded)} / ${formatFileSize(updateState.totalBytes)}",
                                color = ExcavPalette.TextMuted,
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp)
                            )
                        }
                    }
                }
            }
        }

        else -> Unit
    }
}
