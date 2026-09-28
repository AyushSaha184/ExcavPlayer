package com.excavplayer.ui.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.excavplayer.ui.theme.BackgroundDark
import com.excavplayer.ui.theme.CyanAccent
import com.excavplayer.ui.theme.DialogShape
import com.excavplayer.ui.theme.PillShape
import com.excavplayer.ui.theme.SurfaceBorder
import com.excavplayer.ui.theme.SurfaceDarkElevated
import com.excavplayer.ui.theme.TextPrimary
import com.excavplayer.ui.theme.TextSecondary

@Composable
fun CreatePlaylistDialog(
    initialTitle: String = "",
    titleLabel: String = "New Playlist",
    confirmButtonLabel: String = "Create",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(DialogShape)
                .background(SurfaceDarkElevated)
                .border(1.dp, SurfaceBorder, DialogShape)
                .padding(24.dp)
        ) {
            Text(
                text = titleLabel,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Playlist Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = CyanAccent,
                    unfocusedBorderColor = SurfaceBorder,
                    focusedLabelColor = CyanAccent,
                    unfocusedLabelColor = TextSecondary,
                    cursorColor = CyanAccent
                )
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary)
                ) {
                    Text("Cancel", fontWeight = FontWeight.Medium)
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = {
                        if (title.isNotBlank()) {
                            onConfirm(title.trim())
                            onDismiss()
                        }
                    },
                    enabled = title.isNotBlank(),
                    shape = PillShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanAccent,
                        contentColor = BackgroundDark,
                        disabledContainerColor = SurfaceBorder,
                        disabledContentColor = TextSecondary
                    )
                ) {
                    Text(confirmButtonLabel, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
