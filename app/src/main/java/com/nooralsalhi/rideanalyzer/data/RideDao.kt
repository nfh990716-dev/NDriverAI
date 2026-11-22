package com.nooralsalhi.rideanalyzer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RideDao {
    @Insert suspend fun insert(ride: RideEntity): Long
    @Query("SELECT * FROM rides ORDER BY timestamp DESC") fun latest(): Flow<List<RideEntity>>
    @Query("SELECT * FROM rides WHERE id = :id LIMIT 1") fun byId(id: Long): Flow<RideEntity?>
    @Query("DELETE FROM rides WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM rides") suspend fun clear()
    @Query("SELECT COALESCE(SUM(netProfitIqd),0) FROM rides WHERE timestamp >= :from AND timestamp < :to") fun totalBetween(from: Long, to: Long): Flow<Long>
    @Query("SELECT COUNT(*) FROM rides WHERE timestamp >= :from AND timestamp < :to") fun countBetween(from: Long, to: Long): Flow<Int>
}
