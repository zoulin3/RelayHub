package dev.relayhub.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Endpoint::class], version = 5, exportSchema = true)
abstract class RelayDatabase : RoomDatabase() {
    abstract fun endpointDao(): EndpointDao

    companion object {
        /** v1 → v2：新增余额缓存字段 */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE endpoints ADD COLUMN balanceText TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE endpoints ADD COLUMN balanceAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE endpoints ADD COLUMN balanceError TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v2 → v3：新增网页面板账号字段 */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE endpoints ADD COLUMN account TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE endpoints ADD COLUMN accountPassword TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE endpoints ADD COLUMN authToken TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE endpoints ADD COLUMN tokenExpiresAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v3 → v4：新增网页面板登录 Cookie（HttpOnly，脚本读不到） */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE endpoints ADD COLUMN siteCookie TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v4 → v5：新增 new-api 的 New-Api-User 用户 id */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE endpoints ADD COLUMN siteUserId TEXT NOT NULL DEFAULT ''")
            }
        }

        @Volatile
        private var instance: RelayDatabase? = null

        fun get(context: Context): RelayDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                RelayDatabase::class.java,
                "relay_hub.db",
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
                .also { instance = it }
        }
    }
}
