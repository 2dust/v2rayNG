package com.npv.crsgw.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/*
class ServerRepository(context: Context) {
    private val db = NpvDatabase.getInstance(applicationContext)
    private val serverDao = db.serverDao()

    suspend fun insertServer(server) {
        serverDao.insertServer(server)
    }
}
 */

/*
// 自动升级： 加字段、加表、改索引
@Database(
    entities = [Server::class],
    version = 2,
    autoMigrations = [AutoMigration(from = 1, to = 2)]
)
*/
@Database(
    entities = [Server::class],
    version = 1
)
abstract class NpvDatabase : RoomDatabase() {
    abstract fun serverDao(): ServerDao

    companion object {
        @Volatile private var INSTANCE: NpvDatabase? = null

        fun getInstance(context: Context): NpvDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    NpvDatabase::class.java, "npv"
                ).build().also { INSTANCE = it }
            }
        }
    }
}