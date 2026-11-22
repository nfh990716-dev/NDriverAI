package com.nooralsalhi.rideanalyzer.domain

import org.junit.Assert.*
import org.junit.Test

class RideTextParserTest {
    private val parser = RideTextParser()
    @Test fun parsesEnglishFareAndDistance() {
        val ride = parser.parse("Fare: 5,000 IQD\nDistance: 8 km")!!
        assertEquals(5000L, ride.fareIqd)
        assertEquals(8.0, ride.tripDistanceKm, 0.001)
    }
    @Test fun parsesArabicIndicDigits() {
        val ride = parser.parse("الأجرة: ٥٬٠٠٠ د.ع\nالمسافة: ٨ كم")!!
        assertEquals(5000L, ride.fareIqd)
        assertEquals(8.0, ride.tripDistanceKm, 0.001)
    }
    @Test fun parsesCurrencyBeforeFare() {
        val ride = parser.parse("د.ع 5,000\n8 km")!!
        assertEquals(5000L, ride.fareIqd)
        assertEquals(8.0, ride.tripDistanceKm, 0.001)
    }
    @Test fun selectsTripDistanceWhenPickupIsSeparate() {
        val ride = parser.parse("الأجرة 5000 د.ع\nمسافة الوصول 2 كم\nالمسافة 8 كم")!!
        assertEquals(2.0, ride.pickupDistanceKm!!, 0.001)
        assertEquals(8.0, ride.tripDistanceKm, 0.001)
    }
    @Test fun refusesUnlabelledRandomNumbers() {
        assertNull(parser.parse("رقم الطلب 1256\nوقت 7:30\nشارع 42"))
        assertNull(parser.parse("الأجرة 5000 د.ع\n2 كم و8 كم"))
    }
}
