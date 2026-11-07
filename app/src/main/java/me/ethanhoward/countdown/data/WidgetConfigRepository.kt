package me.ethanhoward.countdown.data

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class WidgetConfigRepository(
    private val dao: WidgetConfigDao,
    /** Directory holding per-widget background images. */
    val imageDir: File,
) {
    fun observe(appWidgetId: Int): Flow<WidgetConfig?> = dao.observe(appWidgetId)
    suspend fun get(appWidgetId: Int): WidgetConfig? = dao.get(appWidgetId)
    suspend fun getAll(): List<WidgetConfig> = dao.getAll()
    suspend fun save(config: WidgetConfig) = dao.upsert(config.copy(updatedAt = System.currentTimeMillis()))

    fun processedImageFile(appWidgetId: Int) = File(imageDir, "$appWidgetId.webp")
    fun sourceImageFile(appWidgetId: Int) = File(imageDir, "${appWidgetId}_src.jpg")

    /** Removes the config row and any image files for a widget that was deleted from the home screen. */
    suspend fun delete(appWidgetId: Int) {
        dao.delete(appWidgetId)
        withContext(Dispatchers.IO) {
            processedImageFile(appWidgetId).delete()
            sourceImageFile(appWidgetId).delete()
        }
    }
}
