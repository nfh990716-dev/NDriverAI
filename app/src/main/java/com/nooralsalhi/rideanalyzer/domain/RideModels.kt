package com.nooralsalhi.rideanalyzer.domain

data class ParsedRide(
    val fareIqd: Long,
    val tripDistanceKm: Double,
    val pickupDistanceKm: Double?,
    val confidence: Double,
    val fareEvidence: String,
    val distanceEvidence: String
)

data class RideCalculation(
    val fareIqd: Long,
    val commissionIqd: Long,
    val fuelIqd: Long,
    val maintenanceIqd: Long,
    val tyresIqd: Long,
    val depreciationIqd: Long,
    val reserveIqd: Long,
    val extraIqd: Long,
    val totalCostIqd: Long,
    val netProfitIqd: Long,
    val distanceKm: Double,
    val isEstimated: Boolean,
    val rating: RideRating
)

enum class RideRating(val titleAr: String) {
    BAD("سيء"), AVERAGE("متوسط"), GOOD("جيد"), EXCELLENT("ممتاز")
}

enum class FuelMode { LITRE_PRICE, COST_PER_100_KM }
enum class DistanceMode { TRIP_ONLY, PICKUP_PLUS_TRIP }
