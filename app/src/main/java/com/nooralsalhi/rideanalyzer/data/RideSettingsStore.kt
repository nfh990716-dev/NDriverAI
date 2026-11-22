package com.nooralsalhi.rideanalyzer.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.nooralsalhi.rideanalyzer.domain.DistanceMode
import com.nooralsalhi.rideanalyzer.domain.FuelMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.noorDataStore by preferencesDataStore("noor_settings")

class RideSettingsStore(private val context: Context) {
    private object K {
        val commission = doublePreferencesKey("commission")
        val fuelMode = stringPreferencesKey("fuel_mode")
        val fuelPrice = doublePreferencesKey("fuel_price")
        val consumption = doublePreferencesKey("consumption")
        val fuel100 = doublePreferencesKey("fuel_100")
        val maintenance = doublePreferencesKey("maintenance")
        val tyres = doublePreferencesKey("tyres")
        val depreciation = doublePreferencesKey("depreciation")
        val reserve = doublePreferencesKey("reserve")
        val extraKm = doublePreferencesKey("extra_km")
        val extraRide = doublePreferencesKey("extra_ride")
        val distanceMode = stringPreferencesKey("distance_mode")
        val pickupKm = doublePreferencesKey("pickup_km")
        val extraDistance = doublePreferencesKey("extra_distance")
        val distanceBuffer = doublePreferencesKey("distance_buffer")
        val badBelow = longPreferencesKey("bad_below")
        val averageFrom = longPreferencesKey("average_from")
        val goodFrom = longPreferencesKey("good_from")
        val excellentFrom = longPreferencesKey("excellent_from")
        val overlayEnabled = booleanPreferencesKey("overlay_enabled")
        val showCostDetails = booleanPreferencesKey("show_cost_details")
        val overlayCompact = booleanPreferencesKey("overlay_compact")
        val overlayAlpha = floatPreferencesKey("overlay_alpha")
        val targetPackage = stringPreferencesKey("target_package")
        val cropLeft = intPreferencesKey("ocr_crop_left")
        val cropTop = intPreferencesKey("ocr_crop_top")
        val cropWidth = intPreferencesKey("ocr_crop_width")
        val cropHeight = intPreferencesKey("ocr_crop_height")
        val serviceEnabled = booleanPreferencesKey("service_enabled")
    }

    val settings: Flow<RideSettings> = context.noorDataStore.data.map { p ->
        RideSettings(
            commissionPercent = p[K.commission] ?: 20.0,
            fuelMode = runCatching { FuelMode.valueOf(p[K.fuelMode] ?: "LITRE_PRICE") }.getOrDefault(FuelMode.LITRE_PRICE),
            fuelPricePerLitre = p[K.fuelPrice] ?: 0.0,
            consumptionPer100Km = p[K.consumption] ?: 0.0,
            fuelCostPer100Km = p[K.fuel100] ?: 0.0,
            maintenancePerKm = p[K.maintenance] ?: 0.0,
            tyresPerKm = p[K.tyres] ?: 0.0,
            depreciationPerKm = p[K.depreciation] ?: 0.0,
            reservePerKm = p[K.reserve] ?: 0.0,
            extraPerKm = p[K.extraKm] ?: 0.0,
            extraPerRide = p[K.extraRide] ?: 0.0,
            distanceMode = runCatching { DistanceMode.valueOf(p[K.distanceMode] ?: "TRIP_ONLY") }.getOrDefault(DistanceMode.TRIP_ONLY),
            defaultPickupKm = p[K.pickupKm] ?: 0.0,
            extraDistanceKm = p[K.extraDistance] ?: 0.0,
            distanceBufferPercent = p[K.distanceBuffer] ?: 0.0,
            badBelow = p[K.badBelow] ?: 500L,
            averageFrom = p[K.averageFrom] ?: 500L,
            goodFrom = p[K.goodFrom] ?: 1000L,
            excellentFrom = p[K.excellentFrom] ?: 1500L,
            overlayEnabled = p[K.overlayEnabled] ?: true,
            showCostDetails = p[K.showCostDetails] ?: false,
            overlayCompact = p[K.overlayCompact] ?: false,
            overlayAlpha = p[K.overlayAlpha] ?: 0.96f,
            targetPackage = (p[K.targetPackage] ?: DEFAULT_TARGET_PACKAGE).let {
                if (it.equals("super", ignoreCase = true) || it.isBlank()) DEFAULT_TARGET_PACKAGE else it
            },
            ocrCropLeftPercent = p[K.cropLeft] ?: 0,
            ocrCropTopPercent = p[K.cropTop] ?: 0,
            ocrCropWidthPercent = p[K.cropWidth] ?: 100,
            ocrCropHeightPercent = p[K.cropHeight] ?: 100,
            serviceEnabled = p[K.serviceEnabled] ?: true
        )
    }

    suspend fun save(s: RideSettings) {
        context.noorDataStore.edit { p ->
            p[K.commission] = s.commissionPercent
            p[K.fuelMode] = s.fuelMode.name
            p[K.fuelPrice] = s.fuelPricePerLitre
            p[K.consumption] = s.consumptionPer100Km
            p[K.fuel100] = s.fuelCostPer100Km
            p[K.maintenance] = s.maintenancePerKm
            p[K.tyres] = s.tyresPerKm
            p[K.depreciation] = s.depreciationPerKm
            p[K.reserve] = s.reservePerKm
            p[K.extraKm] = s.extraPerKm
            p[K.extraRide] = s.extraPerRide
            p[K.distanceMode] = s.distanceMode.name
            p[K.pickupKm] = s.defaultPickupKm
            p[K.extraDistance] = s.extraDistanceKm
            p[K.distanceBuffer] = s.distanceBufferPercent
            p[K.badBelow] = s.badBelow
            p[K.averageFrom] = s.averageFrom
            p[K.goodFrom] = s.goodFrom
            p[K.excellentFrom] = s.excellentFrom
            p[K.overlayEnabled] = s.overlayEnabled
            p[K.showCostDetails] = s.showCostDetails
            p[K.overlayCompact] = s.overlayCompact
            p[K.overlayAlpha] = s.overlayAlpha
            p[K.targetPackage] = s.targetPackage.trim().lowercase()
            p[K.cropLeft] = s.ocrCropLeftPercent
            p[K.cropTop] = s.ocrCropTopPercent
            p[K.cropWidth] = s.ocrCropWidthPercent
            p[K.cropHeight] = s.ocrCropHeightPercent
            p[K.serviceEnabled] = s.serviceEnabled
        }
        context.getSharedPreferences("runtime", Context.MODE_PRIVATE).edit()
            .putString("target_package_cache", s.targetPackage.trim().lowercase())
            .putBoolean("service_enabled_cache", s.serviceEnabled).apply()
    }
}
