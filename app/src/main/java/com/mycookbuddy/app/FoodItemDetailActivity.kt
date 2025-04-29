package com.mycookbuddy.app

import android.app.DatePickerDialog
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.gms.auth.api.signin.GoogleSignIn
import java.util.Calendar

class FoodItemDetailActivity : ComponentActivity() {

    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val foodItemName = intent.getStringExtra("FOOD_ITEM_NAME") ?: ""
        val userEmail = GoogleSignIn.getLastSignedInAccount(this)?.email ?: "unknown@example.com"
        setContent {
            FoodItemDetailScreen(
                userEmail = userEmail,
                foodItemName = foodItemName,
                onSaveClick = { updatedFoodItem ->
                    saveFoodItemToFirestore(updatedFoodItem, userEmail, foodItemName)
                }
            )
        }
    }

    private fun refreshHomeScreen(refresh: Boolean) {
        val sharedPreferences = getSharedPreferences("MyCookBuddyPrefs", MODE_PRIVATE)
        sharedPreferences.edit { putBoolean("shouldRefresh", refresh) }
    }

    private fun saveFoodItemToFirestore(foodItem: FoodItem, userEmail: String, originalName: String) {
        firestore.collection("fooditem")
            .whereEqualTo("userEmail", userEmail)
            .whereEqualTo("name", originalName)
            .get()
            .addOnSuccessListener { result ->
                if (result.documents.isNotEmpty()) {
                    val documentId = result.documents[0].id
                    firestore.collection("fooditem")
                        .document(documentId)
                        .set(foodItem)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Food item updated successfully", Toast.LENGTH_SHORT).show()
                            refreshHomeScreen(true)
                        }
                        .addOnFailureListener { e ->
                            Log.e("Firestore", "Error updating food item", e)
                            Toast.makeText(this, "Failed to update food item", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    val newFoodItem = foodItem.copy(userEmail = userEmail)
                    firestore.collection("fooditem")
                        .add(newFoodItem)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Food item saved successfully", Toast.LENGTH_SHORT).show()
                            refreshHomeScreen(true)
                        }
                        .addOnFailureListener { e ->
                            Log.e("Firestore", "Error saving new food item", e)
                            Toast.makeText(this, "Failed to save food item", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Error fetching food item", e)
            }
    }
}

@Composable
fun FoodItemDetailScreen(
    userEmail: String,
    foodItemName: String,
    onSaveClick: (FoodItem) -> Unit
) {
    var foodItem by remember { mutableStateOf(FoodItem()) }
    var lastConsumptionDate by remember { mutableStateOf("") }
    var eatingType by remember { mutableStateOf(setOf<String>()) }
    var selectedType by remember { mutableStateOf("") }

    LaunchedEffect(foodItemName) {
        val firestore = FirebaseFirestore.getInstance()
        firestore.collection("fooditem")
            .whereEqualTo("userEmail", userEmail)
            .whereEqualTo("name", foodItemName)
            .get()
            .addOnSuccessListener { result ->
                if (result.documents.isNotEmpty()) {
                    val fetchedFoodItem = result.documents[0].data?.let { data ->
                        FoodItem(
                            name = data["name"] as? String ?: "",
                            userEmail = data["userEmail"] as? String ?: "",
                            type = data["type"] as? String ?: "",
                            eatingTypes = (data["eatingTypes"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                            lastConsumptionDate = data["lastConsumptionDate"] as? String ?: "",
                            repeatAfter = (data["repeatAfter"] as? Long)?.toInt() ?: 0
                        )
                    } ?: FoodItem()
                    foodItem = fetchedFoodItem
                    lastConsumptionDate = fetchedFoodItem.lastConsumptionDate
                    eatingType = fetchedFoodItem.eatingTypes.toSet()
                    selectedType = fetchedFoodItem.type
                }
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Error fetching food item details", e)
            }
    }

    val calendar = Calendar.getInstance()
    val datePickerDialog = DatePickerDialog(
        LocalContext.current,
        { _, year, month, dayOfMonth ->
            lastConsumptionDate = "$dayOfMonth/${month + 1}/$year"
            foodItem = foodItem.copy(lastConsumptionDate = lastConsumptionDate)
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        BasicTextField(
            value = foodItem.name,
            onValueChange = { foodItem = foodItem.copy(name = it) },
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { innerTextField ->
                if (foodItem.name.isEmpty()) Text("Enter Food Item Name")
                innerTextField()
            }
        )

        Text("Type")
        Row {
            listOf("Veg", "Non Veg", "Eggy", "Vegan").forEach { type ->
                Row {
                    RadioButton(
                        selected = selectedType == type,
                        onClick = {
                            selectedType = type
                            foodItem = foodItem.copy(type = selectedType)
                        }
                    )
                    Text(type)
                }
            }
        }

        Button(onClick = { datePickerDialog.show() }) {
            Text(text = if (lastConsumptionDate.isEmpty()) "Pick Date" else lastConsumptionDate)
        }

        Text("Eating Type")
        Row {
            listOf("Breakfast", "Lunch", "Snacks", "Dinner").forEach { type ->
                Row {
                    Checkbox(
                        checked = eatingType.contains(type),
                        onCheckedChange = {
                            eatingType = if (it) eatingType + type else eatingType - type
                            foodItem = foodItem.copy(eatingTypes = eatingType.toList())
                        }
                    )
                    Text(type)
                }
            }
        }

        BasicTextField(
            value = foodItem.repeatAfter.toString(),
            onValueChange = { foodItem = foodItem.copy(repeatAfter = it.toIntOrNull() ?: 0) },
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { innerTextField ->
                if (foodItem.repeatAfter == 0) Text("Enter Repeat After (in days)")
                innerTextField()
            }
        )

        Button(onClick = { onSaveClick(foodItem) }) {
            Text("Save")
        }
    }
}