package com.html2mp4.renderer.engine

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import androidx.documentfile.provider.DocumentFile
import java.io.ByteArrayInputStream

/**
 * Intercepts virtual local URLs (https://appassets.html2mp4.local/...) and streams
 * Storage Access Framework (SAF) file descriptors directly to Chromium's native C++ engine.
 * Never loads the HTML text into Kotlin Strings or Compose UI memory.
 */
class SafWebResourceHandler(
    private val context: Context,
    private val htmlUri: Uri,
    private val assetsTreeUri: Uri?
) {
    companion object {
        const val VIRTUAL_HOST = "appassets.html2mp4.local"
        const val ENTRY_URL = "https://$VIRTUAL_HOST/index.html"
    }

    fun handleRequest(request: WebResourceRequest): WebResourceResponse? {
        val url = request.url ?: return null
        if (url.host != VIRTUAL_HOST) return null

        val path = url.path?.trimStart('/') ?: "index.html"
        if (path == "index.html" || path.isEmpty()) {
            return streamUri(htmlUri, "text/html")
        }

        val treeUri = assetsTreeUri ?: return null
        val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return null
        val targetDoc = resolveRelativeDocument(rootDoc, path) ?: return null
        val mime = guessMimeType(path)
        return streamUri(targetDoc.uri, mime)
    }

    private fun streamUri(uri: Uri, fallbackMime: String): WebResourceResponse? {
        return try {
            val stream = context.contentResolver.openInputStream(uri) ?: return null
            val mime = context.contentResolver.getType(uri) ?: fallbackMime
            WebResourceResponse(
                mime,
                "UTF-8",
                200,
                "OK",
                mapOf("Access-Control-Allow-Origin" to "*"),
                stream
            )
        } catch (_: Exception) {
            WebResourceResponse(
                "text/plain",
                "UTF-8",
                404,
                "Not Found",
                emptyMap(),
                ByteArrayInputStream(ByteArray(0))
            )
        }
    }

    private fun resolveRelativeDocument(root: DocumentFile, relativePath: String): DocumentFile? {
        var current: DocumentFile = root
        val segments = relativePath.split('/').filter { it.isNotBlank() && it != "." }
        for (segment in segments) {
            if (segment == "..") continue
            current = current.findFile(segment) ?: return null
        }
        return current
    }

    private fun guessMimeType(path: String): String {
        val ext = path.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "html", "htm" -> "text/html"
            "js", "mjs" -> "application/javascript"
            "css" -> "text/css"
            "json" -> "application/json"
            "svg" -> "image/svg+xml"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "mp4" -> "video/mp4"
            "webm" -> "video/webm"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "woff2" -> "font/woff2"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
        }
    }
}
