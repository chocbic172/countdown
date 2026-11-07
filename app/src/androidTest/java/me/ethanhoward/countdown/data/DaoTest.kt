package me.ethanhoward.countdown.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DaoTest {
    private lateinit var db: AppDatabase

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
    }

    @After fun tearDown() = db.close()

    @Test fun eventsAreOrderedByDateAndTime() = runBlocking {
        val events = db.eventDao()
        events.insert(Event(title = "Later", targetDate = LocalDate.of(2027, 1, 1)))
        events.insert(Event(title = "Evening", targetDate = LocalDate.of(2026, 12, 25), targetTime = LocalTime.of(18, 0)))
        events.insert(Event(title = "Morning", targetDate = LocalDate.of(2026, 12, 25), targetTime = LocalTime.of(9, 30)))
        assertEquals(listOf("Morning", "Evening", "Later"), events.observeAll().first().map { it.title })
        assertEquals(LocalTime.of(9, 30), events.getAll().first().targetTime)
    }

    @Test fun deletingEventClearsWidgetEventButKeepsConfig() = runBlocking {
        val id = db.eventDao().insert(Event(title = "Party", targetDate = LocalDate.of(2026, 12, 31)))
        db.widgetConfigDao().upsert(WidgetConfig(appWidgetId = 7, eventId = id, opacity = 0.5f))

        db.eventDao().delete(db.eventDao().get(id)!!)

        val config = db.widgetConfigDao().get(7)!!
        assertNull(config.eventId)
        assertEquals(0.5f, config.opacity)
    }
}
