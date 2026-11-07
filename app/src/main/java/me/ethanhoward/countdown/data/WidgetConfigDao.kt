package me.ethanhoward.countdown.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface WidgetConfigDao {
    @Query("SELECT * FROM widget_config WHERE appWidgetId = :appWidgetId")
    fun observe(appWidgetId: Int): Flow<WidgetConfig?>

    @Query("SELECT * FROM widget_config WHERE appWidgetId = :appWidgetId")
    suspend fun get(appWidgetId: Int): WidgetConfig?

    @Query("SELECT * FROM widget_config")
    suspend fun getAll(): List<WidgetConfig>

    @Upsert
    suspend fun upsert(config: WidgetConfig)

    @Query("DELETE FROM widget_config WHERE appWidgetId = :appWidgetId")
    suspend fun delete(appWidgetId: Int)
}
