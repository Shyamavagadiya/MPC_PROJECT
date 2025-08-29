package com.shyamavagadia.mpc_project_1.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val username: String,
    val password: String, // In production, this should be hashed
    val role: UserRole,
    val name: String,
    val email: String,
    val createdAt: Long = System.currentTimeMillis()
)

enum class UserRole {
    TEACHER, STUDENT
}

@Entity(tableName = "class_locations")
data class ClassLocation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int = 75,
    val teacherId: Long // Add teacher reference
)

@Entity(tableName = "time_windows")
data class TimeWindow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val classLocationId: Long,
    val startMinutesOfDay: Int, // e.g., 13*60 for 1:00 PM
    val endMinutesOfDay: Int    // exclusive
)

enum class AttendanceType { CHECK_IN }

@Entity(tableName = "attendance")
data class Attendance(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val classLocationId: Long,
    val studentId: Long, // Add student reference
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val isMock: Boolean,
    val type: AttendanceType = AttendanceType.CHECK_IN
)


