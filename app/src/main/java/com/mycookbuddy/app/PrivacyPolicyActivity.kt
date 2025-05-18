package com.mycookbuddy.app

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.firebase.firestore.FirebaseFirestore
import java.io.BufferedReader
import java.io.InputStreamReader


class PrivacyPolicyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PrivacyPolicyContent()
        }
    }

    @Composable
    fun PrivacyPolicyContent() {
        val navController = rememberNavController()
        val context = LocalContext.current

        NavHost(navController = navController, startDestination = "privacy_policy") {
            composable("privacy_policy") {
                PrivacyPolicyScreen(
                    onNext = {
                        saveUserToFirestore(context)

                    },
                    onViewPrivacyPolicy = {
                        navController.navigate("html_webview")
                    }
                )
            }
            composable("html_webview") {
                val htmlContent = loadHtmlFromAssets(
                    context = LocalContext.current,
                    fileName = "privacy_policy.html"
                )
                HtmlWebViewWithClose(
                    htmlContent = htmlContent,
                    onClose = { navController.popBackStack() }
                )
            }
        }
    }

    @Composable
    fun PrivacyPolicyScreen(
        onNext: () -> Unit, // Changed from @Composable () -> Unit to () -> Unit
        onViewPrivacyPolicy: () -> Unit
    ) {
        var isChecked by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Privacy Policy", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = isChecked,
                        onCheckedChange = { isChecked = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("I accept the privacy policy", style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "View Privacy Policy",
                    style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.clickable { onViewPrivacyPolicy() }
                )
            }
            Button(
                onClick = onNext, // This now works correctly
                enabled = isChecked,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Next")
            }
        }
    }

    @Composable
    fun HtmlWebViewWithClose(htmlContent: String, onClose: () -> Unit) {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(factory = { context ->
                WebView(context).apply {
                    webViewClient = WebViewClient()
                    loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
                }
            }, modifier = Modifier.fillMaxSize())

            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close"
                )
            }
        }
    }

    fun loadHtmlFromAssets(context: Context, fileName: String): String {
        return context.assets.open(fileName).use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                reader.readText()
            }
        }
    }

    private fun saveUserToFirestore(context: Context) {
        val firestore = FirebaseFirestore.getInstance()
        val account = GoogleSignIn.getLastSignedInAccount(context)
        val userName = account?.displayName
        val userEmail = account?.email ?: return
        // Define the default notifications

        val defaultNotifications = listOf(
            mapOf(
                "mealType" to "Breakfast",
                "status" to true,
                "timestamp" to "7:30 AM"
            ),
            mapOf(
                "mealType" to "Lunch",
                "status" to true,
                "timestamp" to "01:30 PM"
            ),
            mapOf(
                "mealType" to "Dinner",
                "status" to true,
                "timestamp" to "7:30 PM"
            )
        )
        val user = mutableMapOf<String, Any>(
            "name" to (userName ?: "User"),
            "email" to userEmail,
            "privacyPolicy" to true,
            "notifications" to defaultNotifications
        )

        firestore.collection("users").document(userEmail)
            .set(user)
            .addOnSuccessListener {
                Log.d("Firestore", "User data successfully saved!")
                setResult(RESULT_OK) // Notify MainActivity that saving is done
                finish() // Close PrivacyPolicyActivity
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Error saving user data", e)
            }
    }

}