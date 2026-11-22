package com.nooralsalhi.rideanalyzer.domain

import com.nooralsalhi.rideanalyzer.data.RideSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfitCalculatorTest {
    private val calculator = ProfitCalculator()
    private val base = RideSettings(commissionPercent = 0.0, fuelMode = FuelMode.COST_PER_100_KM, fuelCostPer100Km = 1.0)
    private fun result(net: Long) = calculator.calculate(
        ParsedRide(fareIqd = net, tripDistanceKm = 1.0, pickupDistanceKm = null, confidence = 0.98, fareEvidence = "fare", distanceEvidence = "1 km"),
        base
    ).rating

    @Test fun defaultThresholdsAreExact() {
        assertEquals(RideRating.BAD, result(499))
        assertEquals(RideRating.AVERAGE, result(500))
        assertEquals(RideRating.AVERAGE, result(999))
        assertEquals(RideRating.GOOD, result(1000))
        assertEquals(RideRating.GOOD, result(1499))
        assertEquals(RideRating.EXCELLENT, result(1500))
    }
}
