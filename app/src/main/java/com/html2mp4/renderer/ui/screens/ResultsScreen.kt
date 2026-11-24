package com.html2mp4.renderer.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.html2mp4.renderer.model.RenderedVideoItem
import com.html2mp4.renderer.model.VideoDetailInfo
import com.html2mp4.renderer.ui.i18n.AppStrings
import java.util.Locale

@Composable
fun ResultsScreen(
    strings: AppStrings,
    isWideScreen: Boolean,
    videos: List<RenderedVideoItem>,
    thumbnails: Map<Uri, Bitmap?>,
    onRefresh: () -> Unit,
    onPlayVideo: (RenderedVideoItem) -> Unit,
    onEditVideo: (RenderedVideoItem) -> Unit,
    onShowVideoInfo: (RenderedVideoItem) -> Unit,
    onShareVideo: (RenderedVideoItem) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = strings.resultsCountFormat(videos.size),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            IconButton(onClick = onRefresh) {
                Icon(Icons.Rounded.Refresh, contentDescription = strings.refreshListCd)
            }
        }

        if (videos.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Movie,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = strings.noVideosTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = strings.noVideosSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (isWideScreen) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 320.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                gridItems(videos, key = { it.uri.toString() }) { item ->
                    RenderedVideoCard(
                        strings = strings,
                        item = item,
                        thumbnail = thumbnails[item.uri],
                        onPlay = { onPlayVideo(item) },
                        onEdit = { onEditVideo(item) },
                        onInfo = { onShowVideoInfo(item) },
                        onShare = { onShareVideo(item) }
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(videos, key = { it.uri.toString() }) { item ->
                    RenderedVideoCard(
                        strings = strings,
                        item = item,
                        thumbnail = thumbnails[item.uri],
                        onPlay = { onPlayVideo(item) },
                        onEdit = { onEditVideo(item) },
                        onInfo = { onShowVideoInfo(item) },
                        onShare = { onShareVideo(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RenderedVideoCard(
    strings: AppStrings,
    item: RenderedVideoItem,
    thumbnail: Bitmap?,
    onPlay: () -> Unit,
    onEdit: () -> Unit,
    onInfo: () -> Unit,
    onShare: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(96.dp)
                    .height(60.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail.asImageBitmap(),
                        contentDescription = item.fileName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Rounded.Movie,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = item.fileName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val res = if (item.width > 0 && item.height > 0) "${item.width}×${item.height}" else "MP4"
                Text(
                    text = "${formatDuration(item.durationMs)} · $res · ${formatSize(item.fileSizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.width(8.dp))

            FilledTonalIconButton(onClick = onPlay) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = strings.playVideoCd)
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = strings.moreOptionsCd)
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(strings.menuEditVideo) },
                        leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onEdit()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(strings.menuVideoInfo) },
                        leadingIcon = { Icon(Icons.Rounded.Info, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onInfo()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(strings.menuShareVideo) },
                        leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onShare()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun VideoInfoDialog(
    strings: AppStrings,
    info: VideoDetailInfo?,
    isLoading: Boolean,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.videoInfoTitle, fontWeight = FontWeight.Bold) },
        text = {
            if (isLoading && info == null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Text(strings.loadingInfoLabel, style = MaterialTheme.typography.bodyMedium)
                }
            } else if (info != null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoRow(strings.infoFileName, info.fileName)
                    HorizontalDivider()
                    InfoRow(strings.infoResolution, "${info.width} × ${info.height} px")
                    InfoRow(strings.infoAspectRatio, info.aspectRatioLabel)
                    InfoRow(strings.infoOrientation, info.orientationLabel)
                    InfoRow(strings.infoDuration, formatDuration(info.durationMs))
                    InfoRow(strings.infoFrameRate, String.format(Locale.US, "%.1f FPS", info.fps))
                    val vMbps = if (info.videoBitrateBps > 0) {
                        String.format(Locale.US, "%.2f Mbps", info.videoBitrateBps / 1_000_000.0)
                    } else "Auto"
                    InfoRow(strings.infoVideoCodec, "${info.videoCodec} · $vMbps")
                    val audioSummary = if (info.audioSampleRateHz > 0) {
                        val aKbps = if (info.audioBitrateBps > 0) "${info.audioBitrateBps / 1000} kbps" else ""
                        "${info.audioCodec} · ${info.audioSampleRateHz} Hz · ${info.audioChannels}ch $aKbps".trim()
                    } else {
                        info.audioCodec
                    }
                    InfoRow(strings.infoAudioCodec, audioSummary)
                    InfoRow(strings.infoFileSize, formatSize(info.fileSizeBytes))
                    InfoRow(strings.infoUriLocation, info.uriString)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.closeButton)
            }
        }
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun formatDuration(ms: Long): String {
    val sec = (ms / 1000L).coerceAtLeast(0L)
    return String.format(Locale.US, "%02d:%02d", sec / 60L, sec % 60L)
}

private fun formatSize(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1.0) String.format(Locale.US, "%.1f MB", mb)
    else String.format(Locale.US, "%.0f KB", bytes / 1024.0)
}
