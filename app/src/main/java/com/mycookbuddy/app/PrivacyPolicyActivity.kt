package com.mycookbuddy.app

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            MaterialTheme(colorScheme = lightColorScheme()) {
                PrivacyPolicyContent()
            }
        }
    }

    @Composable
    fun PrivacyPolicyContent() {
        val navController = rememberNavController()
        val context = LocalContext.current

        NavHost(navController = navController, startDestination = "privacy_policy") {
            composable("privacy_policy") {
                PrivacyPolicyScreen(
                    onNext = { saveUserToFirestore(context) },
                    onViewPrivacyPolicy = { navController.navigate("html_webview") }
                )
            }
            composable("html_webview") {
                val htmlContent = loadHtmlFromAssets(context, "privacy_policy.html")
                HtmlWebViewWithClose(htmlContent = htmlContent) { navController.popBackStack() }
            }
        }
    }

    @Composable
    fun PrivacyPolicyScreen(onNext: () -> Unit, onViewPrivacyPolicy: () -> Unit) {
        var agreed by remember { mutableStateOf(false) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.align(Alignment.Center)
                        ) {
                            Text(
                                text = "Sign In",
                                color = Color.Black,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )

                            Text(
                                text = "Before you continue",
                                color = Color.Black,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 28.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Text(
                                text = buildAnnotatedString {
                                    append("Please read and agree to MyCookBuddy ")
                                    withStyle(style = SpanStyle(textDecoration = TextDecoration.Underline, color = Color.Blue)) {
                                        append("Privacy Policy")
                                    }
                                },
                                modifier = Modifier.clickable { onViewPrivacyPolicy() },
                                color = Color.DarkGray,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Checkbox(
                                    checked = agreed,
                                    onCheckedChange = { agreed = it },
                                    colors = CheckboxDefaults.colors(checkedColor = Color.Black)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "I agree to MyCookBuddy Terms & Conditions and Privacy Policy",
                                    color = Color.Black,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }

                    Button(
                        onClick = onNext,
                        enabled = agreed,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (agreed) Color.Black else Color.Gray,
                            contentColor = Color.White
                        )
                    ) {
                        Text("Continue", fontWeight = FontWeight.Medium)
                    }
                }
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
                Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.Black)
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

        val defaultNotifications = listOf(
            mapOf("mealType" to "Breakfast", "status" to true, "timestamp" to "7:30 AM"),
            mapOf("mealType" to "Lunch", "status" to true, "timestamp" to "01:30 PM"),
            mapOf("mealType" to "Dinner", "status" to true, "timestamp" to "7:30 PM")
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
                setResult(RESULT_OK)
                finish()
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Error saving user data", e)
            }
    }
}