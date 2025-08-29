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

enum class AuthMode { LOGIN, SIGNUP }

class AuthViewModel(private val repo: AttendanceRepository) : ViewModel() {
    suspend fun login(username: String, password: String): User? = 
        repo.authenticateUser(username, password)
    
    suspend fun signup(user: User): Long = repo.registerUser(user)
    
    suspend fun checkUsernameExists(username: String): Boolean = 
        repo.getUserByUsername(username) != null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(onAuthSuccess: (User) -> Unit) {
    var authMode by remember { mutableStateOf(AuthMode.LOGIN) }
    var username by remember { mutableStateOf("") }
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
                    // Username field
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth()
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

                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Email") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Role selection
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { selectedRole = UserRole.TEACHER },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Teacher")
                            }
                            OutlinedButton(
                                onClick = { selectedRole = UserRole.STUDENT },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Student")
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
                                        val user = vm.login(username, password)
                                        if (user != null) {
                                            onAuthSuccess(user)
                                        } else {
                                            errorMessage = "Invalid username or password"
                                        }
                                    } else {
                                        if (password != confirmPassword) {
                                            errorMessage = "Passwords do not match"
                                            return@launch
                                        }
                                        
                                        val user = User(
                                            username = username,
                                            password = password,
                                            role = selectedRole,
                                            name = name,
                                            email = email
                                        )
                                        
                                        val userId = vm.signup(user)
                                        if (userId > 0) {
                                            onAuthSuccess(user.copy(id = userId))
                                        } else {
                                            errorMessage = "Failed to create account"
                                        }
                                    }
                                } catch (e: Exception) {
                                    errorMessage = "An error occurred: ${e.message}"
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
