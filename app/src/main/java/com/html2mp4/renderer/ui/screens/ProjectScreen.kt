package com.html2mp4.renderer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MovieCreation
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.html2mp4.renderer.model.AudioBitrateOption
import com.html2mp4.renderer.model.AudioChannelOption
import com.html2mp4.renderer.model.AudioCodecOption
import com.html2mp4.renderer.model.AudioSampleRate
import com.html2mp4.renderer.model.FrameRateOption
import com.html2mp4.renderer.model.HtmlProjectMetadata
import com.html2mp4.renderer.model.RenderConfig
import com.html2mp4.renderer.model.RenderStatus
import com.html2mp4.renderer.model.ResolutionPreset
import com.html2mp4.renderer.model.VideoBitratePreset
import com.html2mp4.renderer.model.VideoCodecOption
import com.html2mp4.renderer.model.VideoOrientation
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ProjectScreen(
    metadata: HtmlProjectMetadata?,
    isProbingDuration: Boolean,
    config: RenderConfig,
    renderStatus: RenderStatus,
    onPickHtmlFile: () -> Unit,
    onPickAssetsFolder: () -> Unit,
    onPickOutputFolder: () -> Unit,
    onOrientationChange: (VideoOrientation) -> Unit,
    onResolutionChange: (ResolutionPreset) -> Unit,
    onCustomSizeChange: (Int, Int) -> Unit,
    onFrameRateChange: (FrameRateOption) -> Unit,
    onVideoCodecChange: (VideoCodecOption) -> Unit,
    onVideoBitratePresetChange: (VideoBitratePreset) -> Unit,
    onCustomBitrateChange: (Int) -> Unit,
    onAudioMutedChange: (Boolean) -> Unit,
    onAudioCodecChange: (AudioCodecOption) -> Unit,
    onAudioSampleRateChange: (AudioSampleRate) -> Unit,
    onAudioBitrateChange: (AudioBitrateOption) -> Unit,
    onAudioChannelsChange: (AudioChannelOption) -> Unit,
    onStartRender: () -> Unit,
    onCancelRender: () -> Unit,
    onDismissStatus: () -> Unit,
    onNavigateToResults: () -> Unit
) {
    val isRendering = renderStatus is RenderStatus.Preparing || renderStatus is RenderStatus.Rendering

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. HTML Source Card (Strict Metadata Only — No HTML preview, No Source Code Viewer)
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Sumber Dokumen HTML", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }

                if (metadata == null) {
                    Text(
                        text = "Pilih file .html melalui Storage Access Framework. Isi kode HTML tidak dimuat ke memori UI agar bebas OOM.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = metadata.fileName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val sizeText = formatBytes(metadata.fileSizeBytes)
                        val durText = if (isProbingDuration) {
                            "Mendeteksi durasi & orientasi..."
                        } else {
                            formatDurationMs(metadata.detectedDurationMs)
                        }
                        Text(
                            text = "Ukuran: $sizeText · Durasi: $durText",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!isProbingDuration && metadata.isDurationAutoDetected) {
                            val orientText = if (metadata.detectedOrientation == VideoOrientation.PORTRAIT) {
                                "Vertikal"
                            } else {
                                "Lanskap"
                            }
                            Text(
                                text = "Terdeteksi otomatis: ${metadata.detectedWidth}×${metadata.detectedHeight} ($orientText)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (metadata.assetsTreeUri != null) {
                            Text(
                                text = "Folder aset lokal terhubung",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = onPickHtmlFile,
                        enabled = !isRendering,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (metadata == null) "Pilih File HTML" else "Ganti HTML", maxLines = 1)
                    }
                    if (metadata != null) {
                        OutlinedButton(
                            onClick = onPickAssetsFolder,
                            enabled = !isRendering
                        ) {
                            Text("Aset Lokal", maxLines = 1)
                        }
                    }
                }
            }
        }

        // 2. Active Render Progress Card (with ETA & Cancel)
        when (renderStatus) {
            is RenderStatus.Preparing -> {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Menyiapkan Pipeline...", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(renderStatus.message, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            is RenderStatus.Rendering -> {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Merender Video (${renderStatus.progressPercent}%)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "ETA ${renderStatus.etaFormatted}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        LinearProgressIndicator(
                            progress = { renderStatus.progressPercent / 100f },
                            modifier = Modifier.fillMaxWidth().height(8.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = renderStatus.phaseLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            OutlinedButton(
                                onClick = onCancelRender,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(Icons.Rounded.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Batal")
                            }
                        }
                    }
                }
            }
            is RenderStatus.Completed -> {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Render Selesai", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(renderStatus.fileName, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        TextButton(onClick = {
                            onDismissStatus()
                            onNavigateToResults()
                        }) {
                            Text("Lihat Hasil")
                        }
                    }
                }
            }
            is RenderStatus.Failed -> {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Gagal: ${renderStatus.errorMessage}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onDismissStatus) { Text("Tutup") }
                    }
                }
            }
            is RenderStatus.Cancelled -> {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(renderStatus.reason, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = onDismissStatus) { Text("OK") }
                    }
                }
            }
            RenderStatus.Idle -> Unit
        }

        // 3. Video Parameters Card
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Videocam, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Pengaturan Video (Exynos MFC)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        text = "${config.resolvedWidth}×${config.resolvedHeight}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                M3Dropdown(
                    label = "Orientasi Video",
                    selectedText = config.orientation.label,
                    options = VideoOrientation.entries,
                    optionLabel = { it.label },
                    enabled = !isRendering,
                    onSelect = onOrientationChange
                )

                M3Dropdown(
                    label = "Resolusi",
                    selectedText = if (config.resolutionPreset == ResolutionPreset.CUSTOM) {
                        "Custom (${config.resolvedWidth}×${config.resolvedHeight})"
                    } else if (config.orientation == VideoOrientation.PORTRAIT) {
                        "${config.resolutionPreset.label} (${config.resolutionPreset.shortSide}×${config.resolutionPreset.longSide})"
                    } else {
                        "${config.resolutionPreset.label} (${config.resolutionPreset.longSide}×${config.resolutionPreset.shortSide})"
                    },
                    options = ResolutionPreset.entries,
                    optionLabel = { preset ->
                        when {
                            preset == ResolutionPreset.CUSTOM -> "Custom (Atur W × H)"
                            config.orientation == VideoOrientation.PORTRAIT ->
                                "${preset.label} (${preset.shortSide}×${preset.longSide})"
                            else ->
                                "${preset.label} (${preset.longSide}×${preset.shortSide})"
                        }
                    },
                    enabled = !isRendering,
                    onSelect = onResolutionChange
                )

                if (config.resolutionPreset == ResolutionPreset.CUSTOM) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = config.customWidth.toString(),
                            onValueChange = { v -> v.toIntOrNull()?.let { onCustomSizeChange(it, config.customHeight) } },
                            label = { Text("Lebar (px)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            enabled = !isRendering,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = config.customHeight.toString(),
                            onValueChange = { v -> v.toIntOrNull()?.let { onCustomSizeChange(config.customWidth, it) } },
                            label = { Text("Tinggi (px)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            enabled = !isRendering,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                M3Dropdown(
                    label = "Frame Rate",
                    selectedText = config.frameRate.label,
                    options = FrameRateOption.entries,
                    optionLabel = { it.label },
                    enabled = !isRendering,
                    onSelect = onFrameRateChange
                )

                M3Dropdown(
                    label = "Codec Video",
                    selectedText = config.videoCodec.label,
                    options = VideoCodecOption.entries,
                    optionLabel = { it.label },
                    enabled = !isRendering,
                    onSelect = onVideoCodecChange
                )

                M3Dropdown(
                    label = "Bitrate Video",
                    selectedText = config.bitratePreset.label,
                    options = VideoBitratePreset.entries,
                    optionLabel = { it.label },
                    enabled = !isRendering,
                    onSelect = onVideoBitratePresetChange
                )

                if (config.bitratePreset == VideoBitratePreset.CUSTOM) {
                    Column {
                        Text(
                            text = "Target Bitrate: ${config.customBitrateKbps} Kbps",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                        Slider(
                            value = config.customBitrateKbps.toFloat(),
                            onValueChange = { onCustomBitrateChange(it.roundToInt()) },
                            valueRange = 1000f..20000f,
                            steps = 18,
                            enabled = !isRendering
                        )
                    }
                }
            }
        }

        // 4. Audio Parameters Card
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.AudioFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Pengaturan Audio", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Tanpa Audio", style = MaterialTheme.typography.bodySmall)
                        Switch(
                            checked = config.isAudioMuted,
                            onCheckedChange = onAudioMutedChange,
                            enabled = !isRendering
                        )
                    }
                }

                if (!config.isAudioMuted) {
                    M3Dropdown(
                        label = "Codec Audio",
                        selectedText = config.audioCodec.label,
                        options = AudioCodecOption.entries,
                        optionLabel = { it.label },
                        enabled = !isRendering,
                        onSelect = onAudioCodecChange
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            M3Dropdown(
                                label = "Sample Rate",
                                selectedText = config.audioSampleRate.label,
                                options = AudioSampleRate.entries,
                                optionLabel = { it.label },
                                enabled = !isRendering,
                                onSelect = onAudioSampleRateChange
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            M3Dropdown(
                                label = "Channel",
                                selectedText = config.audioChannels.label,
                                options = AudioChannelOption.entries,
                                optionLabel = { it.label },
                                enabled = !isRendering,
                                onSelect = onAudioChannelsChange
                            )
                        }
                    }

                    M3Dropdown(
                        label = "Bitrate Audio",
                        selectedText = config.audioBitrate.label,
                        options = AudioBitrateOption.entries,
                        optionLabel = { it.label },
                        enabled = !isRendering,
                        onSelect = onAudioBitrateChange
                    )
                }
            }
        }

        // 5. Output Folder Card
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Lokasi Output MP4", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = config.outputFolderDisplay,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OutlinedButton(onClick = onPickOutputFolder, enabled = !isRendering) {
                    Text("Ubah Folder")
                }
            }
        }

        HorizontalDivider()

        // 6. Primary Render CTA
        Button(
            onClick = onStartRender,
            enabled = metadata != null && !isRendering,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Icon(
                imageVector = if (isRendering) Icons.Rounded.MovieCreation else Icons.Rounded.PlayArrow,
                contentDescription = null
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (isRendering) "Sedang Merender..." else "Mulai Render",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(12.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> M3Dropdown(
    label: String,
    selectedText: String,
    options: List<T>,
    optionLabel: (T) -> String,
    enabled: Boolean,
    onSelect: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedText,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            singleLine = true,
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { item ->
                DropdownMenuItem(
                    text = { Text(optionLabel(item)) },
                    onClick = {
                        onSelect(item)
                        expanded = false
                    }
                )
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1.0) String.format(Locale.US, "%.2f MB", mb)
    else String.format(Locale.US, "%.1f KB", kb)
}

private fun formatDurationMs(ms: Long): String {
    val sec = (ms / 1000L).coerceAtLeast(1L)
    val m = sec / 60L
    val s = sec % 60L
    return String.format(Locale.US, "%02d:%02d", m, s)
}
