package com.shyamavagadia.mpc_project_1.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class AttendanceRepository private constructor(private val db: AppDatabase) {

    // User authentication methods
    suspend fun authenticateUser(username: String, password: String): User? = 
        db.userDao().authenticate(username, password)
    
    suspend fun registerUser(user: User): Long = db.userDao().insert(user)
    
    suspend fun getUserByUsername(username: String): User? = 
        db.userDao().getUserByUsername(username)
    
    suspend fun getUserById(id: Long): User? = db.userDao().getUserById(id)
    
    fun observeUsersByRole(role: UserRole): Flow<List<User>> = 
        db.userDao().observeUsersByRole(role)

    // Sample data creation for testing
    suspend fun createSampleData() {
        // Create sample teacher
        val teacher = User(
            username = "teacher",
            password = "password",
            role = UserRole.TEACHER,
            name = "Dr. Smith",
            email = "teacher@university.edu"
        )
        val teacherId = registerUser(teacher)
        
        // Create sample students
        val student1 = User(
            username = "student1",
            password = "password",
            role = UserRole.STUDENT,
            name = "John Doe",
            email = "john@university.edu"
        )
        val student1Id = registerUser(student1)
        
        val student2 = User(
            username = "student2", 
            password = "password",
            role = UserRole.STUDENT,
            name = "Jane Smith",
            email = "jane@university.edu"
        )
        val student2Id = registerUser(student2)
        
        // Create sample class location
        val classLocation = ClassLocation(
            name = "MA102 - Calculus",
            latitude = 40.7128, // New York coordinates for demo
            longitude = -74.0060,
            radiusMeters = 50,
            teacherId = teacherId
        )
        val classId = upsertClassLocation(classLocation)
        
        // Create sample timetable entry
        val timetableEntry = TimetableEntry(
            teacherId = teacherId,
            classLocationId = classId,
            subject = "MA102 - Calculus",
            dayOfWeek = 2, // Monday
            startMinutesOfDay = 9 * 60, // 9:00 AM
            endMinutesOfDay = 10 * 60 + 30 // 10:30 AM
        )
        val entryId = upsertTimetableEntry(timetableEntry)
        
        // Create sample attendance records
        insertAttendance(Attendance(
            classLocationId = classId,
            studentId = student1Id,
            timetableEntryId = entryId,
            timestamp = System.currentTimeMillis(),
            latitude = 40.7128,
            longitude = -74.0060,
            accuracyMeters = 10f,
            isMock = false
        ))
        
        insertAttendance(Attendance(
            classLocationId = classId,
            studentId = student2Id,
            timetableEntryId = entryId,
            timestamp = System.currentTimeMillis(),
            latitude = 40.7128,
            longitude = -74.0060,
            accuracyMeters = 15f,
            isMock = false
        ))
    }

    // Class location methods
    fun observeClasses(): Flow<List<ClassLocation>> = db.classLocationDao().observeAll()
    fun observeClassesByTeacher(teacherId: Long): Flow<List<ClassLocation>> = 
        db.classLocationDao().observeByTeacher(teacherId)
    
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
    
    // Attendance methods
    fun observeAttendance(): Flow<List<Attendance>> = db.attendanceDao().observeAll()
    fun observeAttendanceByStudent(studentId: Long): Flow<List<Attendance>> = 
        db.attendanceDao().observeByStudent(studentId)
    fun observeAttendanceByTimetable(entryId: Long): Flow<List<Attendance>> =
        db.attendanceDao().observeByTimetableEntry(entryId)
    suspend fun getAttendanceForStudentAndEntry(entryId: Long, studentId: Long): Attendance? =
        db.attendanceDao().getAttendanceForStudentAndEntry(entryId, studentId)
    suspend fun deleteAttendanceForStudentAndEntry(entryId: Long, studentId: Long) =
        db.attendanceDao().deleteAttendanceForStudentAndEntry(entryId, studentId)
    
    suspend fun insertAttendance(a: Attendance): Long = db.attendanceDao().insert(a)

    // Timetable methods
    fun observeTimetableForTeacher(teacherId: Long): Flow<List<TimetableEntry>> =
        db.timetableDao().observeForTeacher(teacherId)
    fun observeTimetableForDay(dayOfWeek: Int): Flow<List<TimetableEntry>> =
        db.timetableDao().observeForDay(dayOfWeek)
    suspend fun upsertTimetableEntry(entry: TimetableEntry): Long =
        db.timetableDao().upsert(entry)
    suspend fun deleteTimetableEntry(id: Long) = db.timetableDao().deleteById(id)

    companion object {
        @Volatile private var INSTANCE: AttendanceRepository? = null
        fun get(context: Context): AttendanceRepository = INSTANCE ?: synchronized(this) {
            INSTANCE ?: AttendanceRepository(AppDatabase.get(context)).also { INSTANCE = it }
        }
    }
}


