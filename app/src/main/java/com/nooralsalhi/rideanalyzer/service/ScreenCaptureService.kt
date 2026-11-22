package com.nooralsalhi.rideanalyzer.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import com.nooralsalhi.rideanalyzer.MainActivity
import com.nooralsalhi.rideanalyzer.NoorApplication
import com.nooralsalhi.rideanalyzer.R
import com.nooralsalhi.rideanalyzer.ocr.TesseractOcr
import com.nooralsalhi.rideanalyzer.domain.RidePipeline
import com.nooralsalhi.rideanalyzer.domain.RideTextParser
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

/** User-consented live OCR fallback. Frames are processed in memory and never written to disk. */
class ScreenCaptureService : Service() {
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var thread: HandlerThread? = null
    private var lastOcrAt = 0L
    @Volatile private var busy = false
    private var lastFrameSignature = 0L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var ocr: TesseractOcr

    override fun onCreate() {
        super.onCreate()
        ocr = TesseractOcr(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        val resultCode = intent?.getIntExtra(EXTRA_RESULT, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data = getProjectionData(intent)
        if (resultCode != Activity.RESULT_OK || data == null) { stopSelf(); return START_NOT_STICKY }
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        else startForeground(NOTIFICATION_ID, notification())
        startProjection(resultCode, data)
        return START_STICKY
    }

    @Suppress("DEPRECATION")
    private fun getProjectionData(intent: Intent?): Intent? = if (Build.VERSION.SDK_INT >= 33)
        intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java)
    else intent?.getParcelableExtra(EXTRA_DATA) as? Intent

    private fun startProjection(resultCode: Int, data: Intent) {
        try {
            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = manager.getMediaProjection(resultCode, data)
            val metrics = resources.displayMetrics
            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val density = metrics.densityDpi
            thread = HandlerThread("NoorScreenOCR").apply { start() }
            val handler = Handler(thread!!.looper)
            reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3)
            reader!!.setOnImageAvailableListener({ source -> processLatestFrame(source) }, handler)
            projection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { stopSelf() }
            }, handler)
            virtualDisplay = projection?.createVirtualDisplay("NoorRideOCR", width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader!!.surface, null, handler)
        } catch (_: Exception) { stopSelf() }
    }

    private fun processLatestFrame(source: ImageReader) {
        val runtime = getSharedPreferences("runtime", MODE_PRIVATE)
        if (!runtime.getBoolean("target_app_visible", false)) { runCatching { source.acquireLatestImage()?.close() }; return }
        val now = System.currentTimeMillis()
        if (busy || now - lastOcrAt < 1400L) { runCatching { source.acquireLatestImage()?.close() }; return }
        val image = runCatching { source.acquireLatestImage() }.getOrNull() ?: return
        busy = true; lastOcrAt = now
        val plane = image.planes.firstOrNull()
        if (plane == null) { image.close(); busy = false; return }
        val bitmap = try {
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * image.width
            val full = Bitmap.createBitmap(image.width + rowPadding / pixelStride, image.height, Bitmap.Config.ARGB_8888)
            full.copyPixelsFromBuffer(buffer)
            Bitmap.createBitmap(full, 0, 0, image.width, image.height).also { full.recycle() }
        } catch (_: Exception) { null } finally { image.close() }
        if (bitmap == null) { busy = false; return }
        scope.launch {
            var cropped: Bitmap? = null
            try {
                val settings = (application as NoorApplication).settings.settings.first()
                val left = (bitmap.width * settings.ocrCropLeftPercent.coerceIn(0, 99) / 100).coerceIn(0, bitmap.width - 1)
                val top = (bitmap.height * settings.ocrCropTopPercent.coerceIn(0, 99) / 100).coerceIn(0, bitmap.height - 1)
                val width = (bitmap.width * settings.ocrCropWidthPercent.coerceIn(1, 100) / 100).coerceAtMost(bitmap.width - left).coerceAtLeast(1)
                val height = (bitmap.height * settings.ocrCropHeightPercent.coerceIn(1, 100) / 100).coerceAtMost(bitmap.height - top).coerceAtLeast(1)
                cropped = Bitmap.createBitmap(bitmap, left, top, width, height)
                val signature = frameSignature(cropped!!)
                if (signature == lastFrameSignature) return@launch
                lastFrameSignature = signature
                val text = ocr.recognize(cropped!!)
                val parsed = RideTextParser().parse(text)
                runtime.edit().putLong("last_read_at", System.currentTimeMillis())
                    .putString("read_status", if (parsed != null) "OCR: تم استخراج الأجرة والمسافة" else "OCR: تعذر تحديد الأجرة والمسافة بثقة")
                    .apply()
                if (text.isNotBlank()) RidePipeline(this@ScreenCaptureService).analyze(text, "OCR")
            } catch (_: Exception) {
                runtime.edit().putString("ocr_error", "تعذر استخراج النص من الشاشة").apply()
            } finally {
                if (cropped != null && cropped !== bitmap) cropped?.recycle()
                bitmap.recycle()
                busy = false
            }
        }
    }

    private fun frameSignature(source: Bitmap): Long {
        val sample = Bitmap.createScaledBitmap(source, 24, 24, true)
        val pixels = IntArray(24 * 24)
        sample.getPixels(pixels, 0, 24, 0, 0, 24, 24)
        sample.recycle()
        var hash = 1125899906842597L
        for (pixel in pixels) {
            val grey = (((pixel shr 16) and 0xff) * 3 + ((pixel shr 8) and 0xff) * 6 + (pixel and 0xff)) / 10
            hash = hash * 31 + grey
        }
        return hash
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "قراءة عروض الرحلات", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun notification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_menu_view)
        .setContentTitle("Noor Al-Salhi")
        .setContentText("قراءة OCR تعمل فقط عند ظهور تطبيق Baly Driver")
        .setOngoing(true)
        .setContentIntent(android.app.PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE))
        .build()

    override fun onDestroy() {
        runCatching { virtualDisplay?.release() }; runCatching { reader?.close() }; runCatching { projection?.stop() }
        runCatching { ocr.close() }; thread?.quitSafely(); scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    companion object {
        const val ACTION_STOP = "com.nooralsalhi.rideanalyzer.STOP_CAPTURE"
        private const val EXTRA_RESULT = "result_code"
        private const val EXTRA_DATA = "projection_data"
        private const val CHANNEL_ID = "noor_capture"
        private const val NOTIFICATION_ID = 601
        fun start(context: Context, resultCode: Int, data: Intent) {
            val i = Intent(context, ScreenCaptureService::class.java).putExtra(EXTRA_RESULT, resultCode).putExtra(EXTRA_DATA, data)
            androidx.core.content.ContextCompat.startForegroundService(context, i)
        }
        fun stop(context: Context) { context.startService(Intent(context, ScreenCaptureService::class.java).setAction(ACTION_STOP)) }
    }
}
