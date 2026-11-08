package me.ethanhoward.countdown.data

import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Owns the background image files for events. */
class EventImageStore(
    private val dir: File,
    /** Older locations that may still hold referenced images (v1 stored them per widget). */
    private val legacyDirs: List<File> = emptyList(),
) {
    /**
     * Fresh, uniquely named files. Each save writes new files and then deletes the replaced ones,
     * so a widget never reads a half-written image and needs no event id before the first insert.
     */
    fun newProcessedFile() = File(dir, "${UUID.randomUUID()}.webp")
    fun newSourceFile() = File(dir, "${UUID.randomUUID()}_src.jpg")

    suspend fun delete(vararg paths: String?) = withContext(Dispatchers.IO) {
        paths.filterNotNull().forEach { File(it).delete() }
    }

    /**
     * Deletes image files no event references. Files modified in the last hour are kept so an
     * in-progress save (file written, database not yet updated) is never pruned.
     */
    suspend fun prune(referencedPaths: Set<String>) = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1)
        (listOf(dir) + legacyDirs)
            .flatMap { it.listFiles()?.toList().orEmpty() }
            .filter { it.isFile && it.absolutePath !in referencedPaths && it.lastModified() < cutoff }
            .forEach { it.delete() }
        legacyDirs.filter { it.list()?.isEmpty() == true }.forEach { it.delete() }
    }
}
