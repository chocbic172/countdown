package me.ethanhoward.countdown.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val dbName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test fun migrate1To2MovesWidgetStylingOntoEvents() {
        val trip = LocalDate.of(2026, 12, 18).toEpochDay()
        helper.createDatabase(dbName, 1).use { db ->
            db.execSQL("INSERT INTO events (id, title, targetDate, targetTime, createdAt) VALUES (1, 'Trip', $trip, NULL, 0)")
            db.execSQL("INSERT INTO events (id, title, targetDate, targetTime, createdAt) VALUES (2, 'Plain', $trip, NULL, 0)")
            val cols = "appWidgetId, eventId, backgroundType, backgroundColor, opacity, blur, contrast, imagePath, backgroundLuminance, textColorMode, updatedAt"
            // Two widgets for event 1: the newer (updatedAt = 200) photo widget should win.
            db.execSQL("INSERT INTO widget_config ($cols) VALUES (10, 1, 'COLOR', -1, 1.0, 0, 1.0, NULL, 1.0, 'AUTO', 100)")
            db.execSQL("INSERT INTO widget_config ($cols) VALUES (11, 1, 'PHOTO', -12627531, 0.8, 9, 1.3, '/files/widget_bg/11.webp', 0.2, 'LIGHT', 200)")
            // A widget whose event was deleted.
            db.execSQL("INSERT INTO widget_config ($cols) VALUES (12, NULL, 'COLOR', -1, 1.0, 0, 1.0, NULL, 1.0, 'AUTO', 50)")
        }

        // Validates the migrated schema against the exported v2 schema, including column defaults.
        helper.runMigrationsAndValidate(dbName, 2, true, Migrations.MIGRATION_1_2).close()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName).addMigrations(Migrations.MIGRATION_1_2).build()
        try {
            runBlocking {
                val styled = db.eventDao().get(1)!!.appearance
                assertEquals(BackgroundType.PHOTO, styled.backgroundType)
                assertEquals(0.8f, styled.opacity)
                assertEquals(9f, styled.blur)
                assertEquals(1.3f, styled.contrast)
                assertEquals("/files/widget_bg/11.webp", styled.imagePath)
                assertEquals("/files/widget_bg/11_src.jpg", styled.sourceImagePath)
                assertEquals(TextColorMode.LIGHT, styled.textColorMode)
                assertEquals(200L, styled.appearanceUpdatedAt)

                assertEquals(EventAppearance(), db.eventDao().get(2)!!.appearance)

                val widgets = db.widgetConfigDao().getAll().sortedBy { it.appWidgetId }
                assertEquals(listOf(WidgetConfig(10, 1), WidgetConfig(11, 1), WidgetConfig(12, null)), widgets)
                assertNull(db.widgetConfigDao().get(99))
            }
        } finally {
            db.close()
        }
    }
}
