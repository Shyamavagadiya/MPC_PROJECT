package com.shyamavagadia.mpc_project_1.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shyamavagadia.mpc_project_1.data.AttendanceRepository
import com.shyamavagadia.mpc_project_1.data.User
import com.shyamavagadia.mpc_project_1.data.UserRole
import kotlinx.coroutines.launch
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException

enum class AuthMode { LOGIN, SIGNUP }

class AuthViewModel(private val repo: AttendanceRepository) : ViewModel() {
    // Local repo methods retained for sample data, but primary auth is via Firebase
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(onAuthSuccess: (User) -> Unit) {
    var authMode by remember { mutableStateOf(AuthMode.LOGIN) }
    var username by remember { mutableStateOf("") } // For signup/display only
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf(UserRole.STUDENT) }
    var showPassword by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { AttendanceRepository.get(ctx) }
    val vm = viewModel<AuthViewModel>(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return AuthViewModel(repo) as T
        }
    })
    val firebaseAuth = remember { FirebaseAuth.getInstance() }
    val firestore = remember { FirebaseFirestore.getInstance() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (authMode == AuthMode.LOGIN) "Login" else "Sign Up") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        if (authMode == AuthMode.LOGIN) "Welcome Back!" else "Create Account",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Auth Form
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Email field (used for Firebase auth)
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)
                    )

                    // Password field
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        trailingIcon = {
                            TextButton(onClick = { showPassword = !showPassword }) {
                                Text(if (showPassword) "Hide" else "Show")
                            }
                        },
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Signup specific fields
                    if (authMode == AuthMode.SIGNUP) {
                        // Username (optional, stored in Firestore)
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("Username") },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            label = { Text("Confirm Password") },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Full Name") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Role selection (single choice)
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text("Role", style = MaterialTheme.typography.labelLarge)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(
                                        selected = selectedRole == UserRole.TEACHER,
                                        onClick = { selectedRole = UserRole.TEACHER },
                                        enabled = !isLoading
                                    )
                                    Text("Teacher")
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(
                                        selected = selectedRole == UserRole.STUDENT,
                                        onClick = { selectedRole = UserRole.STUDENT },
                                        enabled = !isLoading
                                    )
                                    Text("Student")
                                }
                            }
                        }
                    }

                    // Error message
                    if (errorMessage.isNotEmpty()) {
                        Text(
                            errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    // Submit button
                    Button(
                        onClick = {
                            scope.launch {
                                isLoading = true
                                errorMessage = ""
                                
                                try {
                                    if (authMode == AuthMode.LOGIN) {
                                        if (email.isBlank() || password.isBlank()) {
                                            errorMessage = "Email and password are required"
                                        } else {
                                            val result = firebaseAuth.signInWithEmailAndPassword(email.trim(), password).await()
                                            val uid = result.user?.uid ?: throw IllegalStateException("No user ID")
                                            var doc = firestore.collection("users").document(uid).get().await()
                                            if (!doc.exists()) {
                                                // Create minimal profile with default STUDENT role
                                                val data = mapOf(
                                                    "email" to (result.user?.email ?: email.trim()),
                                                    "name" to (result.user?.displayName ?: name),
                                                    "username" to username,
                                                    "role" to UserRole.STUDENT.name,
                                                    "createdAt" to com.google.firebase.Timestamp.now()
                                                )
                                                firestore.collection("users").document(uid).set(data).await()
                                                doc = firestore.collection("users").document(uid).get().await()
                                            }
                                            val roleStr = (doc.getString("role") ?: "STUDENT").uppercase()
                                            val role = if (roleStr == "TEACHER") UserRole.TEACHER else UserRole.STUDENT
                                            val displayName = doc.getString("name") ?: (result.user?.displayName ?: name)
                                            val uname = doc.getString("username") ?: username
                                            onAuthSuccess(
                                                User(
                                                    id = 0L,
                                                    username = uname,
                                                    password = "",
                                                    role = role,
                                                    name = displayName,
                                                    email = email.trim()
                                                )
                                            )
                                        }
                                    } else {
                                        if (password != confirmPassword) {
                                            errorMessage = "Passwords do not match"
                                            isLoading = false
                                            return@launch
                                        }
                                        if (email.isBlank()) {
                                            errorMessage = "Email is required"
                                            isLoading = false
                                            return@launch
                                        }
                                        val result = firebaseAuth.createUserWithEmailAndPassword(email.trim(), password).await()
                                        val uid = result.user?.uid ?: throw IllegalStateException("No user ID")
                                        // Best-effort profile write; proceed regardless of result
                                        try {
                                            val data = mapOf(
                                                "email" to email.trim(),
                                                "name" to name,
                                                "username" to username,
                                                "role" to selectedRole.name,
                                                "createdAt" to com.google.firebase.Timestamp.now()
                                            )
                                            firestore.collection("users").document(uid).set(data).await()
                                        } catch (_: Exception) { /* proceed even if profile write fails */ }
                                        onAuthSuccess(
                                            User(
                                                id = 0L,
                                                username = username,
                                                password = "",
                                                role = selectedRole,
                                                name = name,
                                                email = email.trim()
                                            )
                                        )
                                    }
                                } catch (e: Exception) {
                                    errorMessage = when (e) {
                                        is FirebaseAuthInvalidCredentialsException, is FirebaseAuthInvalidUserException ->
                                            "Invalid email or password"
                                        is FirebaseAuthUserCollisionException -> "Email already in use"
                                        is FirebaseAuthWeakPasswordException -> "Password is too weak"
                                        else -> "An error occurred: ${e.message}"
                                    }
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                        }
                        Text(if (authMode == AuthMode.LOGIN) "Login" else "Sign Up")
                    }
                }
            }

            // Toggle between login and signup
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    if (authMode == AuthMode.LOGIN) "Don't have an account? " else "Already have an account? "
                )
                TextButton(
                    onClick = {
                        authMode = if (authMode == AuthMode.LOGIN) AuthMode.SIGNUP else AuthMode.LOGIN
                        errorMessage = ""
                        isLoading = false
                    }
                ) {
                    Text(
                        if (authMode == AuthMode.LOGIN) "Sign Up" else "Login",
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Sample data button for testing
            if (authMode == AuthMode.LOGIN) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            try {
                                repo.createSampleData()
                                errorMessage = "Sample data created! Use teacher/password or student/password to login."
                            } catch (e: Exception) {
                                errorMessage = "Failed to create sample data: ${e.message}"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Create Sample Data (for testing)")
                }
            }
        }
    }
}
