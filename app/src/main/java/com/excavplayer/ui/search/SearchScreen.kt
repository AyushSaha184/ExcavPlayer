package com.excavplayer.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.excavplayer.R
import com.excavplayer.domain.model.Video
import com.excavplayer.ui.components.*
import com.excavplayer.ui.theme.ExcavPalette
import dev.chrisbanes.haze.hazeSource

@Composable
fun SearchScreen(
    query: String,
    results: List<Video>,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onPlay: (Video) -> Unit,
    onRenameVideo: (Video, String) -> Unit = { _, _ -> },
    onDeleteVideo: (Video) -> Unit = {}
) {
    var selectedVideoForMenu by remember { mutableStateOf<Video?>(null) }
    var renameVideoTarget by remember { mutableStateOf<Video?>(null) }
    var propertiesVideo by remember { mutableStateOf<Video?>(null) }

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        runCatching {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ExcavPalette.Ink)
    ) {
        val searchListState = androidx.compose.foundation.lazy.rememberLazyListState()
        val isScrolled by remember {
            derivedStateOf {
                searchListState.firstVisibleItemIndex > 0 || searchListState.firstVisibleItemScrollOffset > 10
            }
        }

        val hazeState = LocalHazeState.current
        val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        LazyColumn(
            state = searchListState,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = statusBarTop + 96.dp,
                bottom = 100.dp
            ),
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState)
        ) {
            if (results.isEmpty() && query.isNotBlank()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyState(
                            icon = Icons.Default.SearchOff,
                            label = "No videos matching \"$query\""
                        )
                    }
                }
            } else {
                items(results, key = { it.id }) { video ->
                    ListVideoRow(
                        video = video,
                        onClick = { onPlay(video) },
                        onMoreClick = { selectedVideoForMenu = video },
                        dropdownMenu = {
                            if (selectedVideoForMenu?.id == video.id) {
                                VideoOptionsMenu(
                                    expanded = true,
                                    video = video,
                                    onDismiss = { selectedVideoForMenu = null },
                                    onRename = { renameVideoTarget = video },
                                    onProperties = { propertiesVideo = video },
                                    onDelete = { onDeleteVideo(video) }
                                )
                            }
                        },
                        modifier = Modifier.animateItem()
                    )
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
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassmorphicBackButton(onClick = onBack)
                Spacer(Modifier.width(12.dp))
                val pillShape = remember { RoundedCornerShape(percent = 50) }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .lightweightLiquidGlass(shape = pillShape)
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = ExcavPalette.TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (query.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.search),
                                    color = ExcavPalette.TextMuted,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp)
                                )
                            }
                            BasicTextField(
                                value = query,
                                onValueChange = onQueryChange,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = ExcavPalette.Text,
                                    fontSize = 14.sp
                                ),
                                cursorBrush = SolidColor(ExcavPalette.Text),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
                            )
                        }
                        if (query.isNotEmpty()) {
                            Spacer(Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .clickable { onQueryChange("") },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = ExcavPalette.TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Modals & Dialogs
    propertiesVideo?.let { video ->
        VideoPropertiesDialog(
            video = video,
            onDismiss = { propertiesVideo = null }
        )
    }

    renameVideoTarget?.let { video ->
        RenameVideoDialog(
            video = video,
            onRename = { onRenameVideo(video, it) },
            onDismiss = { renameVideoTarget = null }
        )
    }
}
