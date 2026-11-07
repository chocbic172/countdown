package me.ethanhoward.countdown.data

import kotlinx.coroutines.flow.Flow

class EventRepository(
    private val dao: EventDao,
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
        onChanged()
    }
}
