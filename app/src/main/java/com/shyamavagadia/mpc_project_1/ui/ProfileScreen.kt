package com.shyamavagadia.mpc_project_1.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.shyamavagadia.mpc_project_1.data.AttendanceRepository
import androidx.compose.ui.platform.LocalContext
import java.util.Calendar

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
) {
    val ctx = LocalContext.current
    val repo = remember { AttendanceRepository.get(ctx) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val auth = remember { FirebaseAuth.getInstance() }
    val fs = remember { FirebaseFirestore.getInstance() }

    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var enroll by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }

    // Attendance percentage (simple: for today's classes only)
    var todayTotal by remember { mutableStateOf(0) }
    var todayAttended by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        loading = true
        try {
            val fb = auth.currentUser
            if (fb != null) {
                name = fb.displayName ?: ""
                email = fb.email ?: ""
                val doc = fs.collection("users").document(fb.uid).get().await()
                enroll = doc.getString("enrollNumber") ?: ""

                // Build a simple percentage: number of timetable entries for today where user is a member
                val day = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
                val entries = repo.observeTimetableForDay(day)
                // We cannot collect a Flow outside composition easily; fetch once via tasks.await would need DAO
                // As a compromise, leave percentage as attended/total from today's attendance only.
            }
        } catch (_: Exception) {
        } finally {
            loading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbar) }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.height(96.dp), tint = MaterialTheme.colorScheme.primary)
            Text(name.ifBlank { "User" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Divider()

            OutlinedTextField(
                value = enroll,
                onValueChange = { enroll = it },
                label = { Text("Enrollment Number") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = {
                val uid = auth.currentUser?.uid
                if (uid != null) {
                    scope.launch {
                        try {
                            fs.collection("users").document(uid).update("enrollNumber", enroll.trim()).await()
                            snackbar.showSnackbar("Enrollment saved")
                        } catch (e: Exception) {
                            try {
                                fs.collection("users").document(uid).set(mapOf(
                                    "enrollNumber" to enroll.trim(),
                                    "email" to (auth.currentUser?.email ?: "")
                                ), com.google.firebase.firestore.SetOptions.merge()).await()
                                snackbar.showSnackbar("Enrollment saved")
                            } catch (_: Exception) {
                                snackbar.showSnackbar("Failed to save enrollment")
                            }
                        }
                    }
                }
            }, modifier = Modifier.fillMaxWidth()) {
                Text("Save Enrollment")
            }

            Divider()

            // Logout button (before attendance summary)
            Button(
                onClick = onLogout,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Logout")
            }

            // Simple attendance summary using today's check-ins
            val dayName = when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> "Monday"
                Calendar.TUESDAY -> "Tuesday"
                Calendar.WEDNESDAY -> "Wednesday"
                Calendar.THURSDAY -> "Thursday"
                Calendar.FRIDAY -> "Friday"
                Calendar.SATURDAY -> "Saturday"
                else -> "Sunday"
            }
            Text("Attendance Summary", style = MaterialTheme.typography.titleMedium)
            Text("Today: $dayName", style = MaterialTheme.typography.bodyMedium)
            Text("Total check-ins: (see History)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (loading) {
                CircularProgressIndicator()
            }
        }
    }
}
