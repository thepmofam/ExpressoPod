package com.rpmtech.expressopod

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun UpNextScreen(
    vm: PodcastViewModel,
    onClose: () -> Unit,
    onOpenNowPlaying: () -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current
    val subtleHaptic = rememberSubtleHaptic()

    val currentEpisode = vm.currentEpisode
    val currentPodcast = vm.currentPodcast
    val hasNowPlaying = currentEpisode != null && currentPodcast != null
    val archivedEpisodes by vm.archivedEpisodes.collectAsState()
    val playedEpisodes by vm.playedEpisodes.collectAsState()
    val queuedItems = remember(vm.upNextQueue, currentEpisode?.audioUrl, archivedEpisodes, playedEpisodes) {
        vm.upNextQueue
            .filterNot {
                (currentEpisode != null && it.episode.audioUrl == currentEpisode.audioUrl) ||
                    archivedEpisodes.contains(it.episode.audioUrl) ||
                    playedEpisodes.contains(it.episode.audioUrl)
            }
            .distinctBy { it.episode.audioUrl.ifBlank { "${it.episode.title}_${it.podcast.feedUrl}" } }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                AutoFitSingleLineText(
                    text = "Up Next Queue",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                if (queuedItems.isNotEmpty()) {
                    Text(
                        "Press and hold to rearrange",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = {
                subtleHaptic()
                onClose()
            }) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }

        if (!hasNowPlaying && queuedItems.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Your queue is empty.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            val listState = rememberLazyListState()
            val itemIndexOffset by rememberUpdatedState(if (hasNowPlaying) 2 else 0)
            val reorderableLazyListState = rememberReorderableLazyListState(listState) { from, to ->
                if (vm.reorderUpNextItemByKey(from.key, to.key)) {
                    subtleHaptic()
                } else {
                    val fromQueueIdx = from.index - itemIndexOffset
                    val toQueueIdx = to.index - itemIndexOffset
                    if (fromQueueIdx in vm.upNextQueue.indices && toQueueIdx in vm.upNextQueue.indices) {
                        subtleHaptic()
                        vm.reorderUpNextItem(fromQueueIdx, toQueueIdx)
                    }
                }
            }

            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (hasNowPlaying) {
                    item(key = "__now_playing_section__") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                AutoFitSingleLineText(
                                    text = if (vm.isPlaying) "NOW PLAYING" else "PAUSED",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            val progress = if (vm.duration > 0L) {
                                (vm.currentPosition.toFloat() / vm.duration.toFloat()).coerceIn(0f, 1f)
                            } else 0f

                            Surface(
                                shape = RoundedCornerShape(22.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                border = BorderStroke(
                                    1.5.dp,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(22.dp))
                                    .clickable {
                                        subtleHaptic()
                                        onOpenNowPlaying()
                                    }
                            ) {
                                Column {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.GraphicEq,
                                            contentDescription = "Now Playing",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(start = 4.dp, end = 12.dp)
                                        )
                                        AsyncImage(
                                            model = currentPodcast.artworkUrl,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(56.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                text = currentEpisode.title,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = currentPodcast.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        FilledTonalIconButton(
                                            onClick = {
                                                subtleHaptic()
                                                vm.togglePlayPause()
                                            },
                                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            )
                                        ) {
                                            Icon(
                                                imageVector = if (vm.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                contentDescription = if (vm.isPlaying) "Pause" else "Play"
                                            )
                                        }
                                        IconButton(onClick = {
                                            subtleHaptic()
                                            vm.stopOrSkipCurrentEpisode()
                                        }) {
                                            Icon(Icons.Default.Close, contentDescription = "Clear playing episode")
                                        }
                                    }

                                    if (vm.duration > 0L && progress > 0f) {
                                        LinearProgressIndicator(
                                            progress = { progress },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(3.dp),
                                            color = MaterialTheme.colorScheme.primary,
                                            trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item(key = "__queue_separator__") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp, bottom = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                AutoFitSingleLineText(
                                    text = if (queuedItems.isNotEmpty()) "NEXT IN QUEUE (${queuedItems.size})" else "NEXT IN QUEUE",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                                HorizontalDivider(
                                    modifier = Modifier.weight(1f),
                                    thickness = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                )
                            }
                            if (queuedItems.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No upcoming episodes in queue.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                items(
                    items = queuedItems,
                    key = { it.episode.audioUrl.ifBlank { "${it.episode.title}_${it.podcast.feedUrl}" } }
                ) { item ->
                    val itemKey = item.episode.audioUrl.ifBlank { "${item.episode.title}_${item.podcast.feedUrl}" }
                    ReorderableItem(reorderableLazyListState, key = itemKey) { isDragging ->
                        val elevation by animateDpAsState(if (isDragging) 16.dp else 0.dp, label = "dragElevation")
                        val scale by animateFloatAsState(if (isDragging) 1.04f else 1f, label = "dragScale")

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (isDragging) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            shadowElevation = elevation,
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                }
                                .longPressDraggableHandle(
                                    onDragStarted = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onDragStopped = {
                                        vm.saveUpNextQueue()
                                        vm.syncWidget()
                                    }
                                )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.DragHandle,
                                    contentDescription = "Press and hold to rearrange",
                                    tint = if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.padding(start = 4.dp, end = 12.dp)
                                )
                                AsyncImage(
                                    model = item.podcast.artworkUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.secondaryContainer)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        item.episode.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        item.podcast.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                IconButton(onClick = {
                                    subtleHaptic()
                                    vm.playEpisode(item.podcast, item.episode, autoPlay = true, fromQueue = true)
                                }) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Play Now")
                                }
                                IconButton(onClick = {
                                    subtleHaptic()
                                    vm.removeFromUpNext(item)
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
