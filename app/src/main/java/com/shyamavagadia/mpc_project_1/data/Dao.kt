package com.shyamavagadia.mpc_project_1.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(user: User): Long

    @Query("SELECT * FROM users WHERE username = :username AND password = :password")
    suspend fun authenticate(username: String, password: String): User?

    @Query("SELECT * FROM users WHERE username = :username")
    suspend fun getUserByUsername(username: String): User?

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun getUserById(id: Long): User?

    @Query("SELECT * FROM users WHERE role = :role")
    fun observeUsersByRole(role: UserRole): Flow<List<User>>
}

@Dao
interface ClassLocationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(location: ClassLocation): Long

    @Query("SELECT * FROM class_locations ORDER BY name")
    fun observeAll(): Flow<List<ClassLocation>>

    @Query("SELECT * FROM class_locations WHERE teacherId = :teacherId ORDER BY name")
    fun observeByTeacher(teacherId: Long): Flow<List<ClassLocation>>

    @Query("SELECT * FROM class_locations WHERE id = :id")
    suspend fun getById(id: Long): ClassLocation?
    
    @Query("DELETE FROM class_locations WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface TimeWindowDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(window: TimeWindow): Long

    @Query("SELECT * FROM time_windows WHERE classLocationId = :classId")
    fun observeForClass(classId: Long): Flow<List<TimeWindow>>

    @Query("DELETE FROM time_windows WHERE classLocationId = :classId")
    suspend fun deleteForClass(classId: Long)
}

@Dao
interface AttendanceDao {
    @Insert
    suspend fun insert(record: Attendance): Long

    @Query("SELECT * FROM attendance ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<Attendance>>

    @Query("SELECT * FROM attendance WHERE studentId = :studentId ORDER BY timestamp DESC")
    fun observeByStudent(studentId: Long): Flow<List<Attendance>>
}


