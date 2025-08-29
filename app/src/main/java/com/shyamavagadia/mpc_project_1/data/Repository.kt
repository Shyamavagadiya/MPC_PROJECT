package com.shyamavagadia.mpc_project_1.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class AttendanceRepository private constructor(private val db: AppDatabase) {

    fun observeClasses(): Flow<List<ClassLocation>> = db.classLocationDao().observeAll()
    suspend fun upsertClassLocation(item: ClassLocation): Long = db.classLocationDao().upsert(item)
    suspend fun deleteClassLocation(id: Long) {
        db.classLocationDao().deleteById(id)
        db.timeWindowDao().deleteForClass(id)
    }
    fun observeTimeWindows(classId: Long): Flow<List<TimeWindow>> = db.timeWindowDao().observeForClass(classId)
    suspend fun setTimeWindows(classId: Long, windows: List<TimeWindow>) {
        db.timeWindowDao().deleteForClass(classId)
        windows.forEach { db.timeWindowDao().insert(it) }
    }
    fun observeAttendance(): Flow<List<Attendance>> = db.attendanceDao().observeAll()
    suspend fun insertAttendance(a: Attendance): Long = db.attendanceDao().insert(a)

    companion object {
        @Volatile private var INSTANCE: AttendanceRepository? = null
        fun get(context: Context): AttendanceRepository = INSTANCE ?: synchronized(this) {
            INSTANCE ?: AttendanceRepository(AppDatabase.get(context)).also { INSTANCE = it }
        }
    }
}


