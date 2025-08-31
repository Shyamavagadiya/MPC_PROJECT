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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.shyamavagadia.mpc_project_1.data.SessionManager

// Bottom navigation tabs (top-level to avoid scoping issues)
enum class TeacherTab(val title: String) { Dashboard("Dashboard"), Timetable("Timetable") }
enum class StudentTab(val title: String) { Home("Home"), History("History") }

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AppRoot() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { AttendanceRepository.get(ctx) }
    val session = remember { SessionManager(ctx) }

    var currentUser by remember { mutableStateOf<com.shyamavagadia.mpc_project_1.data.User?>(null) }

    LaunchedEffect(Unit) {
        // Try to restore session
        val existingId = session.getLoggedInUserId()
        if (existingId != null) {
            currentUser = repo.getUserById(existingId)
        } else {
            // Ensure sample data exists and login default teacher for demo
            repo.createSampleData()
            val teacher = repo.getUserByUsername("teacher")
            if (teacher != null) {
                session.setLoggedInUserId(teacher.id)
                currentUser = teacher
            } else {
                // fallback: try student
                val student = repo.getUserByUsername("student")
                if (student != null) {
                    session.setLoggedInUserId(student.id)
                    currentUser = student
                }
            }
        }
    }

    val onLogout: () -> Unit = {
        session.clear()
        currentUser = null
    }

    var teacherTab by remember { mutableStateOf(TeacherTab.Dashboard) }
    var studentTab by remember { mutableStateOf(StudentTab.Home) }

    // Hoisted state to avoid duplicate FABs
    var showAddClassForm by remember { mutableStateOf(false) }
    var showAddTimetableForm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.height(56.dp),
                title = {
                    val title = when (currentUser?.role) {
                        com.shyamavagadia.mpc_project_1.data.UserRole.TEACHER -> "Attendance - Teacher"
                        com.shyamavagadia.mpc_project_1.data.UserRole.STUDENT -> "Attendance - Student"
                        else -> "Attendance"
                    }
                    Text(title, style = MaterialTheme.typography.titleMedium)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        bottomBar = {
            val user = currentUser
            if (user != null) {
                when (user.role) {
                    com.shyamavagadia.mpc_project_1.data.UserRole.TEACHER -> {
                        NavigationBar {
                            NavigationBarItem(
                                selected = teacherTab == TeacherTab.Dashboard,
                                onClick = { teacherTab = TeacherTab.Dashboard },
                                icon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                                label = { Text(TeacherTab.Dashboard.title) }
                            )
                            NavigationBarItem(
                                selected = teacherTab == TeacherTab.Timetable,
                                onClick = { teacherTab = TeacherTab.Timetable },
                                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                                label = { Text(TeacherTab.Timetable.title) }
                            )
                        }
                    }
                    com.shyamavagadia.mpc_project_1.data.UserRole.STUDENT -> {
                        NavigationBar {
                            NavigationBarItem(
                                selected = studentTab == StudentTab.Home,
                                onClick = { studentTab = StudentTab.Home },
                                icon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                                label = { Text(StudentTab.Home.title) }
                            )
                            NavigationBarItem(
                                selected = studentTab == StudentTab.History,
                                onClick = { studentTab = StudentTab.History },
                                icon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                label = { Text(StudentTab.History.title) }
                            )
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            val user = currentUser
            if (user != null) {
                when (user.role) {
                    com.shyamavagadia.mpc_project_1.data.UserRole.TEACHER -> {
                        FloatingActionButton(onClick = {
                            if (teacherTab == TeacherTab.Dashboard) {
                                showAddClassForm = true
                            } else {
                                showAddTimetableForm = true
                            }
                        }) {
                            Icon(Icons.Default.Add, contentDescription = "Add")
                        }
                    }
                    else -> {}
                }
            }
        }
    ) { inner ->
        val user = currentUser
        if (user == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("Loading session...")
            }
        } else {
            when (user.role) {
                com.shyamavagadia.mpc_project_1.data.UserRole.TEACHER ->
                    TeacherScreen(
                        currentUser = user,
                        selectedTab = teacherTab,
                        showAddForm = showAddClassForm,
                        onShowAddFormChange = { showAddClassForm = it },
                        showAddTimetable = showAddTimetableForm,
                        onShowAddTimetableChange = { showAddTimetableForm = it },
                        modifier = Modifier.padding(inner)
                    )
                com.shyamavagadia.mpc_project_1.data.UserRole.STUDENT ->
                    StudentScreen(
                        currentUser = user,
                        selectedTab = studentTab,
                        modifier = Modifier.padding(inner)
                    )
            }
        }
    }
}

class TeacherViewModel(private val repo: AttendanceRepository, private val teacherId: Long) : ViewModel() {
    fun classes() = repo.observeClassesByTeacher(teacherId)
    fun timetable() = repo.observeTimetableForTeacher(teacherId)
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TeacherScreen(
    currentUser: com.shyamavagadia.mpc_project_1.data.User,
    selectedTab: TeacherTab,
    showAddForm: Boolean,
    onShowAddFormChange: (Boolean) -> Unit,
    showAddTimetable: Boolean,
    onShowAddTimetableChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
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
    // State is hoisted to AppRoot to control via single FAB and avoid duplicates
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

    LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .then(modifier)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header (only for Dashboard tab)
            if (selectedTab == TeacherTab.Dashboard) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
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
                                IconButton(onClick = { onShowAddFormChange(false) }) {
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
                                                    "Location captured",
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
                                            "No location captured yet",
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
                                        } else {
                                            Icon(Icons.Default.LocationOn, contentDescription = null)
                                            Spacer(modifier = Modifier.size(8.dp))
                                        }
                                        Text(if (isCapturing) "Capturing..." else "Capture current location")
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
                                            name = ""; capturedLocation = null; radius = "40"; start = null; end = null; onShowAddFormChange(false)
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = name.isNotBlank() && capturedLocation != null
                            ) {
                                Text("Save class location")
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
                                IconButton(onClick = { onShowAddTimetableChange(false) }) { Icon(Icons.Default.Close, contentDescription = "Close") }
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
                                            subject = ""; selectedClassId = null; ttStart = null; ttEnd = null; onShowAddTimetableChange(false)
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

            // Classes List (Dashboard tab)
            if (selectedTab == TeacherTab.Dashboard && classes.isNotEmpty()) {
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
                                    "Location: ${String.format("%.6f", classLocation.latitude)}, ${String.format("%.6f", classLocation.longitude)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "Radius: ${classLocation.radiusMeters}m",
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
            } else if (selectedTab == TeacherTab.Dashboard) {
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

            // Timetable list (Timetable tab)
            if (selectedTab == TeacherTab.Timetable && timetable.isNotEmpty()) {
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
                            val attendanceForEntry by remember(entry.id) { repo.observeAttendanceByTimetable(entry.id) }.collectAsState(initial = emptyList())
                            var showList by remember { mutableStateOf(false) }
                            val uniqueStudentIds = attendanceForEntry.map { it.studentId }.distinct()
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Attendance: ${uniqueStudentIds.size}")
                                TextButton(onClick = { showList = !showList }) { Text(if (showList) "Hide" else "View") }
                            }
                            if (showList && uniqueStudentIds.isNotEmpty()) {
                                val scopeLocal = rememberCoroutineScope()
                                var names by remember(entry.id, uniqueStudentIds) { mutableStateOf<List<String>>(emptyList()) }
                                LaunchedEffect(entry.id, uniqueStudentIds) {
                                    val fetched = mutableListOf<String>()
                                    uniqueStudentIds.forEach { sid ->
                                        repo.getUserById(sid)?.let { fetched.add(it.name) }
                                    }
                                    names = fetched
                                }
                                if (names.isNotEmpty()) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        names.forEach { nm -> Text("• $nm", style = MaterialTheme.typography.bodySmall) }
                                    }
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { scope.launch { repo.deleteTimetableEntry(entry.id) } }) { Text("Delete") }
                            }
                        }
                    }
                }
            } else if (selectedTab == TeacherTab.Timetable) {
                item { Spacer(Modifier.height(8.dp)) }
                item {
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("No timetable entries yet")
                            Spacer(Modifier.height(4.dp))
                            OutlinedButton(onClick = { onShowAddTimetableChange(true) }) { Text("Add timetable entry") }
                        }
                    }
                }
            }
        }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun StudentScreen(
    currentUser: com.shyamavagadia.mpc_project_1.data.User,
    selectedTab: StudentTab,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { AttendanceRepository.get(ctx) }
    val classes by repo.observeClasses().collectAsState(initial = emptyList())
    val attendance by repo.observeAttendanceByStudent(currentUser.id).collectAsState(initial = emptyList())

    var currentLocation by remember { mutableStateOf<Location?>(null) }
    var isLocationEnabled by remember { mutableStateOf(false) }
    var nearestClass by remember { mutableStateOf<ClassLocation?>(null) }
    var distanceToNearest by remember { mutableStateOf<Float?>(null) }
    var canCheckIn by remember { mutableStateOf(false) }
    var checkInMessage by remember { mutableStateOf("") }

    // Persisted location enabled preference
    val prefs = remember { ctx.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }

    val locationClient = remember { LocationServices.getFusedLocationProviderClient(ctx) }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            isLocationEnabled = true
            prefs.edit().putBoolean("location_enabled", true).apply()
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

    // Restore persisted location enabled state on enter
    LaunchedEffect(Unit) {
        val saved = prefs.getBoolean("location_enabled", false)
        val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (saved && granted) {
            isLocationEnabled = true
            try {
                val location = locationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    CancellationTokenSource().token
                ).await()
                currentLocation = location
            } catch (_: Exception) {}
        }
    }

    // Calculate nearest class and check-in eligibility
    LaunchedEffect(currentLocation, classes) {
        if (currentLocation != null && classes.isNotEmpty()) {
            var minDistance = Float.MAX_VALUE
            var nearest: ClassLocation? = null
            
            for (classLocation in classes) {
                val distance = FloatArray(1)
                Location.distanceBetween(
                    currentLocation!!.latitude, currentLocation!!.longitude,
                    classLocation.latitude, classLocation.longitude,
                    distance
                )
                
                if (distance[0] < minDistance) {
                    minDistance = distance[0]
                    nearest = classLocation
                }
            }
            
            nearestClass = nearest
            distanceToNearest = minDistance
            
            // Check if can check in (within radius and time window)
            if (nearest != null) {
                val now = java.util.Calendar.getInstance()
                val currentMinutes = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)
                
                // For now, just check distance. Time window check will be added when we implement TimeWindow queries
                canCheckIn = minDistance <= nearest.radiusMeters && currentLocation!!.accuracy <= 30
                checkInMessage = when {
                    minDistance > nearest.radiusMeters -> "Too far from ${nearest.name} (${String.format("%.0f", minDistance)}m away)"
                    currentLocation!!.accuracy > 30 -> "GPS accuracy too low (${String.format("%.0f", currentLocation!!.accuracy)}m)"
                    else -> "✅ Can check in to ${nearest.name}"
                }
            }
        }
    }

    LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .then(modifier)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header only on Home tab
            if (selectedTab == StudentTab.Home) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
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
                                    "Location not enabled",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        when {
                                            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED -> {
                                                isLocationEnabled = true
                                                prefs.edit().putBoolean("location_enabled", true).apply()
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
                                            "Location active",
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
                                        Spacer(modifier = Modifier.height(12.dp))
                                        OutlinedButton(
                                            onClick = {
                                                // Disable and clear state
                                                isLocationEnabled = false
                                                prefs.edit().putBoolean("location_enabled", false).apply()
                                                currentLocation = null
                                                nearestClass = null
                                                distanceToNearest = null
                                                canCheckIn = false
                                                checkInMessage = ""
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Disable Location")
                                        }
                                    }
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                                    Text("Getting location...")
                                }
                            }
                        }
                    }
                }
            }

            // Nearest class info
            if (nearestClass != null && distanceToNearest != null) {
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
                                Text("Nearest class", fontWeight = FontWeight.Medium)
                            }

                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        nearestClass!!.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Distance: ${String.format("%.0f", distanceToNearest!!)}m")
                                    Text("Required: ≤${nearestClass!!.radiusMeters}m")
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        checkInMessage,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (canCheckIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Check-in button
            if (selectedTab == StudentTab.Home) item {
                Button(
                    onClick = {
                        if (canCheckIn && currentLocation != null && nearestClass != null) {
                            scope.launch {
                                repo.insertAttendance(
                                    com.shyamavagadia.mpc_project_1.data.Attendance(
                                        classLocationId = nearestClass!!.id,
                                        studentId = currentUser.id,
                                        timestamp = System.currentTimeMillis(),
                                        latitude = currentLocation!!.latitude,
                                        longitude = currentLocation!!.longitude,
                                        accuracyMeters = currentLocation!!.accuracy,
                                        isMock = false // We'll add mock detection later
                                    )
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = canCheckIn
                ) {
                    Text("Check in")
                }
            }

            // Attendance history (History tab)
            if (selectedTab == StudentTab.History && attendance.isNotEmpty()) {
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
                                    "Time: ${java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(record.timestamp))}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "Location: ${String.format("%.6f", record.latitude)}, ${String.format("%.6f", record.longitude)}",
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

