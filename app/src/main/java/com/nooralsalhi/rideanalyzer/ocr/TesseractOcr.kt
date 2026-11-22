package com.nooralsalhi.rideanalyzer.ocr

import android.content.Context
import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class TesseractOcr(private val context: Context) {
    private var api: TessBaseAPI? = null

    suspend fun recognize(bitmap: Bitmap): String = withContext(Dispatchers.Default) {
        val engine = api ?: createEngine().also { api = it }
        synchronized(engine) {
            engine.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
            engine.setImage(bitmap)
            engine.getUTF8Text().orEmpty()
        }
    }

    private fun createEngine(): TessBaseAPI {
        val base = File(context.filesDir, "noor-tesseract")
        val data = File(base, "tessdata")
        data.mkdirs()
        listOf("ara", "eng").forEach { language ->
            val target = File(data, "$language.traineddata")
            if (!target.exists()) context.assets.open("tessdata/$language.traineddata").use { input -> target.outputStream().use { output -> input.copyTo(output) } }
        }
        val engine = TessBaseAPI()
        check(engine.init(base.absolutePath, "ara+eng", TessBaseAPI.OEM_LSTM_ONLY)) { "تعذر تشغيل محرك OCR" }
        return engine
    }

    fun close() { api?.recycle(); api = null }
}
