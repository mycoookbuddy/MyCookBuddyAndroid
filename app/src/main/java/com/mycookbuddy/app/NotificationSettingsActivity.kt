package com.mycookbuddy.app

import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FirebaseFirestore
import com.mycookbuddy.app.ui.theme.MyApplicationTheme
import java.util.*

class NotificationSettingsActivity : ComponentActivity() {
    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val userEmail = intent.getStringExtra("USER_EMAIL") ?: return

        setContent {
            MyApplicationTheme {
                NotificationSettingsScreen(userEmail)
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun NotificationSettingsScreen(userEmail: String) {
        var notifications by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
        val context = this@NotificationSettingsActivity

        LaunchedEffect(Unit) {
            firestore.collection("users").document(userEmail).get()
                .addOnSuccessListener { doc ->
                    val fetchedList = doc.get("notifications") as? List<Map<String, Any>> ?: emptyList()
                    notifications = fetchedList
                }
                .addOnFailureListener {
                    Toast.makeText(context, "Failed to load notifications", Toast.LENGTH_SHORT).show()
                }
        }

        Scaffold(topBar = {
            TopAppBar(
                title = { Text("Edit Notification", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { (context as? ComponentActivity)?.finish() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                modifier = Modifier.background(
                    Brush.horizontalGradient(listOf(Color(0xFF00ACC1), Color(0xFF26C6DA)))
                ),
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        }) { inner ->
            Column(
                modifier = Modifier
                    .padding(inner)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                notifications.forEachIndexed { index, notif ->
                    var enabled by remember { mutableStateOf(notif["status"] as? Boolean ?: true) }
                    var time by remember { mutableStateOf(notif["timestamp"] as? String ?: "") }
                    val localContext = LocalContext.current
                    val calendar = Calendar.getInstance()

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        elevation = CardDefaults.cardElevation(4.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Alarm, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = notif["mealType"].toString(),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(Modifier.weight(1f))
                                Switch(
                                    checked = enabled,
                                    onCheckedChange = {
                                        enabled = it
                                        notifications = notifications.toMutableList().also {
                                            it[index] = it[index].toMutableMap().apply {
                                                this["status"] = enabled
                                            }
                                        }
                                    }
                                )
                            }

                            Spacer(Modifier.height(12.dp))

                            // Large time picker box
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(80.dp)
                                    .background(
                                        color = Color.White,
                                        shape = MaterialTheme.shapes.medium
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = Color(0xFFB0BEC5),
                                        shape = MaterialTheme.shapes.medium
                                    )
                                    .clickable {
                                        val (initialHour, initialMinute) = if (time.matches(Regex("""\d{1,2}:\d{2} (AM|PM)"""))) {
                                            val parts = time.split(":", " ")
                                            val rawHour = parts[0].toInt()
                                            val minute = parts[1].toInt()
                                            val amPm = parts[2]
                                            val hour = if (amPm == "PM" && rawHour < 12) rawHour + 12 else if (amPm == "AM" && rawHour == 12) 0 else rawHour
                                            hour to minute
                                        } else {
                                            calendar.get(Calendar.HOUR_OF_DAY) to calendar.get(Calendar.MINUTE)
                                        }

                                        TimePickerDialog(
                                            localContext,
                                            { _, hourOfDay, minute ->
                                                val formattedTime = formatTime(hourOfDay, minute)
                                                time = formattedTime
                                                notifications = notifications.toMutableList().also {
                                                    it[index] = it[index].toMutableMap().apply {
                                                        this["timestamp"] = formattedTime
                                                    }
                                                }
                                            },
                                            initialHour,
                                            initialMinute,
                                            false
                                        ).show()
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Column(
                                    verticalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxHeight()
                                ) {
                                    Text(
                                        text = "Time",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color(0xFF607D8B)
                                    )
                                    Text(
                                        text = time.ifEmpty { "Select Time" },
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                        color = if (time.isEmpty()) Color.Gray else Color.Black
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        firestore.collection("users").document(userEmail)
                            .update("notifications", notifications)
                            .addOnSuccessListener {
                                Toast.makeText(context, "Notifications updated", Toast.LENGTH_SHORT).show()
                                val intent = Intent(context, ProfileActivity::class.java)
                                context.startActivity(intent)
                                (context as? ComponentActivity)?.finish()
                            }
                            .addOnFailureListener {
                                Toast.makeText(context, "Update failed", Toast.LENGTH_SHORT).show()
                            }
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Icon(Icons.Default.Save, contentDescription = "Save")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save Settings")
                }
            }
        }
    }

    private fun formatTime(hourOfDay: Int, minute: Int): String {
        val isPM = hourOfDay >= 12
        val hour = if (hourOfDay % 12 == 0) 12 else hourOfDay % 12
        val minuteStr = minute.toString().padStart(2, '0')
        val amPm = if (isPM) "PM" else "AM"
        return "$hour:$minuteStr $amPm"
    }
}
