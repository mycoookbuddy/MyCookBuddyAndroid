package com.mycookbuddy.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class SettingsActivity : ComponentActivity() {

    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val userEmail = intent.getStringExtra("USER_EMAIL") ?: return

        setContent {
            SettingsScreen(
                onSave = { selectedCuisines, selectedFoodTypes ->
                    savePreferences(userEmail, selectedCuisines, selectedFoodTypes, "SET")
                },
                onSkip = {
                    savePreferences(userEmail, emptyList(), emptyList(), "SKIPPED")
                }
            )
        }
    }

    private fun savePreferences(
        userEmail: String,
        selectedCuisines: List<String>,
        selectedFoodTypes: List<String>,
        preferencesStatus: String
    ) {
        val userUpdates = mapOf(
            "cuisines" to selectedCuisines,
            "foodTypes" to selectedFoodTypes,
            "preferences" to preferencesStatus
        )

        firestore.collection("users").document(userEmail)
            .set(userUpdates, SetOptions.merge())  // Safely update without overwriting existing fields
            .addOnSuccessListener {
                Toast.makeText(this, "Preferences saved successfully", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, SuggestFoodItemsActivity::class.java).apply {
                    putExtra("USER_EMAIL", userEmail)
                }
                startActivity(intent)
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to save preferences: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}

@Composable
fun SettingsScreen(
    onSave: (List<String>, List<String>) -> Unit,
    onSkip: () -> Unit
) {
    val firestore = FirebaseFirestore.getInstance()
    var cuisines by remember { mutableStateOf<List<String>>(emptyList()) }
    val selectedCuisines = remember { mutableStateListOf<String>() }
    val selectedFoodTypes = remember { mutableStateListOf<String>() }
    val foodTypes = listOf("Veg", "Non Veg", "Eggy", "Vegan")

    LaunchedEffect(Unit) {
        firestore.collection("cuisine").get()
            .addOnSuccessListener { querySnapshot ->
                cuisines = querySnapshot.documents.mapNotNull { it.getString("name") }
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Set Your Preferences", style = MaterialTheme.typography.titleLarge)

        // Cuisines Checkboxes
        Text("Cuisines", style = MaterialTheme.typography.titleMedium)
        cuisines.forEach { cuisine ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = selectedCuisines.contains(cuisine),
                    onCheckedChange = { isChecked ->
                        if (isChecked) selectedCuisines.add(cuisine)
                        else selectedCuisines.remove(cuisine)
                    }
                )
                Text(text = cuisine)
            }
        }

        // Food Types Checkboxes
        Text("Food Types", style = MaterialTheme.typography.titleMedium)
        foodTypes.forEach { foodType ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = selectedFoodTypes.contains(foodType),
                    onCheckedChange = { isChecked ->
                        if (isChecked) selectedFoodTypes.add(foodType)
                        else selectedFoodTypes.remove(foodType)
                    }
                )
                Text(text = foodType)
            }
        }

        // Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(onClick = { onSkip() }) {
                Text("Skip")
            }
            Button(onClick = { onSave(selectedCuisines.toList(), selectedFoodTypes.toList()) }) {
                Text("Save")
            }
        }
    }
}
