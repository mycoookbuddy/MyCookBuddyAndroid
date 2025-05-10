package com.mycookbuddy.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.mycookbuddy.app.ui.theme.MyApplicationTheme

class SettingsActivity : ComponentActivity() {
    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val user = GoogleSignIn.getLastSignedInAccount(this)
        val userEmail = user?.email

        setContent {
            MyApplicationTheme {
                SettingsScreen(
                    onSave = { selectedCuisines, selectedFoodTypes ->
                        userEmail?.let { savePreferences(it, selectedCuisines, selectedFoodTypes) }
                    }
                )
            }
        }
    }

    private fun savePreferences(
        userEmail: String,
        selectedCuisines: List<String>,
        selectedFoodTypes: List<String>
    ) {
        val userUpdates = mapOf(
            "preferences" to "SET"
        )

        // Step 1: Save preferences status to "SET" in /users
        firestore.collection("users").document(userEmail)
            .set(userUpdates, SetOptions.merge())
            .addOnSuccessListener {
                // Step 2: Pull food items from /commonfooditem based on selectedCuisines and selectedFoodTypes
                firestore.collection("commonfooditem")
                    .whereArrayContainsAny("cuisines", selectedCuisines)
                    .whereIn("type", selectedFoodTypes)
                    .limit(20)
                    .get()
                    .addOnSuccessListener { result ->
                        val foodItems = result.documents.mapNotNull { doc ->
                            doc.toObject(FoodItem::class.java)?.copy(userEmail = userEmail)
                        }

                        // Step 3: Check for duplicates in /fooditem collection
                        firestore.collection("fooditem")
                            .whereEqualTo("userEmail", userEmail)
                            .get()
                            .addOnSuccessListener { existingItemsResult ->
                                val existingNames = existingItemsResult.documents.mapNotNull { it.getString("name") }
                                val newItems = foodItems.filter { it.name !in existingNames }

                                // Step 4: Save only new items in /fooditem collection
                                val batch = firestore.batch()
                                newItems.forEach { item ->
                                    val newDoc = firestore.collection("fooditem").document()
                                    batch.set(newDoc, item)
                                }

                                batch.commit().addOnSuccessListener {
                                    // Step 5: Show Toast and navigate to SuggestFoodItemListActivity
                                    Toast.makeText(
                                        this,
                                        "Preferences saved successfully",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    val intent = Intent(this, SuggestFoodItemsActivity::class.java)
                                    startActivity(intent)
                                    finish()
                                }.addOnFailureListener { e ->
                                    Toast.makeText(
                                        this,
                                        "Failed to save food items: ${e.message}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(
                                    this,
                                    "Failed to check existing food items: ${e.message}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(
                            this,
                            "Failed to fetch food items: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to save preferences: ${e.message}", Toast.LENGTH_SHORT)
                    .show()
            }
    }

    @Composable
    fun SelectableItem(
        title: String,
        selected: Boolean,
        onToggle: () -> Unit,
        selectedColor: Color
    ) {
        val interactionSource = remember { MutableInteractionSource() }
        val isHovered by interactionSource.collectIsHoveredAsState()
        val isPressed by interactionSource.collectIsPressedAsState()

        val animatedColor by animateColorAsState(
            targetValue = if (selected) selectedColor else if (isHovered) Color(0xFFE0E0E0) else Color(
                0xFFF4F4F4
            ),
            animationSpec = tween(300), label = ""
        )

        val scale by animateFloatAsState(
            targetValue = if (isPressed) 0.97f else if (selected) 1.05f else 1.0f,
            animationSpec = tween(150), label = ""
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .scale(scale)
                .clickable(interactionSource = interactionSource, indication = null) { onToggle() },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = animatedColor)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(16.dp)
            ) {

                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.weight(1f))
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onToggle() }
                )
            }
        }
    }

    @Composable
    fun SettingsScreen(
        onSave: (List<String>, List<String>) -> Unit
    ) {
        val firestore = FirebaseFirestore.getInstance()
        var cuisines by remember { mutableStateOf<List<String>>(emptyList()) }
        val selectedCuisines = remember { mutableStateListOf<String>() }
        val selectedFoodTypes = remember { mutableStateListOf<String>() }

        val foodTypeIcons = mapOf(
            "Veg" to R.drawable.vegetarian_4x,
            "Non Veg" to R.drawable.non_vegetarian_4x,
            "Eggy" to R.drawable.eggetarian_4x,
            "Vegan" to R.drawable.vegan_4x
        )

        val foodTypeColors = mapOf(
            "Veg" to Color(0xFFC8E6C9),
            "Non Veg" to Color(0xFFFFCDD2),
            "Eggy" to Color(0xFFFFF9C4),
            "Vegan" to Color(0xFFDCEDC8)
        )

        LaunchedEffect(Unit) {
            // Fetch cuisines and select all by default
            firestore.collection("cuisine").get()
                .addOnSuccessListener { results ->
                    cuisines = results.documents.mapNotNull { doc ->
                        doc.getString("name")
                    }
                    selectedCuisines.addAll(cuisines) // Select all cuisines by default
                }

            // Select all food types by default
            selectedFoodTypes.addAll(foodTypeIcons.keys)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Cuisine Preferences",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )

            cuisines.forEach { cuisine ->
                SelectableItem(
                    title = cuisine,
                    selected = selectedCuisines.contains(cuisine),
                    onToggle = {
                        if (selectedCuisines.contains(cuisine)) selectedCuisines.remove(cuisine)
                        else selectedCuisines.add(cuisine)
                    },
                    selectedColor = Color(0xFFE1F5FE)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "Food Type Preferences",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )

            foodTypeIcons.forEach { (type, icon) ->
                SelectableItem(
                    title = type,
                    selected = selectedFoodTypes.contains(type),
                    onToggle = {
                        if (selectedFoodTypes.contains(type)) selectedFoodTypes.remove(type)
                        else selectedFoodTypes.add(type)
                    },
                    selectedColor = foodTypeColors[type] ?: Color(0xFFF0F0F0)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = {
                        onSave(selectedCuisines.toList(), selectedFoodTypes.toList())
                    },
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00ACC1))
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Save", tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Save", color = Color.White)
                }
            }
        }
    }
}
