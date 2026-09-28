package com.excavplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.excavplayer.domain.model.Video
import com.excavplayer.ui.theme.BackgroundDark
import com.excavplayer.ui.theme.CyanAccent
import com.excavplayer.ui.theme.DialogShape
import com.excavplayer.ui.theme.PillShape
import com.excavplayer.ui.theme.SurfaceBorder
import com.excavplayer.ui.theme.SurfaceDarkElevated
import com.excavplayer.ui.theme.TextPrimary
import com.excavplayer.ui.theme.TextSecondary

@Composable
fun VideoDetailsDialog(
    video: Video,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(DialogShape)
                .background(SurfaceDarkElevated)
                .border(1.dp, SurfaceBorder, DialogShape)
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Video Details",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = SurfaceBorder, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(12.dp))

            DetailItem(label = "Filename", value = video.displayName)
            DetailItem(label = "Duration", value = video.formattedDuration)
            DetailItem(label = "File Size", value = formatFileSize(video.sizeBytes))

            if (video.width > 0 && video.height > 0) {
                DetailItem(label = "Resolution", value = "${video.width} × ${video.height}")
            }

            if (video.frameRate != null && video.frameRate > 0) {
                DetailItem(label = "Frame Rate", value = String.format("%.2f fps", video.frameRate))
            }

            if (video.bitrate != null && video.bitrate > 0) {
                val kbps = video.bitrate / 1000
                DetailItem(label = "Bitrate", value = "$kbps kbps")
            }

            if (video.mimeType.isNotEmpty()) {
                DetailItem(label = "Format", value = video.mimeType)
            }

            if (video.folderName.isNotEmpty()) {
                DetailItem(label = "Folder", value = video.folderName)
            }

            if (video.folderPath.isNotEmpty()) {
                DetailItem(label = "Path", value = video.folderPath)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onDismiss,
                shape = PillShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyanAccent,
                    contentColor = BackgroundDark
                ),
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(text = "Close", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun DetailItem(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = TextSecondary,
            modifier = Modifier.width(90.dp)
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary,
            modifier = Modifier.weight(1f)
        )
    }
}
