package com.nooralsalhi.rideanalyzer.domain

import com.nooralsalhi.rideanalyzer.data.RideSettings
import kotlin.math.ceil
import kotlin.math.roundToLong

class ProfitCalculator {
    fun calculate(ride: ParsedRide, settings: RideSettings): RideCalculation {
        val pickup = if (settings.distanceMode == DistanceMode.PICKUP_PLUS_TRIP) (ride.pickupDistanceKm ?: settings.defaultPickupKm) else 0.0
        val baseDistance = ride.tripDistanceKm + pickup + settings.extraDistanceKm
        val distance = baseDistance * (1.0 + settings.distanceBufferPercent.coerceAtLeast(0.0) / 100.0)
        val commission = (ride.fareIqd * settings.commissionPercent.coerceIn(0.0, 100.0) / 100.0).roundToLong()
        val fuelPer100 = when (settings.fuelMode) {
            FuelMode.LITRE_PRICE -> settings.consumptionPer100Km * settings.fuelPricePerLitre
            FuelMode.COST_PER_100_KM -> settings.fuelCostPer100Km
        }
        val fuel = (distance * fuelPer100 / 100.0).roundToLong()
        val maintenance = (distance * settings.maintenancePerKm).roundToLong()
        val tyres = (distance * settings.tyresPerKm).roundToLong()
        val depreciation = (distance * settings.depreciationPerKm).roundToLong()
        val reserve = (distance * settings.reservePerKm).roundToLong()
        val extra = (distance * settings.extraPerKm + settings.extraPerRide).roundToLong()
        val total = commission + fuel + maintenance + tyres + depreciation + reserve + extra
        val net = ride.fareIqd - total
        val rating = when {
            net < settings.averageFrom -> RideRating.BAD
            net < settings.goodFrom -> RideRating.AVERAGE
            net < settings.excellentFrom -> RideRating.GOOD
            else -> RideRating.EXCELLENT
        }
        val estimated = (settings.distanceMode == DistanceMode.PICKUP_PLUS_TRIP && ride.pickupDistanceKm == null) || settings.distanceMode == DistanceMode.TRIP_ONLY || ride.confidence < 0.95 || settings.extraDistanceKm > 0.0 || settings.distanceBufferPercent > 0.0
        return RideCalculation(
            ride.fareIqd, commission, fuel, maintenance, tyres, depreciation, reserve, extra,
            total, net, distance, estimated, rating
        )
    }
}
