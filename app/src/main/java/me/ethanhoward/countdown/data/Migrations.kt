package me.ethanhoward.countdown.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migrations {
    /**
     * v2 moves widget styling from each widget onto its event. Each event inherits the appearance
     * of the most recently saved widget that showed it; images stay where they are on disk.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            listOf(
                "`backgroundType` TEXT NOT NULL DEFAULT 'COLOR'",
                "`backgroundColor` INTEGER NOT NULL DEFAULT ${EventAppearance.DEFAULT_COLOR}",
                "`opacity` REAL NOT NULL DEFAULT 1",
                "`blur` REAL NOT NULL DEFAULT 0",
                "`contrast` REAL NOT NULL DEFAULT 1",
                "`imagePath` TEXT",
                "`sourceImagePath` TEXT",
                "`backgroundLuminance` REAL NOT NULL DEFAULT 0",
                "`textColorMode` TEXT NOT NULL DEFAULT 'AUTO'",
                "`appearanceUpdatedAt` INTEGER NOT NULL DEFAULT 0",
            ).forEach { db.execSQL("ALTER TABLE `events` ADD COLUMN $it") }

            db.execSQL(
                """
                UPDATE `events` SET
                    (`backgroundType`, `backgroundColor`, `opacity`, `blur`, `contrast`, `imagePath`,
                     `sourceImagePath`, `backgroundLuminance`, `textColorMode`, `appearanceUpdatedAt`) =
                    (SELECT w.`backgroundType`, w.`backgroundColor`, w.`opacity`, w.`blur`, w.`contrast`,
                            w.`imagePath`,
                            CASE WHEN w.`imagePath` IS NULL THEN NULL
                                 ELSE replace(w.`imagePath`, '.webp', '_src.jpg') END,
                            w.`backgroundLuminance`, w.`textColorMode`, w.`updatedAt`
                     FROM `widget_config` w WHERE w.`eventId` = `events`.`id`
                     ORDER BY w.`updatedAt` DESC LIMIT 1)
                WHERE `id` IN (SELECT `eventId` FROM `widget_config` WHERE `eventId` IS NOT NULL)
                """.trimIndent(),
            )

            db.execSQL(
                "CREATE TABLE `widget_config_new` (`appWidgetId` INTEGER NOT NULL, `eventId` INTEGER, " +
                    "PRIMARY KEY(`appWidgetId`), FOREIGN KEY(`eventId`) REFERENCES `events`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE SET NULL )",
            )
            db.execSQL("INSERT INTO `widget_config_new` (`appWidgetId`, `eventId`) SELECT `appWidgetId`, `eventId` FROM `widget_config`")
            db.execSQL("DROP TABLE `widget_config`")
            db.execSQL("ALTER TABLE `widget_config_new` RENAME TO `widget_config`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_widget_config_eventId` ON `widget_config` (`eventId`)")
        }
    }
}
