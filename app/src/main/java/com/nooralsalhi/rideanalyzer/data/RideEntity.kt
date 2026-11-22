package com.nooralsalhi.rideanalyzer.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rides")
data class RideEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val fareIqd: Long,
    val distanceKm: Double,
    val commissionIqd: Long,
    val fuelIqd: Long,
    val maintenanceIqd: Long,
    val tyresIqd: Long,
    val depreciationIqd: Long,
    val reserveIqd: Long,
    val extraIqd: Long,
    val totalCostIqd: Long,
    val netProfitIqd: Long,
    val rating: String,
    val confidence: Double,
    val isEstimated: Boolean,
    val source: String,
    val fingerprint: String,
    val sourceText: String
)
