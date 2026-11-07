package me.ethanhoward.countdown.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Query("SELECT * FROM events ORDER BY targetDate, targetTime")
    fun observeAll(): Flow<List<Event>>

    @Query("SELECT * FROM events ORDER BY targetDate, targetTime")
    suspend fun getAll(): List<Event>

    @Query("SELECT * FROM events WHERE id = :id")
    fun observe(id: Long): Flow<Event?>

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun get(id: Long): Event?

    @Insert
    suspend fun insert(event: Event): Long

    @Update
    suspend fun update(event: Event)

    @Delete
    suspend fun delete(event: Event)
}
