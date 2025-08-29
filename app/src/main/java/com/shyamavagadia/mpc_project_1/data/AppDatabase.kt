package com.shyamavagadia.mpc_project_1.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [User::class, ClassLocation::class, TimeWindow::class, Attendance::class, TimetableEntry::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun classLocationDao(): ClassLocationDao
    abstract fun timeWindowDao(): TimeWindowDao
    abstract fun attendanceDao(): AttendanceDao
    abstract fun timetableDao(): TimetableDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "attendance-db"
            ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
        }
    }
}


