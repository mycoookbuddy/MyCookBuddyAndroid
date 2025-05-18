package com.mycookbuddy.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class HelpActivity : ComponentActivity() {

    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val user = GoogleSignIn.getLastSignedInAccount(this)
        val userEmail = user?.email ?: "unknown@example.com"
        setContent {
            HelpScreen(userEmail) // Replace with actual user email
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun HelpScreen(userEmail: String) {
        var feedback by remember { mutableStateOf(TextFieldValue("")) }
        var isSubmitting by remember { mutableStateOf(false) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Help") },
                    navigationIcon = {
                        IconButton(onClick = { finish() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("We value your feedback!", style = MaterialTheme.typography.headlineMedium)

                OutlinedTextField(
                    value = feedback,
                    onValueChange = {
                        if (it.text.length <= 500) feedback = it
                    },
                    label = { Text("Your Feedback") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp), // Makes the TextField initially look big
                    maxLines = 5
                )

                Button(
                    onClick = {
                        if (feedback.text.isNotBlank()) {
                            isSubmitting = true
                            submitFeedback(userEmail, feedback.text) {
                                isSubmitting = false
                                feedback = TextFieldValue("")
                            }
                        } else {
                            Toast.makeText(this@HelpActivity, "Feedback cannot be empty", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isSubmitting) "Submitting..." else "Submit")
                }

                Spacer(modifier = Modifier.height(32.dp))

                Text("Contact Us: mycoookbuddy@gmail.com", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    private fun submitFeedback(userEmail: String, feedbackContent: String, onComplete: () -> Unit) {
        val feedbackData = mapOf(
            "email" to userEmail,
            "feedback" to feedbackContent,
            "date" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        )

        firestore.collection("feedbacks")
            .add(feedbackData)
            .addOnSuccessListener {
                Toast.makeText(this, "Feedback submitted successfully!", Toast.LENGTH_SHORT).show()
                onComplete()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to submit feedback: ${e.message}", Toast.LENGTH_SHORT).show()
                onComplete()
            }
    }
}