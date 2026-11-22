package com.nooralsalhi.rideanalyzer.domain

import android.content.Context
import com.nooralsalhi.rideanalyzer.NoorApplication
import com.nooralsalhi.rideanalyzer.data.RideEntity
import com.nooralsalhi.rideanalyzer.data.hasFuelProfile
import com.nooralsalhi.rideanalyzer.overlay.FloatingOverlayManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale
import java.security.MessageDigest

class RidePipeline(private val context: Context) {
    private val parser = RideTextParser()
    private val calculator = ProfitCalculator()
    private val app get() = context.applicationContext as NoorApplication

    suspend fun analyze(text: String, source: String): Boolean = analysisMutex.withLock {
        analyzeLocked(text, source)
    }

    private suspend fun analyzeLocked(text: String, source: String): Boolean {
        val parsed = parser.parse(text) ?: return false
        val settings = app.settings.settings.first()
        if (!settings.serviceEnabled) {
            FloatingOverlayManager(context).remove()
            return false
        }
        if (!settings.overlayEnabled) FloatingOverlayManager(context).remove()
        if (!settings.hasFuelProfile()) {
            context.getSharedPreferences("runtime", Context.MODE_PRIVATE).edit()
                .putString("analysis_error", "أدخل سعر الوقود واستهلاك السيارة أولاً").apply()
            return false
        }
        val calculation = calculator.calculate(parsed, settings)
        val contextText = text.lowercase(Locale.ROOT)
            .replace(Regex("[0-9٠-٩۰-۹]+"), " ")
            .replace(Regex("[^\\p{L} ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim().take(500)
        val fingerprint = sha256("${parsed.fareIqd}|${String.format(Locale.US, "%.2f", parsed.tripDistanceKm)}|${parsed.pickupDistanceKm?.let { String.format(Locale.US, "%.2f", it) } ?: "na"}|$contextText")
        val prefs = context.getSharedPreferences("runtime", Context.MODE_PRIVATE)
        val lastHash = prefs.getString("last_fingerprint", null)
        val lastAt = prefs.getLong("last_fingerprint_time", 0L)
        val now = System.currentTimeMillis()
        if (lastHash == fingerprint && now - lastAt < 120_000L) return false

        val entity = RideEntity(
            timestamp = now,
            fareIqd = calculation.fareIqd,
            distanceKm = calculation.distanceKm,
            commissionIqd = calculation.commissionIqd,
            fuelIqd = calculation.fuelIqd,
            maintenanceIqd = calculation.maintenanceIqd,
            tyresIqd = calculation.tyresIqd,
            depreciationIqd = calculation.depreciationIqd,
            reserveIqd = calculation.reserveIqd,
            extraIqd = calculation.extraIqd,
            totalCostIqd = calculation.totalCostIqd,
            netProfitIqd = calculation.netProfitIqd,
            rating = calculation.rating.name,
            confidence = parsed.confidence,
            isEstimated = calculation.isEstimated,
            source = source,
            fingerprint = fingerprint,
            sourceText = "${parsed.fareEvidence} | ${parsed.distanceEvidence}"
        )
        app.database.rideDao().insert(entity)
        prefs.edit().putString("last_fingerprint", fingerprint).putLong("last_fingerprint_time", now)
            .putLong("last_profit", calculation.netProfitIqd).putLong("last_fare", calculation.fareIqd)
            .putFloat("last_distance", calculation.distanceKm.toFloat()).putBoolean("last_estimated", calculation.isEstimated)
            .putString("last_rating", calculation.rating.titleAr).putLong("last_at", now).apply()
        if (settings.overlayEnabled && android.provider.Settings.canDrawOverlays(context)) {
            FloatingOverlayManager(context).show(calculation, settings)
        }
        return true
    }

    private fun sha256(s: String): String = MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
        .joinToString("") { "%02x".format(it) }

    companion object { private val analysisMutex = Mutex() }
}
