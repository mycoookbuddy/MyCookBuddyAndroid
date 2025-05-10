// Revamped FoodItemDetailActivity screen with top/bottom bars, rich UI styling, and input enhancements

package com.mycookbuddy.app

import android.app.DatePickerDialog
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.firebase.firestore.FirebaseFirestore
import com.mycookbuddy.app.Utils.Companion.refreshHomeScreen
import com.mycookbuddy.app.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*

class FoodItemDetailActivity : ComponentActivity() {
    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val foodItemName = intent.getStringExtra("FOOD_ITEM_NAME") ?: ""
        val userEmail = GoogleSignIn.getLastSignedInAccount(this)?.email ?: "unknown@example.com"

        setContent {
            MyApplicationTheme {
                FoodItemDetailScreen(
                    userEmail = userEmail,
                    foodItemName = foodItemName,
                    onSaveClick = { updatedFoodItem ->
                        saveFoodItemToFirestore(updatedFoodItem, userEmail, foodItemName)
                    }
                )
            }
        }
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
                            refreshHomeScreen(this,true)
                            setResult(RESULT_OK)
                            finish() // ✅ Close the activity to return to list
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
                            refreshHomeScreen(this,true)
                            setResult(RESULT_OK)
                            finish() // ✅ Close the activity to return to list
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FoodItemDetailScreen(
    userEmail: String,
    foodItemName: String,
    onSaveClick: (FoodItem) -> Unit
) {
    val context = LocalContext.current
    var foodItem by remember { mutableStateOf(FoodItem()) }
    var lastConsumptionDate by remember { mutableStateOf("") }
    var eatingType by remember { mutableStateOf(setOf<String>()) }
    var selectedType by remember { mutableStateOf("") }
    var showLoading by remember { mutableStateOf(false) }

    val calendar = Calendar.getInstance()
    val datePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            lastConsumptionDate = "$dayOfMonth/${month + 1}/$year"
            foodItem = foodItem.copy(lastConsumptionDate = lastConsumptionDate)
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    LaunchedEffect(foodItemName) {
        val firestore = FirebaseFirestore.getInstance()
        firestore.collection("fooditem")
            .whereEqualTo("userEmail", userEmail)
            .whereEqualTo("name", foodItemName)
            .get()
            .addOnSuccessListener { result ->
                result.documents.firstOrNull()?.data?.let { data ->
                    foodItem = FoodItem(
                        name = data["name"] as? String ?: "",
                        userEmail = data["userEmail"] as? String ?: "",
                        type = data["type"] as? String ?: "",
                        eatingTypes = (data["eatingTypes"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                        lastConsumptionDate = data["lastConsumptionDate"] as? String ?: "",
                        repeatAfter = (data["repeatAfter"] as? Long)?.toInt() ?: 0
                    )
                    lastConsumptionDate = foodItem.lastConsumptionDate
                    eatingType = foodItem.eatingTypes.toSet()
                    selectedType = foodItem.type
                }
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Food Item", color = Color.White) },
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
        },
        floatingActionButton = {
            if (!showLoading) {
                FloatingActionButton(
                    onClick = {
                        showLoading = true
                        onSaveClick(foodItem)
                    },
                    containerColor = Color(0xFF26C6DA),
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Save, contentDescription = "Save", tint = Color.White)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (showLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                OutlinedTextField(
                    value = foodItem.name,
                    onValueChange = { foodItem = foodItem.copy(name = it) },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Fastfood, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(6.dp))
                            Text("Food Name")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Select Type", style = MaterialTheme.typography.titleMedium)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf("Veg", "Non Veg", "Eggy", "Vegan").forEach { type ->
                        AssistChip(
                            onClick = {
                                selectedType = type
                                foodItem = foodItem.copy(type = type)
                            },
                            label = { Text(type) },
                            shape = CircleShape,
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (selectedType == type) Color(0xFFB2EBF2) else Color.White
                            )
                        )
                    }
                }

                Text("Meal Preferences", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf("Breakfast", "Lunch", "Snacks", "Dinner").forEach { type ->
                        ElevatedFilterChip(
                            selected = eatingType.contains(type),
                            onClick = {
                                eatingType = if (eatingType.contains(type)) eatingType - type else eatingType + type
                                foodItem = foodItem.copy(eatingTypes = eatingType.toList())
                            },
                            label = { Text(type) }
                        )
                    }
                }

                OutlinedTextField(
                    value = foodItem.repeatAfter.toString(),
                    onValueChange = { foodItem = foodItem.copy(repeatAfter = it.toIntOrNull() ?: 0) },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(6.dp))
                            Text("Repeat After (days)")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = { datePickerDialog.show() },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (lastConsumptionDate.isEmpty()) "Pick Last Consumed Date" else lastConsumptionDate)
                }
            }
        }
    }
}
