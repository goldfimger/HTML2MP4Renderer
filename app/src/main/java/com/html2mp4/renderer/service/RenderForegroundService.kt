package com.html2mp4.renderer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.html2mp4.renderer.MainActivity
import com.html2mp4.renderer.R
import com.html2mp4.renderer.data.RenderRepository
import com.html2mp4.renderer.engine.WebViewOffscreenRenderer
import com.html2mp4.renderer.model.RenderConfig
import com.html2mp4.renderer.model.RenderStatus
import com.html2mp4.renderer.util.ChipsetOptimizer
import com.html2mp4.renderer.util.MemoryGuard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Runs offscreen WebView + Hardware MediaCodec rendering in a dedicated
 * Android 15 FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING service.
 */
class RenderForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var activeJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var memoryGuard: MemoryGuard
    private lateinit var repository: RenderRepository

    override fun onCreate() {
        super.onCreate()
        memoryGuard = MemoryGuard(applicationContext).also { it.register() }
        repository = RenderRepository(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL_RENDER -> {
                activeJob?.cancel(CancellationException("Dibatalkan oleh pengguna"))
                _status.value = RenderStatus.Cancelled("Proses rendering dibatalkan.")
                releaseWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_START_RENDER -> {
                val request = pendingRequest
                startAsMediaProcessingForeground()
                if (request == null) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return START_NOT_STICKY
                }
                launchRenderJob(request)
            }
        }
        return START_NOT_STICKY
    }

    private fun startAsMediaProcessingForeground() {
        val profile = ChipsetOptimizer.detect(applicationContext)
        val notification = buildProgressNotification(
            percent = 0,
            eta = "--:--",
            phase = "Menyiapkan Hardware MediaCodec (${profile.socFamily.displayName})..."
        )
        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        }
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                fgsType
            )
        } catch (_: Throwable) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        }
    }

    private fun launchRenderJob(request: RenderJobRequest) {
        activeJob?.cancel()
        acquireWakeLock()

        activeJob = serviceScope.launch {
            val tempFile = File(cacheDir, "render_tmp_${System.currentTimeMillis()}.mp4")
            try {
                _status.value = RenderStatus.Preparing("Memuat dokumen HTML secara aman...")

                val renderer = WebViewOffscreenRenderer(applicationContext, memoryGuard)
                renderer.renderToMp4(
                    htmlUri = request.htmlUri,
                    assetsTreeUri = request.assetsTreeUri,
                    config = request.config,
                    durationMs = request.durationMs,
                    tempOutputFile = tempFile
                ) { progress ->
                    _status.value = RenderStatus.Rendering(
                        progressPercent = progress.percent,
                        currentFrame = progress.currentFrame,
                        totalFrames = progress.totalFrames,
                        etaFormatted = progress.etaFormatted,
                        elapsedMs = progress.elapsedMs,
                        phaseLabel = progress.phaseLabel
                    )
                    updateNotification(
                        percent = progress.percent,
                        eta = progress.etaFormatted,
                        phase = progress.phaseLabel
                    )
                }

                val outName = buildOutputFileName(request.sourceFileName)
                val savedUri = repository.saveTempMp4ToOutput(
                    tempFile = tempFile,
                    fileName = outName,
                    outputTreeUri = request.config.outputTreeUri
                )

                _status.value = RenderStatus.Completed(
                    outputUri = savedUri,
                    fileName = outName,
                    durationMs = request.durationMs
                )
                postCompletionNotification(outName)
            } catch (_: CancellationException) {
                _status.value = RenderStatus.Cancelled("Proses rendering dibatalkan.")
            } catch (t: Throwable) {
                _status.value = RenderStatus.Failed(
                    t.localizedMessage ?: "Gagal melakukan rendering video MP4."
                )
            } finally {
                tempFile.delete()
                releaseWakeLock()
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf()
            }
        }
    }

    /**
     * Mandatory Android 15 (API 35) mediaProcessing timeout hook.
     * Prevents ForegroundServiceDidNotStopInTimeException when the 6-hour AOSP budget expires.
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        activeJob?.cancel(CancellationException("Batas waktu mediaProcessing Android 15 tercapai"))
        _status.value = RenderStatus.Failed("Batas waktu latar belakang Android 15 tercapai.")
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HTML2MP4::RenderWakeLock").apply {
            acquire(60 * 60 * 1000L) // 1 hour max safety cap
        }
    }

    private fun releaseWakeLock() {
        runCatching {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        }
        wakeLock = null
    }

    private fun buildOutputFileName(sourceName: String): String {
        val base = sourceName.substringBeforeLast('.').replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return "${base}_${System.currentTimeMillis() / 1000}.mp4"
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_render),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notif_channel_render_desc)
            setShowBadge(false)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    private fun updateNotification(percent: Int, eta: String, phase: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildProgressNotification(percent, eta, phase))
    }

    private fun buildProgressNotification(percent: Int, eta: String, phase: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val cancelIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, RenderForegroundService::class.java).apply { action = ACTION_CANCEL_RENDER },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Rendering HTML ke MP4 ($percent%)")
            .setContentText("ETA: $eta · $phase")
            .setProgress(100, percent, percent == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Batal", cancelIntent)
            .build()
    }

    private fun postCompletionNotification(fileName: String) {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val doneNotif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Render Selesai")
            .setContentText("$fileName siap diputar di tab Hasil")
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .build()
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID + 1, doneNotif)
    }

    override fun onDestroy() {
        memoryGuard.unregister()
        releaseWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    data class RenderJobRequest(
        val htmlUri: Uri,
        val assetsTreeUri: Uri?,
        val sourceFileName: String,
        val durationMs: Long,
        val config: RenderConfig
    )

    companion object {
        private const val CHANNEL_ID = "html2mp4_render_channel"
        private const val NOTIFICATION_ID = 4102
        const val ACTION_START_RENDER = "com.html2mp4.renderer.action.START_RENDER"
        const val ACTION_CANCEL_RENDER = "com.html2mp4.renderer.action.CANCEL_RENDER"

        private val _status = MutableStateFlow<RenderStatus>(RenderStatus.Idle)
        val status: StateFlow<RenderStatus> = _status.asStateFlow()

        @Volatile
        private var pendingRequest: RenderJobRequest? = null

        fun startRender(context: Context, request: RenderJobRequest) {
            pendingRequest = request
            val intent = Intent(context, RenderForegroundService::class.java).apply {
                action = ACTION_START_RENDER
            }
            context.startForegroundService(intent)
        }

        fun cancelRender(context: Context) {
            val intent = Intent(context, RenderForegroundService::class.java).apply {
                action = ACTION_CANCEL_RENDER
            }
            context.startService(intent)
        }

        fun resetStatus() {
            _status.value = RenderStatus.Idle
        }
    }
}
