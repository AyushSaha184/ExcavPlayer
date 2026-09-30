package com.excavplayer.ui.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.excavplayer.R
import com.excavplayer.domain.model.Video
import com.excavplayer.ui.components.EmptyState
import com.excavplayer.ui.components.ListVideoRow
import com.excavplayer.ui.theme.ExcavPalette

@Composable
fun SearchScreen(
    query: String,
    results: List<Video>,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onPlay: (Video) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = ExcavPalette.Text
                )
            }
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.search), color = ExcavPalette.TextMuted) },
                singleLine = true,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = ExcavPalette.TextMuted
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = null,
                                tint = ExcavPalette.TextMuted
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ExcavPalette.Blue,
                    unfocusedBorderColor = ExcavPalette.Line,
                    focusedContainerColor = ExcavPalette.SurfaceCard,
                    unfocusedContainerColor = ExcavPalette.SurfaceCard,
                    focusedTextColor = ExcavPalette.Text,
                    unfocusedTextColor = ExcavPalette.Text
                )
            )
        }

        if (query.isNotBlank() && results.isEmpty()) {
            EmptyState(
                icon = Icons.Default.SearchOff,
                label = stringResource(R.string.empty_search)
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 90.dp)
            ) {
                items(results, key = { it.id }) { video ->
                    ListVideoRow(video = video, onClick = { onPlay(video) })
                }
            }
        }
    }
}
