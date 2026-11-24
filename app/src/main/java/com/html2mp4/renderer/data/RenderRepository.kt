package com.html2mp4.renderer.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.html2mp4.renderer.model.HtmlProjectMetadata
import com.html2mp4.renderer.model.RenderedVideoItem
import com.html2mp4.renderer.model.VideoDetailInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.util.Locale

class RenderRepository(private val context: Context) {

    suspend fun readHtmlMetadata(uri: Uri, assetsTreeUri: Uri?): HtmlProjectMetadata =
        withContext(Dispatchers.IO) {
            var name = "project.html"
            var size = 0L

            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) name = cursor.getString(nameIdx) ?: name
                    if (sizeIdx >= 0) size = cursor.getLong(sizeIdx)
                }
            }

            HtmlProjectMetadata(
                uri = uri,
                fileName = name,
                fileSizeBytes = size,
                detectedDurationMs = 10_000L,
                assetsTreeUri = assetsTreeUri,
                isDurationAutoDetected = false
            )
        }

    suspend fun saveTempMp4ToOutput(
        tempFile: File,
        fileName: String,
        outputTreeUri: Uri?
    ): Uri = withContext(Dispatchers.IO) {
        if (outputTreeUri != null) {
            val tree = DocumentFile.fromTreeUri(context, outputTreeUri)
            val doc = tree?.createFile("video/mp4", fileName)
            if (doc != null) {
                context.contentResolver.openOutputStream(doc.uri)?.use { out ->
                    FileInputStream(tempFile).use { input ->
                        input.copyTo(out, bufferSize = 64 * 1024)
                    }
                }
                return@withContext doc.uri
            }
        }

        // Default Android 15 Scoped Storage path: /Movies/HTML2MP4/
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/HTML2MP4")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }

        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val itemUri = context.contentResolver.insert(collection, values)
            ?: error("Gagal membuat entri MediaStore MP4.")

        context.contentResolver.openOutputStream(itemUri)?.use { out ->
            FileInputStream(tempFile).use { input ->
                input.copyTo(out, bufferSize = 64 * 1024)
            }
        }

        values.clear()
        values.put(MediaStore.Video.Media.IS_PENDING, 0)
        context.contentResolver.update(itemUri, values, null, null)
        itemUri
    }

    suspend fun loadRenderedVideos(outputTreeUri: Uri?): List<RenderedVideoItem> =
        withContext(Dispatchers.IO) {
            val results = mutableListOf<RenderedVideoItem>()

            // 1. Scan default /Movies/HTML2MP4/ in MediaStore
            val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.WIDTH,
                MediaStore.Video.Media.HEIGHT,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DATE_ADDED
            )
            val selection = "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?"
            val selectionArgs = arrayOf("%HTML2MP4%")

            context.contentResolver.query(
                collection,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val wCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val hCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    results += RenderedVideoItem(
                        uri = ContentUris.withAppendedId(collection, id),
                        fileName = cursor.getString(nameCol) ?: "output.mp4",
                        durationMs = cursor.getLong(durCol),
                        width = cursor.getInt(wCol),
                        height = cursor.getInt(hCol),
                        fileSizeBytes = cursor.getLong(sizeCol),
                        dateAddedSec = cursor.getLong(dateCol)
                    )
                }
            }

            // 2. Also include custom SAF DocumentTree MP4 files if user selected a custom folder
            if (outputTreeUri != null) {
                val tree = DocumentFile.fromTreeUri(context, outputTreeUri)
                tree?.listFiles()
                    ?.filter { it.isFile && (it.name?.endsWith(".mp4", ignoreCase = true) == true) }
                    ?.forEach { doc ->
                        if (results.none { it.uri == doc.uri }) {
                            val meta = extractMetadataFromUri(doc.uri)
                            results += RenderedVideoItem(
                                uri = doc.uri,
                                fileName = doc.name ?: "video.mp4",
                                durationMs = meta.first,
                                width = meta.second,
                                height = meta.third,
                                fileSizeBytes = doc.length(),
                                dateAddedSec = doc.lastModified() / 1000L
                            )
                        }
                    }
            }

            results.sortedByDescending { it.dateAddedSec }
        }

    suspend fun probeDetailedVideoInfo(item: RenderedVideoItem): VideoDetailInfo =
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            val extractor = MediaExtractor()
            var w = item.width
            var h = item.height
            var dur = item.durationMs
            var fps = 30f
            var vCodec = "H.264 / AVC"
            var vBitrate = 0L
            var aCodec = "Tidak ada / Muted"
            var aRate = 0
            var aCh = 0
            var aBitrate = 0L

            try {
                retriever.setDataSource(context, item.uri)
                w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: w
                h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: h
                dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: dur
                val br = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() ?: 0L
                if (br > 0) vBitrate = br
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    val capRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull()
                    if (capRate != null && capRate > 0f) fps = capRate
                }
            } catch (_: Exception) {
            } finally {
                runCatching { retriever.release() }
            }

            try {
                extractor.setDataSource(context, item.uri, null)
                for (i in 0 until extractor.trackCount) {
                    val fmt = extractor.getTrackFormat(i)
                    val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
                    if (mime.startsWith("video/")) {
                        vCodec = when {
                            mime.contains("hevc", true) -> "H.265 / HEVC"
                            mime.contains("avc", true) -> "H.264 / AVC"
                            mime.contains("vp9", true) -> "VP9"
                            mime.contains("av01", true) -> "AV1"
                            else -> mime
                        }
                        if (fmt.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                            fps = fmt.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat().coerceAtLeast(1f)
                        }
                        if (fmt.containsKey(MediaFormat.KEY_BIT_RATE)) {
                            vBitrate = fmt.getInteger(MediaFormat.KEY_BIT_RATE).toLong()
                        }
                        if (w <= 0 && fmt.containsKey(MediaFormat.KEY_WIDTH)) w = fmt.getInteger(MediaFormat.KEY_WIDTH)
                        if (h <= 0 && fmt.containsKey(MediaFormat.KEY_HEIGHT)) h = fmt.getInteger(MediaFormat.KEY_HEIGHT)
                    } else if (mime.startsWith("audio/")) {
                        aCodec = when {
                            mime.contains("mp4a", true) -> "AAC-LC"
                            mime.contains("mpeg", true) -> "MP3"
                            mime.contains("opus", true) -> "Opus"
                            else -> mime
                        }
                        if (fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            aRate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        }
                        if (fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            aCh = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        }
                        if (fmt.containsKey(MediaFormat.KEY_BIT_RATE)) {
                            aBitrate = fmt.getInteger(MediaFormat.KEY_BIT_RATE).toLong()
                        }
                    }
                }
            } catch (_: Exception) {
            } finally {
                runCatching { extractor.release() }
            }

            fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
            val ratioLabel = if (w > 0 && h > 0) {
                val g = gcd(w, h)
                "${w / g}:${h / g}"
            } else {
                "-"
            }
            val orientLabel = if (h > w) "Vertikal (Portrait)" else "Lanskap (Landscape)"

            VideoDetailInfo(
                fileName = item.fileName,
                uriString = item.uri.toString(),
                width = w,
                height = h,
                aspectRatioLabel = ratioLabel,
                orientationLabel = orientLabel,
                durationMs = dur,
                fps = fps,
                videoCodec = vCodec,
                videoBitrateBps = vBitrate,
                audioCodec = aCodec,
                audioSampleRateHz = aRate,
                audioChannels = aCh,
                audioBitrateBps = aBitrate,
                fileSizeBytes = item.fileSizeBytes,
                dateAddedSec = item.dateAddedSec
            )
        }

    fun probeSimpleDuration(uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 5_000L
        } catch (_: Exception) {
            5_000L
        } finally {
            runCatching { retriever.release() }
        }
    }

    suspend fun extractVideoThumbnail(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            retriever.getFrameAtTime(500_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun extractMetadataFromUri(uri: Uri): Triple<Long, Int, Int> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            Triple(dur, w, h)
        } catch (_: Exception) {
            Triple(0L, 0, 0)
        } finally {
            runCatching { retriever.release() }
        }
    }
}
