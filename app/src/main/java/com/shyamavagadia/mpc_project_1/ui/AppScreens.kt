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
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

enum class Role { Teacher, Student }

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AppRoot() {
    var role by remember { mutableStateOf(Role.Teacher) }
    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.height(56.dp),
                title = { Text("Attendance - ${role.name}", style = MaterialTheme.typography.titleMedium) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = role == Role.Teacher,
                    onClick = { role = Role.Teacher },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    label = { Text("Teacher") }
                )
                NavigationBarItem(
                    selected = role == Role.Student,
                    onClick = { role = Role.Student },
                    icon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                    label = { Text("Student") }
                )
            }
        }
    ) { inner ->
        Box(Modifier.padding(inner)) {
            when (role) {
                Role.Teacher -> TeacherScreen()
                Role.Student -> StudentScreen()
            }
        }
    }
}

class TeacherViewModel(private val repo: AttendanceRepository) : ViewModel() {
    fun classes() = repo.observeClasses()
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TeacherScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { AttendanceRepository.get(ctx) }
    val vm = viewModel<TeacherViewModel>(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return TeacherViewModel(repo) as T
        }
    })
    val classes by vm.classes().collectAsState(initial = emptyList())

    var name by remember { mutableStateOf("") }
    var capturedLocation by remember { mutableStateOf<Location?>(null) }
    var radius by remember { mutableStateOf("40") }
    var start by remember { mutableStateOf<Int?>(null) }
    var end by remember { mutableStateOf<Int?>(null) }
    var isCapturing by remember { mutableStateOf(false) }
    var showAddForm by remember { mutableStateOf(false) }

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
                modifier = Modifier.height(56.dp),
                title = { Text("Teacher Dashboard", style = MaterialTheme.typography.titleMedium) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddForm = true }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Class")
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

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        val t = java.util.Calendar.getInstance()
                                        TimePickerDialog(ctx, { _, h, m -> start = h * 60 + m }, t.get(java.util.Calendar.HOUR_OF_DAY), t.get(java.util.Calendar.MINUTE), true).show()
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(modifier = Modifier.size(4.dp))
                                    Text("Start" + (start?.let { " (${it/60}:${"%02d".format(it%60)})" } ?: ""))
                                }
                                OutlinedButton(
                                    onClick = {
                                        val t = java.util.Calendar.getInstance()
                                        TimePickerDialog(ctx, { _, h, m -> end = h * 60 + m }, t.get(java.util.Calendar.HOUR_OF_DAY), t.get(java.util.Calendar.MINUTE), true).show()
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(modifier = Modifier.size(4.dp))
                                    Text("End" + (end?.let { " (${it/60}:${"%02d".format(it%60)})" } ?: ""))
                                }
                            }

                            Button(
                                onClick = {
                                    val radI = radius.toIntOrNull() ?: 40
                                    val s = start
                                    val e = end
                                    if (name.isNotBlank() && capturedLocation != null && s != null && e != null) {
                                        scope.launch {
                                            val id = repo.upsertClassLocation(
                                                ClassLocation(
                                                    name = name,
                                                    latitude = capturedLocation!!.latitude,
                                                    longitude = capturedLocation!!.longitude,
                                                    radiusMeters = radI
                                                )
                                            )
                                            repo.setTimeWindows(id, listOf(TimeWindow(classLocationId = id, startMinutesOfDay = s, endMinutesOfDay = e)))
                                            name = ""; capturedLocation = null; radius = "40"; start = null; end = null; showAddForm = false
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = name.isNotBlank() && capturedLocation != null && start != null && end != null
                            ) {
                                Text("Save class location")
                            }
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
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun StudentScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { AttendanceRepository.get(ctx) }
    val classes by repo.observeClasses().collectAsState(initial = emptyList())
    val attendance by repo.observeAttendance().collectAsState(initial = emptyList())

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

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.height(56.dp),
                title = { Text("Student Check-in", style = MaterialTheme.typography.titleMedium) },
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
            item {
                Button(
                    onClick = {
                        if (canCheckIn && currentLocation != null && nearestClass != null) {
                            scope.launch {
                                repo.insertAttendance(
                                    com.shyamavagadia.mpc_project_1.data.Attendance(
                                        classLocationId = nearestClass!!.id,
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
}


