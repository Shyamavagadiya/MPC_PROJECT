package com.shyamavagadia.mpc_project_1

import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shyamavagadia.mpc_project_1.ui.theme.MPC_project_1Theme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MPC_project_1Theme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    BasicTestScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun BasicTestScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("attendance_prefs", Context.MODE_PRIVATE) }
    var count by remember { mutableStateOf(prefs.getInt("count", 0)) }
    var lastTs by remember { mutableStateOf(prefs.getLong("last_ts", 0L)) }

    val formatter = remember {
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault())
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "Basic Test Ready")
        Text(text = "Saved count: $count")
        Text(text = if (lastTs == 0L) "Last timestamp: -" else "Last timestamp: ${formatter.format(Instant.ofEpochMilli(lastTs))}")

        Button(
            onClick = {
                val newCount = count + 1
                val now = System.currentTimeMillis()
                prefs.edit().putInt("count", newCount).putLong("last_ts", now).apply()
                count = newCount
                lastTs = now
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Save Test Attendance")
        }

        Button(
            onClick = {
                prefs.edit().clear().apply()
                count = 0
                lastTs = 0L
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Reset Data")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun BasicTestScreenPreview() {
    MPC_project_1Theme {
        BasicTestScreen()
    }
}