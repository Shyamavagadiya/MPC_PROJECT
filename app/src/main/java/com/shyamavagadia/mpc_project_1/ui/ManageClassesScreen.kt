package com.shyamavagadia.mpc_project_1.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun ManageClassesScreen(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val auth = remember<FirebaseAuth> { FirebaseAuth.getInstance() }
    val fs = remember<FirebaseFirestore> { FirebaseFirestore.getInstance() }

    var className by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<StudentItem>>(emptyList()) }
    var selected by remember { mutableStateOf<MutableSet<String>>(mutableSetOf()) } // store user document IDs
    var saving by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Manage Classes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = className,
                    onValueChange = { className = it },
                    label = { Text("Class Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Search students by name/email/enroll") },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        scope.launch {
                            try {
                                val q = query.trim()
                                if (q.isNotBlank()) {
                                    // Search Firestore users where role == STUDENT and matching fields contain query (client-side filter of small result set)
                                    val snap = fs.collection("users").whereEqualTo("role", "STUDENT").get().await()
                                    val list = snap.documents.mapNotNull { d ->
                                        val name = d.getString("name") ?: ""
                                        val email = d.getString("email") ?: ""
                                        val enroll = d.getString("enrollNumber") ?: ""
                                        if (name.contains(q, ignoreCase = true) || email.contains(q, ignoreCase = true) || enroll.contains(q, ignoreCase = true)) {
                                            StudentItem(d.id, name.ifBlank { email }, email, enroll)
                                        } else null
                                    }
                                    searchResults = list
                                } else {
                                    searchResults = emptyList()
                                }
                            } catch (_: Exception) { searchResults = emptyList() }
                        }
                    }) { Text("Search") }
                }

                if (searchResults.isNotEmpty()) {
                    Divider()
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 280.dp)) {
                        items(searchResults) { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(item.displayName, style = MaterialTheme.typography.titleSmall)
                                    Text(item.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (item.enroll.isNotBlank()) Text("Enroll: ${item.enroll}", style = MaterialTheme.typography.bodySmall)
                                }
                                Checkbox(checked = selected.contains(item.id), onCheckedChange = { checked ->
                                    if (checked) selected.add(item.id) else selected.remove(item.id)
                                })
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = {
                        // cancel: clear fields
                        className = ""; query = ""; searchResults = emptyList(); selected.clear()
                    }, modifier = Modifier.weight(1f)) { Text("Cancel") }
                    Button(onClick = {
                        val uid = auth.currentUser?.uid
                        if (uid != null && className.isNotBlank()) {
                            saving = true
                            scope.launch {
                                try {
                                    val doc = fs.collection("classes").document()
                                    doc.set(
                                        mapOf(
                                            "name" to className.trim(),
                                            "teacherUid" to uid,
                                            "studentIds" to selected.toList(),
                                            "createdAt" to com.google.firebase.Timestamp.now()
                                        )
                                    ).await()
                                    // clear after save
                                    className = ""; query = ""; searchResults = emptyList(); selected.clear()
                                } catch (_: Exception) { } finally { saving = false }
                            }
                        }
                    }, modifier = Modifier.weight(1f), enabled = !saving && className.isNotBlank()) { Text("Create") }
                }
            }
        }
    }
}

data class StudentItem(
    val id: String,
    val displayName: String,
    val email: String,
    val enroll: String
)
