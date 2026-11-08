package me.ethanhoward.countdown.data

import kotlinx.coroutines.flow.Flow

class WidgetConfigRepository(private val dao: WidgetConfigDao) {
    fun observe(appWidgetId: Int): Flow<WidgetConfig?> = dao.observe(appWidgetId)
    suspend fun get(appWidgetId: Int): WidgetConfig? = dao.get(appWidgetId)
    suspend fun getAll(): List<WidgetConfig> = dao.getAll()
    suspend fun save(config: WidgetConfig) = dao.upsert(config)

    /** Forgets a widget that was removed from the home screen. */
    suspend fun delete(appWidgetId: Int) = dao.delete(appWidgetId)
}
