package com.shyamavagadia.mpc_project_1.ui

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationToken
import com.google.android.gms.tasks.CancellationTokenSource
import com.shyamavagadia.mpc_project_1.data.AttendanceRepository
import com.shyamavagadia.mpc_project_1.data.ClassLocation
import com.shyamavagadia.mpc_project_1.data.TimeWindow
import com.shyamavagadia.mpc_project_1.data.TimetableEntry
import com.shyamavagadia.mpc_project_1.data.User
import com.shyamavagadia.mpc_project_1.data.UserRole
import com.shyamavagadia.mpc_project_1.data.Attendance
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.shyamavagadia.mpc_project_1.data.SessionManager

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AppRoot() {
    val ctx = LocalContext.current
    val repo = remember { AttendanceRepository.get(ctx) }
    val session = remember { SessionManager(ctx) }
    var currentUser by remember { mutableStateOf<com.shyamavagadia.mpc_project_1.data.User?>(null) }

    // Restore session
    LaunchedEffect(Unit) {
        session.getLoggedInUserId()?.let { uid ->
            val user = repo.getUserById(uid)
            if (user != null) currentUser = user else session.clear()
        }
    }
    
    if (currentUser == null) {
        AuthScreen(onAuthSuccess = { user ->
            session.setLoggedInUserId(user.id)
            currentUser = user
        })
    } else {
        when (currentUser!!.role) {
            com.shyamavagadia.mpc_project_1.data.UserRole.TEACHER -> TeacherScreen(
                currentUser = currentUser!!,
                onLogout = {
                    session.clear()
                    currentUser = null
                }
            )
            com.shyamavagadia.mpc_project_1.data.UserRole.STUDENT -> StudentScreen(
                currentUser = currentUser!!,
                onLogout = {
                    session.clear()
                    currentUser = null
                }
            )
        }
    }
}

class TeacherViewModel(private val repo: AttendanceRepository, private val teacherId: Long) : ViewModel() {
    fun classes() = repo.observeClassesByTeacher(teacherId)
    fun timetable() = repo.observeTimetableForTeacher(teacherId)
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TeacherScreen(currentUser: com.shyamavagadia.mpc_project_1.data.User, onLogout: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { AttendanceRepository.get(ctx) }
    val vm = viewModel<TeacherViewModel>(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return TeacherViewModel(repo, currentUser.id) as T
        }
    })
    val classes by vm.classes().collectAsState(initial = emptyList())
    val timetable by vm.timetable().collectAsState(initial = emptyList())

    var name by remember { mutableStateOf("") }
    var capturedLocation by remember { mutableStateOf<Location?>(null) }
    var radius by remember { mutableStateOf("40") }
    // Optional time window removed for class creation
    var start by remember { mutableStateOf<Int?>(null) }
    var end by remember { mutableStateOf<Int?>(null) }
    var isCapturing by remember { mutableStateOf(false) }
    var showAddForm by remember { mutableStateOf(false) }
    var showAddTimetable by remember { mutableStateOf(false) }
    var subject by remember { mutableStateOf("") }
    var selectedClassId by remember { mutableStateOf<Long?>(null) }
    var selectedDay by remember { mutableStateOf(2) } // default Monday (Calendar.MONDAY = 2)
    var ttStart by remember { mutableStateOf<Int?>(null) }
    var ttEnd by remember { mutableStateOf<Int?>(null) }

    val locationClient = remember { LocationServices.getFusedLocationProviderClient(ctx) }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            isCapturing = true
            scope.launch {
                try {
                    val location = locationClient.getCurrentLocation(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        CancellationTokenSource().token
                    ).await()
                    capturedLocation = location
                } catch (e: Exception) {
                    // Handle error
                } finally {
                    isCapturing = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Teacher Dashboard - ${currentUser.name}") },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.Close, contentDescription = "Logout")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        floatingActionButton = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FloatingActionButton(onClick = { showAddTimetable = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Timetable Entry")
                }
                Spacer(Modifier.height(8.dp))
                FloatingActionButton(onClick = { showAddForm = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Class")
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Class Management",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "${classes.size} classes configured",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // Add Class Form
            if (showAddForm) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Add New Class",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(onClick = { showAddForm = false }) {
                                    Icon(Icons.Default.Close, contentDescription = "Close")
                                }
                            }
                            
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("Class Name (e.g., MA102)") },
                                modifier = Modifier.fillMaxWidth(),
                                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) }
                            )

                            // Location capture section
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.LocationOn, contentDescription = null)
                                        Text("Location", fontWeight = FontWeight.Medium)
                                    }

                                    if (capturedLocation != null) {
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Text(
                                                    "✅ Location Captured",
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                                Text(
                                                    "Lat: ${String.format("%.6f", capturedLocation!!.latitude)}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                                Text(
                                                    "Lng: ${String.format("%.6f", capturedLocation!!.longitude)}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                                Text(
                                                    "Accuracy: ${String.format("%.1f", capturedLocation!!.accuracy)}m",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                        }
                                    } else {
                                        Text(
                                            "📍 No location captured yet",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            when {
                                                ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED -> {
                                                    isCapturing = true
                                                    scope.launch {
                                                        try {
                                                            val location = locationClient.getCurrentLocation(
                                                                Priority.PRIORITY_HIGH_ACCURACY,
                                                                CancellationTokenSource().token
                                                            ).await()
                                                            capturedLocation = location
                                                        } catch (e: Exception) {
                                                            // Handle error
                                                        } finally {
                                                            isCapturing = false
                                                        }
                                                    }
                                                }
                                                else -> permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = !isCapturing
                                    ) {
                                        if (isCapturing) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                color = MaterialTheme.colorScheme.onPrimary
                                            )
                                            Spacer(modifier = Modifier.size(8.dp))
                                        }
                                        Text(if (isCapturing) "Capturing..." else "📱 Capture Current Location")
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = radius,
                                onValueChange = { radius = it },
                                label = { Text("Radius (meters)") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Time window selection removed as per requirement

                            Button(
                                onClick = {
                                    val radI = radius.toIntOrNull() ?: 40
                                    if (name.isNotBlank() && capturedLocation != null) {
                                        scope.launch {
                                            val id = repo.upsertClassLocation(
                                                ClassLocation(
                                                    name = name,
                                                    latitude = capturedLocation!!.latitude,
                                                    longitude = capturedLocation!!.longitude,
                                                    radiusMeters = radI,
                                                    teacherId = currentUser.id
                                                )
                                            )
                                            // No time window saved now
                                            name = ""; capturedLocation = null; radius = "40"; start = null; end = null; showAddForm = false
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = name.isNotBlank() && capturedLocation != null
                            ) {
                                Text("💾 Save Class Location")
                            }
                        }
                    }
                }
            }

            // Add Timetable Form
            if (classes.isNotEmpty() && showAddTimetable) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Add Timetable Entry", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                IconButton(onClick = { showAddTimetable = false }) { Icon(Icons.Default.Close, contentDescription = "Close") }
                            }

                            OutlinedTextField(
                                value = subject,
                                onValueChange = { subject = it },
                                label = { Text("Subject (e.g., MA112)") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Class selector
                            Column {
                                Text("Select Class Location")
                                Spacer(Modifier.height(8.dp))
                                classes.forEach { c ->
                                    OutlinedButton(
                                        onClick = { selectedClassId = c.id },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (selectedClassId == c.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                        )
                                    ) { Text("${c.name}  (r=${c.radiusMeters}m)") }
                                }
                            }

                            // Day selector (simple)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val days = listOf(2 to "Mon", 3 to "Tue", 4 to "Wed", 5 to "Thu", 6 to "Fri", 7 to "Sat", 1 to "Sun")
                                days.forEach { (d, n) ->
                                    OutlinedButton(onClick = { selectedDay = d }, modifier = Modifier.weight(1f)) { Text(n) }
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = {
                                    val t = java.util.Calendar.getInstance()
                                    TimePickerDialog(ctx, { _, h, m -> ttStart = h * 60 + m }, t.get(java.util.Calendar.HOUR_OF_DAY), t.get(java.util.Calendar.MINUTE), true).show()
                                }, modifier = Modifier.weight(1f)) { Text("Start" + (ttStart?.let { " (${it/60}:${"%02d".format(it%60)})" } ?: "")) }
                                OutlinedButton(onClick = {
                                    val t = java.util.Calendar.getInstance()
                                    TimePickerDialog(ctx, { _, h, m -> ttEnd = h * 60 + m }, t.get(java.util.Calendar.HOUR_OF_DAY), t.get(java.util.Calendar.MINUTE), true).show()
                                }, modifier = Modifier.weight(1f)) { Text("End" + (ttEnd?.let { " (${it/60}:${"%02d".format(it%60)})" } ?: "")) }
                            }

                            Button(
                                onClick = {
                                    val clsId = selectedClassId
                                    val s = ttStart
                                    val e = ttEnd
                                    if (subject.isNotBlank() && clsId != null && s != null && e != null) {
                                        scope.launch {
                                            repo.upsertTimetableEntry(
                                                TimetableEntry(
                                                    teacherId = currentUser.id,
                                                    classLocationId = clsId,
                                                    subject = subject,
                                                    dayOfWeek = selectedDay,
                                                    startMinutesOfDay = s,
                                                    endMinutesOfDay = e
                                                )
                                            )
                                            subject = ""; selectedClassId = null; ttStart = null; ttEnd = null; showAddTimetable = false
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = subject.isNotBlank() && selectedClassId != null && ttStart != null && ttEnd != null
                            ) { Text("💾 Save Timetable Entry") }
                        }
                    }
                }
            }

            // Classes List
            if (classes.isNotEmpty()) {
                item {
                    Text(
                        "Saved Classes",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(classes) { classLocation ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    classLocation.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "📍 ${String.format("%.6f", classLocation.latitude)}, ${String.format("%.6f", classLocation.longitude)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "📏 Radius: ${classLocation.radiusMeters}m",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        repo.deleteClassLocation(classLocation.id)
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            } else {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "No classes yet",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Tap the + button to add your first class",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            // Timetable list
            if (timetable.isNotEmpty()) {
                item { Spacer(Modifier.height(8.dp)) }
                item { Text("Timetable", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                items(timetable) { entry ->
                    val cls = classes.find { it.id == entry.classLocationId }
                    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Text(entry.subject, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(4.dp))
                            val dayName = arrayOf("Sun","Mon","Tue","Wed","Thu","Fri","Sat")[entry.dayOfWeek % 7]
                            Text("$dayName ${entry.startMinutesOfDay/60}:${"%02d".format(entry.startMinutesOfDay%60)} - ${entry.endMinutesOfDay/60}:${"%02d".format(entry.endMinutesOfDay%60)}")
                            if (cls != null) {
                                Text("Class: ${cls.name}  •  (${String.format("%.6f", cls.latitude)}, ${String.format("%.6f", cls.longitude)}) r=${cls.radiusMeters}m", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.height(8.dp))
                            // Subject-wise attendance for this timetable entry
                            val attendanceForEntry by repo.observeAttendanceByTimetable(entry.id).collectAsState(initial = emptyList())
                            var showAttendanceManager by remember { mutableStateOf(false) }
                            val presentStudentIds = attendanceForEntry.map { it.studentId }.distinct()
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Present: ${presentStudentIds.size}")
                                TextButton(onClick = { showAttendanceManager = !showAttendanceManager }) { 
                                    Text(if (showAttendanceManager) "Hide" else "Manage Attendance") 
                                }
                            }
                            if (showAttendanceManager) {
                                var allStudents by remember { mutableStateOf<List<User>>(emptyList()) }
                                LaunchedEffect(Unit) {
                                    scope.launch {
                                        repo.observeUsersByRole(UserRole.STUDENT).collect { studentList ->
                                            allStudents = studentList
                                        }
                                    }
                                }
                                if (allStudents.isNotEmpty()) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("Attendance Status:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        allStudents.forEach { student ->
                                            val isPresent = presentStudentIds.contains(student.id)
                                            Row(
                                                Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(student.name, style = MaterialTheme.typography.bodyMedium)
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    OutlinedButton(
                                                        onClick = {
                                                            scope.launch {
                                                                if (isPresent) {
                                                                    // Remove attendance
                                                                    repo.deleteAttendanceForStudentAndEntry(entry.id, student.id)
                                                                } else {
                                                                    // Add attendance
                                                                    val cls = classes.find { it.id == entry.classLocationId }
                                                                    if (cls != null) {
                                                                        repo.insertAttendance(Attendance(
                                                                            classLocationId = entry.classLocationId,
                                                                            studentId = student.id,
                                                                            timetableEntryId = entry.id,
                                                                            timestamp = System.currentTimeMillis(),
                                                                            latitude = cls.latitude,
                                                                            longitude = cls.longitude,
                                                                            accuracyMeters = 0f,
                                                                            isMock = true // Manual entry
                                                                        ))
                                                                    }
                                                                }
                                                            }
                                                        },
                                                        colors = ButtonDefaults.outlinedButtonColors(
                                                            containerColor = if (isPresent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                                        )
                                                    ) {
                                                        Text(if (isPresent) "Present" else "Absent")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { scope.launch { repo.deleteTimetableEntry(entry.id) } }) { Text("Delete") }
                            }
                        }
                    }
                }
            } else {
                item { Spacer(Modifier.height(8.dp)) }
                item {
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("No timetable entries yet")
                            Spacer(Modifier.height(4.dp))
                            OutlinedButton(onClick = { showAddTimetable = true }) { Text("Add timetable entry") }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun StudentScreen(currentUser: com.shyamavagadia.mpc_project_1.data.User, onLogout: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { AttendanceRepository.get(ctx) }
    val classes by repo.observeClasses().collectAsState(initial = emptyList())
    val attendance by repo.observeAttendanceByStudent(currentUser.id).collectAsState(initial = emptyList())
    
    // Get today's timetable entries
    val today = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)
    val todayTimetable by repo.observeTimetableForDay(today).collectAsState(initial = emptyList())

    var currentLocation by remember { mutableStateOf<Location?>(null) }
    var isLocationEnabled by remember { mutableStateOf(false) }
    var activeClass by remember { mutableStateOf<TimetableEntry?>(null) }
    var presenceStartTime by remember { mutableStateOf<Long?>(null) }
    var isTrackingPresence by remember { mutableStateOf(false) }

    val locationClient = remember { LocationServices.getFusedLocationProviderClient(ctx) }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            isLocationEnabled = true
            // Start location updates
            scope.launch {
                try {
                    val location = locationClient.getCurrentLocation(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        CancellationTokenSource().token
                    ).await()
                    currentLocation = location
                } catch (e: Exception) {
                    // Handle error
                }
            }
        }
    }

    // Calculate active class and track presence
    LaunchedEffect(currentLocation, todayTimetable) {
        if (currentLocation != null && todayTimetable.isNotEmpty()) {
            val now = java.util.Calendar.getInstance()
            val currentMinutes = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)
            
            // Find the active class (current time falls within a scheduled class)
            val active = todayTimetable.find { entry ->
                currentMinutes >= entry.startMinutesOfDay && currentMinutes <= entry.endMinutesOfDay
            }
            
            activeClass = active
            
            if (active != null) {
                // Check if student is at the right location for this class
                val classLocation = classes.find { it.id == active.classLocationId }
                if (classLocation != null) {
                    val distance = FloatArray(1)
                    Location.distanceBetween(
                        currentLocation!!.latitude, currentLocation!!.longitude,
                        classLocation.latitude, classLocation.longitude,
                        distance
                    )
                    
                    val isWithinRadius = distance[0] <= classLocation.radiusMeters && currentLocation!!.accuracy <= 30
                    
                    if (isWithinRadius && !isTrackingPresence) {
                        // Start tracking presence
                        presenceStartTime = System.currentTimeMillis()
                        isTrackingPresence = true
                    } else if (!isWithinRadius && isTrackingPresence) {
                        // Stop tracking presence
                        presenceStartTime = null
                        isTrackingPresence = false
                    }
                    
                    // Check if we should mark attendance (25 minutes of continuous presence)
                    if (isTrackingPresence && presenceStartTime != null) {
                        val presenceDuration = System.currentTimeMillis() - presenceStartTime!!
                        val requiredDuration = 25 * 60 * 1000L // 25 minutes in milliseconds
                        
                        if (presenceDuration >= requiredDuration) {
                            // Check if already marked present
                            val alreadyPresent = attendance.any { it.timetableEntryId == active.id }
                            if (!alreadyPresent) {
                                scope.launch {
                                    repo.insertAttendance(
                                        Attendance(
                                            classLocationId = active.classLocationId,
                                            studentId = currentUser.id,
                                            timetableEntryId = active.id,
                                            timestamp = System.currentTimeMillis(),
                                            latitude = currentLocation!!.latitude,
                                            longitude = currentLocation!!.longitude,
                                            accuracyMeters = currentLocation!!.accuracy,
                                            isMock = false
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // No active class, reset tracking
                isTrackingPresence = false
                presenceStartTime = null
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Student Check-in - ${currentUser.name}") },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.Close, contentDescription = "Logout")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Attendance Check-in",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "Check in when you're in class",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // Location status
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.LocationOn, contentDescription = null)
                            Text("Location Status", fontWeight = FontWeight.Medium)
                        }

                        if (!isLocationEnabled) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "❌ Location not enabled",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        when {
                                            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED -> {
                                                isLocationEnabled = true
                                                scope.launch {
                                                    try {
                                                        val location = locationClient.getCurrentLocation(
                                                            Priority.PRIORITY_HIGH_ACCURACY,
                                                            CancellationTokenSource().token
                                                        ).await()
                                                        currentLocation = location
                                                    } catch (e: Exception) {
                                                        // Handle error
                                                    }
                                                }
                                            }
                                            else -> permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Enable Location")
                                }
                            }
                        } else {
                            if (currentLocation != null) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            "✅ Location Active",
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            "Lat: ${String.format("%.6f", currentLocation!!.latitude)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            "Lng: ${String.format("%.6f", currentLocation!!.longitude)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            "Accuracy: ${String.format("%.1f", currentLocation!!.accuracy)}m",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                                    Text("⏳ Getting location...")
                                }
                            }
                        }
                    }
                }
            }

            // Today's Timetable
            if (todayTimetable.isNotEmpty()) {
                item {
                    Text(
                        "Today's Classes",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                items(todayTimetable) { entry ->
                    val classLocation = classes.find { it.id == entry.classLocationId }
                    val isActive = activeClass?.id == entry.id
                    val isAttended = attendance.any { it.timetableEntryId == entry.id }
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        entry.subject,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "${entry.startMinutesOfDay/60}:${"%02d".format(entry.startMinutesOfDay%60)} - ${entry.endMinutesOfDay/60}:${"%02d".format(entry.endMinutesOfDay%60)}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    if (classLocation != null) {
                                        Text(
                                            "📍 ${classLocation.name}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                
                                if (isAttended) {
                                    Text(
                                        "✅ Present",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                } else if (isActive) {
                                    if (isTrackingPresence && presenceStartTime != null) {
                                        val elapsed = (System.currentTimeMillis() - presenceStartTime!!) / 1000 / 60 // minutes
                                        val remaining = 25 - elapsed
                                        Text(
                                            "⏳ ${remaining.toInt()}min left",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    } else {
                                        Text(
                                            "🕐 Active Now",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            
                            if (isActive && !isAttended) {
                                val classLocation = classes.find { it.id == entry.classLocationId }
                                if (classLocation != null && currentLocation != null) {
                                    val distance = FloatArray(1)
                                    Location.distanceBetween(
                                        currentLocation!!.latitude, currentLocation!!.longitude,
                                        classLocation.latitude, classLocation.longitude,
                                        distance
                                    )
                                    
                                    val isWithinRadius = distance[0] <= classLocation.radiusMeters && currentLocation!!.accuracy <= 30
                                    val statusMessage = when {
                                        isWithinRadius -> "✅ You're in the right location"
                                        distance[0] > classLocation.radiusMeters -> "❌ Too far from class (${String.format("%.0f", distance[0])}m away)"
                                        currentLocation!!.accuracy > 30 -> "❌ GPS accuracy too low"
                                        else -> "❌ Location not available"
                                    }
                                    
                                    Text(
                                        statusMessage,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isWithinRadius) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "No classes scheduled for today",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Attendance history
            if (attendance.isNotEmpty()) {
                item {
                    Text(
                        "Recent Check-ins",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(attendance.take(5)) { record ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                val classInfo = classes.find { it.id == record.classLocationId }
                                Text(
                                    classInfo?.name ?: "Unknown Class",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    "🕐 ${java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(record.timestamp))}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "📍 ${String.format("%.6f", record.latitude)}, ${String.format("%.6f", record.longitude)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "No check-ins yet",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Check in to a class to see your history",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }
    }
}


