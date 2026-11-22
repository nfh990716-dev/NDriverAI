package com.nooralsalhi.rideanalyzer

import android.app.Application
import com.nooralsalhi.rideanalyzer.data.AppDatabase
import com.nooralsalhi.rideanalyzer.data.RideSettingsStore

class NoorApplication : Application() {
    val database by lazy { AppDatabase.get(this) }
    val settings by lazy { RideSettingsStore(this) }
}
