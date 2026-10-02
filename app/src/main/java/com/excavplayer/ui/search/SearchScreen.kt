package com.excavplayer.ui.search

import androidx.compose.foundation.background
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.excavplayer.R
import com.excavplayer.domain.model.Video
import com.excavplayer.ui.components.EmptyState
import com.excavplayer.ui.components.ListVideoRow
import com.excavplayer.ui.theme.ExcavPalette

import androidx.compose.ui.graphics.Color
import com.excavplayer.ui.components.GlassmorphicBackButton
import com.excavplayer.ui.components.GlassmorphicItem
import com.excavplayer.ui.components.LocalHazeState
import com.excavplayer.ui.components.ProgressiveHeaderBlur
import com.excavplayer.ui.components.ProgressiveHeaderContainer
import dev.chrisbanes.haze.hazeSource

@Composable
fun SearchScreen(
    query: String,
    results: List<Video>,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onPlay: (Video) -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        val searchListState = androidx.compose.foundation.lazy.rememberLazyListState()
        val isScrolled by remember {
            derivedStateOf {
                searchListState.firstVisibleItemIndex > 0 || searchListState.firstVisibleItemScrollOffset > 10
            }
        }

        if (query.isNotBlank() && results.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(
                    icon = Icons.Default.SearchOff,
                    label = stringResource(R.string.empty_search)
                )
            }
        } else {
            val hazeState = LocalHazeState.current
            LazyColumn(
                state = searchListState,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 74.dp, bottom = 90.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState)
            ) {
                items(results, key = { it.id }) { video ->
                    ListVideoRow(video = video, onClick = { onPlay(video) })
                }
            }
        }

        // Floating Top Glass Search Bar
        ProgressiveHeaderContainer(
            modifier = Modifier.align(Alignment.TopCenter),
            isScrolled = isScrolled,
            fadeHeight = 20.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassmorphicBackButton(onClick = onBack)
                Spacer(Modifier.width(10.dp))
                GlassmorphicItem(
                    modifier = Modifier.weight(1f),
                    cornerRadius = 20,
                    blurRadius = 15
                ) {
                    TextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier.fillMaxWidth(),
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
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            focusedTextColor = ExcavPalette.Text,
                            unfocusedTextColor = ExcavPalette.Text
                        )
                    )
                }
            }
        }
    }
}
