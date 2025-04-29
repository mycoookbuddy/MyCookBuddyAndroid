package com.mycookbuddy.app

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

class AddFoodItemActivity : ComponentActivity() {

    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val userEmail = GoogleSignIn.getLastSignedInAccount(this)?.email ?: "unknown@example.com"

        setContent {
            AddFoodItemScreen(
                onSaveClick = { foodItem ->
                    saveFoodItemToFirestore(foodItem, userEmail)
                }
            )
        }
    }

    private fun saveFoodItemToFirestore(foodItem: FoodItem, userEmail: String) {
        if (foodItem.name.isBlank() || foodItem.type.isBlank()) {
            Toast.makeText(this, "Name and Veg/Non-Veg are mandatory", Toast.LENGTH_SHORT).show()
            return
        }

        firestore.collection("fooditem")
            .whereEqualTo("name", foodItem.name)
            .whereEqualTo("userEmail", userEmail)
            .get()
            .addOnSuccessListener { result ->
                if (result.documents.isNotEmpty()) {
                    Toast.makeText(this, "Food item already exists", Toast.LENGTH_SHORT).show()
                } else {
                    val foodItemData = hashMapOf(
                        "name" to foodItem.name,
                        "type" to foodItem.type,
                        "eatingTypes" to foodItem.eatingTypes,
                        "lastConsumptionDate" to foodItem.lastConsumptionDate,
                        "repeatAfter" to foodItem.repeatAfter,
                        "userEmail" to userEmail
                    )

                    firestore.collection("fooditem")
                        .add(foodItemData)
                        .addOnSuccessListener { documentReference ->
                            Toast.makeText(this, "Food item saved successfully", Toast.LENGTH_SHORT)
                                .show()
                            val intent = Intent().apply {
                                putExtra("NEW_FOOD_ITEM_ID", documentReference.id)
                            }
                            setResult(RESULT_OK, intent)
                            finish() // Close the activity
                        }
                        .addOnFailureListener { e ->
                            Log.e("Firestore", "Error saving food item", e)
                            Toast.makeText(this, "Failed to save food item", Toast.LENGTH_SHORT)
                                .show()
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Error checking food item existence", e)
                Toast.makeText(this, "Error checking food item existence", Toast.LENGTH_SHORT)
                    .show()
            }
    }
}
@Composable
fun AddFoodItemScreen(onSaveClick: (FoodItem) -> Unit) {
    var name by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("") }
    var eatingTypes by remember { mutableStateOf(setOf<String>()) }
    var lastConsumptionDate by remember { mutableStateOf("") }
    var repeatAfter by remember { mutableStateOf("") }

    val calendar = Calendar.getInstance()
    val datePickerDialog = DatePickerDialog(
        LocalContext.current,
        { _, year, month, dayOfMonth ->
            lastConsumptionDate = "$dayOfMonth/${month + 1}/$year"
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BasicTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { innerTextField ->
                if (name.isEmpty()) Text("Enter Food Item Name")
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
                        }
                    )
                    Text(type)
                }
            }
        }


        Text("Eating Type")
        Row {
            listOf("Breakfast", "Lunch", "Snacks", "Dinner").forEach { type ->
                Row {
                    Checkbox(
                        checked = eatingTypes.contains(type),
                        onCheckedChange = {
                            eatingTypes = if (it) eatingTypes + type else eatingTypes - type
                        }
                    )
                    Text(type)
                }
            }
        }

        Button(onClick = { datePickerDialog.show() }) {
            Text(text = if (lastConsumptionDate.isEmpty()) "Pick Date" else lastConsumptionDate)
        }

        BasicTextField(
            value = repeatAfter,
            onValueChange = { repeatAfter = it },
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { innerTextField ->
                if (repeatAfter.isEmpty()) Text("Repeat After (in days)")
                innerTextField()
            }
        )

        Button(onClick = {
            onSaveClick(
                FoodItem(
                    name = name,
                    type = selectedType,
                    eatingTypes = eatingTypes.toList(),
                    lastConsumptionDate = lastConsumptionDate,
                    repeatAfter = repeatAfter.toIntOrNull() ?: 0
                )
            )
        }) {
            Text("Save")
        }
    }
}

data class FoodItem(
    var userEmail: String = "",
    val name: String = "",
    val type: String = "",
    val cuisines: List<String> = emptyList(),
    val eatingTypes: List<String> = emptyList(),
    val lastConsumptionDate: String = "",
    val repeatAfter: Int = 0
)