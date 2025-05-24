package com.mycookbuddy.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.*
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.mycookbuddy.app.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class OnboardingActivity : ComponentActivity() {
    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val userEmail = GoogleSignIn.getLastSignedInAccount(this)?.email ?: "unknown@example.com"

        setContent {
            MyApplicationTheme {
                OnboardingPreferenceScreen(userEmail) { cuisines, foodTypes ->
                    savePreferences(userEmail, cuisines, foodTypes)
                }
            }
        }
    }

    private fun savePreferences(userEmail: String, selectedCuisines: List<String>, selectedFoodTypes: List<String>) {
        val userPreferences = mapOf(
            "preferences" to "SET",
            "cuisines" to selectedCuisines,
            "foodTypes" to selectedFoodTypes
        )
        firestore.collection("users").document(userEmail)
            .set(userPreferences, SetOptions.merge())
            .addOnSuccessListener {
                Toast.makeText(this, "Preferences saved successfully", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to save preferences", Toast.LENGTH_SHORT).show()
            }
    }
}

@Composable
fun OnboardingPreferenceScreen(userEmail: String, onSave: (List<String>, List<String>) -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val successLottie by rememberLottieComposition(LottieCompositionSpec.Asset("success_animation.json"))

    var cuisines by remember { mutableStateOf(listOf("South Indian", "Bengali", "Punjabi", "Marathi", "Gujrati")) }
    var selectedCuisines by remember { mutableStateOf(setOf<String>()) }
    var selectedFoodTypes by remember { mutableStateOf(setOf<String>()) }
    var showSuccess by remember { mutableStateOf(false) }

    fun toggle(set: Set<String>, value: String): Set<String> = if (value in set) set - value else set + value

    Scaffold(topBar = {
        Column(
            modifier = Modifier
                .padding(top = 12.dp, bottom = 4.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "✨ Customize Your Taste!",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF4527A0),
                textAlign = TextAlign.Center
            )
        }
    }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .fillMaxSize()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("🍲 Cuisine Preferences", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF512DA8), modifier = Modifier.padding(bottom = 4.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFF3E5F5)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        cuisines.forEach { cuisine ->
                            AnimatedPreferenceRow(
                                title = cuisine,
                                selected = selectedCuisines.contains(cuisine),
                                onToggle = { selectedCuisines = toggle(selectedCuisines, cuisine) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text("🥗 Food Type Preferences", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF512DA8), modifier = Modifier.padding(bottom = 4.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFF3E5F5)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        listOf("Veg", "Non Veg", "Eggy", "Vegan").forEach { type ->
                            AnimatedPreferenceRow(
                                title = type,
                                selected = selectedFoodTypes.contains(type),
                                onToggle = { selectedFoodTypes = toggle(selectedFoodTypes, type) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    if (selectedCuisines.isNotEmpty() && selectedFoodTypes.isNotEmpty()) {
                        coroutineScope.launch {
                            showSuccess = true
                            delay(2000)
                            onSave(selectedCuisines.toList(), selectedFoodTypes.toList())
                            showSuccess = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C4DFF))
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save Preferences", color = Color.White)
            }
        }

        if (showSuccess) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    LottieAnimation(
                        composition = successLottie,
                        iterations = 1,
                        modifier = Modifier.size(160.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Preferences saved successfully.", color = Color.White, fontSize = 18.sp)
                }
            }
        }
    }
}

@Composable
fun AnimatedPreferenceRow(title: String, selected: Boolean, onToggle: () -> Unit) {
    val scale by animateFloatAsState(if (selected) 1.05f else 1f, label = "scaleAnim")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, modifier = Modifier.weight(1f), fontSize = 15.sp)
        Switch(checked = selected, onCheckedChange = { onToggle() })
    }
}