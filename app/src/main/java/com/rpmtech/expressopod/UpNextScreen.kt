package com.rpmtech.expressopod

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
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
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun UpNextScreen(vm: PodcastViewModel, onClose: () -> Unit) {
    val haptic = LocalHapticFeedback.current

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    "Up Next Queue",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                if (vm.upNextQueue.isNotEmpty()) {
                    Text(
                        "Press and hold to rearrange",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }
        
        if (vm.upNextQueue.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Your queue is empty.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            val listState = rememberLazyListState()
            val reorderableLazyListState = rememberReorderableLazyListState(listState) { from, to ->
                vm.reorderUpNextItem(from.index, to.index)
            }

            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    items = vm.upNextQueue,
                    key = { it.episode.audioUrl.ifBlank { "${it.episode.title}_${it.podcast.feedUrl}" } }
                ) { item ->
                    val itemKey = item.episode.audioUrl.ifBlank { "${item.episode.title}_${item.podcast.feedUrl}" }
                    ReorderableItem(reorderableLazyListState, key = itemKey) { isDragging ->
                        val elevation by animateDpAsState(if (isDragging) 16.dp else 0.dp)
                        val scale by animateFloatAsState(if (isDragging) 1.04f else 1f)

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
                                        maxLines = 2
                                    )
                                    Text(
                                        item.podcast.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                IconButton(onClick = {
                                    vm.removeFromUpNext(item)
                                    vm.playEpisode(item.podcast, item.episode, autoPlay = true, fromQueue = true)
                                }) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Play Now")
                                }
                                IconButton(onClick = { vm.removeFromUpNext(item) }) {
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
