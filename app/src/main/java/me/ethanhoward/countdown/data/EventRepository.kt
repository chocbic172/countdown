package me.ethanhoward.countdown.data

import kotlinx.coroutines.flow.Flow

class EventRepository(
    private val dao: EventDao,
    val images: EventImageStore,
    /** Invoked after any change so placed widgets re-render. */
    private val onChanged: suspend () -> Unit,
) {
    fun observeAll(): Flow<List<Event>> = dao.observeAll()
    fun observe(id: Long): Flow<Event?> = dao.observe(id)
    suspend fun getAll(): List<Event> = dao.getAll()
    suspend fun get(id: Long): Event? = dao.get(id)

    /** Inserts or updates [event] and returns its id. */
    suspend fun save(event: Event): Long {
        val id = if (event.id == 0L) dao.insert(event) else event.id.also { dao.update(event) }
        onChanged()
        return id
    }

    suspend fun delete(event: Event) {
        dao.delete(event)
        images.delete(event.appearance.imagePath, event.appearance.sourceImagePath)
        onChanged()
    }

    /** Removes image files left behind by deleted events, replaced photos or the v1 layout. */
    suspend fun pruneImages() {
        val referenced = getAll()
            .flatMap { listOf(it.appearance.imagePath, it.appearance.sourceImagePath) }
            .filterNotNull()
            .toSet()
        images.prune(referenced)
    }
}
