package com.nooralsalhi.rideanalyzer.data

import com.nooralsalhi.rideanalyzer.domain.DistanceMode
import com.nooralsalhi.rideanalyzer.domain.FuelMode

/** Exact Android package of the ride-driver app monitored by default. */
const val DEFAULT_TARGET_PACKAGE = "app.baly.driver"

/** All money fields are Iraqi dinars (IQD); distance is kilometres. */
data class RideSettings(
    val commissionPercent: Double = 20.0,
    val fuelMode: FuelMode = FuelMode.LITRE_PRICE,
    val fuelPricePerLitre: Double = 0.0,
    val consumptionPer100Km: Double = 0.0,
    val fuelCostPer100Km: Double = 0.0,
    val maintenancePerKm: Double = 0.0,
    val tyresPerKm: Double = 0.0,
    val depreciationPerKm: Double = 0.0,
    val reservePerKm: Double = 0.0,
    val extraPerKm: Double = 0.0,
    val extraPerRide: Double = 0.0,
    val distanceMode: DistanceMode = DistanceMode.TRIP_ONLY,
    val defaultPickupKm: Double = 0.0,
    val extraDistanceKm: Double = 0.0,
    val distanceBufferPercent: Double = 0.0,
    val badBelow: Long = 500,
    val averageFrom: Long = 500,
    val goodFrom: Long = 1000,
    val excellentFrom: Long = 1500,
    val overlayEnabled: Boolean = true,
    val showCostDetails: Boolean = false,
    val overlayCompact: Boolean = false,
    val overlayAlpha: Float = 0.96f,
    val targetPackage: String = DEFAULT_TARGET_PACKAGE,
    val ocrCropLeftPercent: Int = 0,
    val ocrCropTopPercent: Int = 0,
    val ocrCropWidthPercent: Int = 100,
    val ocrCropHeightPercent: Int = 100,
    val serviceEnabled: Boolean = true
)

fun RideSettings.hasFuelProfile(): Boolean = when (fuelMode) {
    FuelMode.LITRE_PRICE -> fuelPricePerLitre.isFinite() && consumptionPer100Km.isFinite() && fuelPricePerLitre > 0.0 && consumptionPer100Km > 0.0
    FuelMode.COST_PER_100_KM -> fuelCostPer100Km.isFinite() && fuelCostPer100Km > 0.0
}
