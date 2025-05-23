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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.mycookbuddy.app.Utils.Companion.refreshHomeScreen
import com.mycookbuddy.app.component.CheckboxGroup
import com.mycookbuddy.app.component.SegmentedControl
import com.mycookbuddy.app.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import java.util.*

class FoodItemDetailActivity : ComponentActivity() {
    private val firestore = FirebaseFirestore.getInstance()
    private var showLoading by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val foodItemName = intent.getStringExtra("FOOD_ITEM_NAME") ?: ""
        val userEmail = GoogleSignIn.getLastSignedInAccount(this)?.email ?: "unknown@example.com"

        setContent {
            MyApplicationTheme {
                FoodItemDetailScreen(
                    userEmail = userEmail,
                    foodItemName = foodItemName,
                    onLoadingChange = { showLoading = it },
                    onSaveClick = { updatedFoodItem ->
                        saveFoodItemToFirestore(updatedFoodItem, userEmail, foodItemName, onLoadingChange = { showLoading = it })
                    }
                )
            }
        }
    }

    private fun saveFoodItemToFirestore(
        foodItem: FoodItem,
        userEmail: String,
        originalName: String,
        onLoadingChange: (Boolean) -> Unit
    ) {
        val newName = foodItem.name.trim()
        // Check for duplicate name (excluding the current item)
        firestore.collection("fooditem")
            .whereEqualTo("userEmail", userEmail)
            .whereEqualTo("name", newName)
            .get()
            .addOnSuccessListener { result ->
                val isDuplicate = result.documents.any { it.getString("name") == newName && newName != originalName }
                if (isDuplicate) {
                    Toast.makeText(this, "A food item with this name already exists.", Toast.LENGTH_SHORT).show()
                    onLoadingChange(false)
                    return@addOnSuccessListener
                }
                // Proceed with original save logic
                firestore.collection("fooditem")
                    .whereEqualTo("userEmail", userEmail)
                    .whereEqualTo("name", originalName)
                    .get()
                    .addOnSuccessListener { result2 ->
                        if (result2.documents.isNotEmpty()) {
                            val documentId = result2.documents[0].id
                            firestore.collection("fooditem")
                                .document(documentId)
                                .set(foodItem)
                                .addOnSuccessListener {
                                    Toast.makeText(this, "Food item updated successfully", Toast.LENGTH_SHORT).show()
                                    refreshHomeScreen(this, true)
                                    setResult(RESULT_OK)
                                    finish()
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
                                    refreshHomeScreen(this, true)
                                    setResult(RESULT_OK)
                                    finish()
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
            .addOnFailureListener { e ->
                Log.e("Firestore", "Error checking duplicate food item", e)
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodItemDetailScreen(
    userEmail: String,
    foodItemName: String,
    onLoadingChange: (Boolean) -> Unit,
    onSaveClick: (FoodItem) -> Unit
) {
    val context = LocalContext.current
    var foodItem by remember { mutableStateOf(FoodItem()) }
    var lastConsumptionDate by remember { mutableStateOf("") }
    var eatingType by remember { mutableStateOf(setOf<String>()) }
    var selectedType by remember { mutableStateOf("") }
    var showLoading by remember { mutableStateOf(false) }
    var showSuccess by remember { mutableStateOf(false) }
    var repeatAfterText by remember { mutableStateOf(foodItem.repeatAfter?.toString() ?: "") }

    val calendar = Calendar.getInstance()
    val datePickerDialog = DatePickerDialog(
        context,
        { _, year, month, day ->
            lastConsumptionDate = "$day/${month + 1}/$year"
            foodItem = foodItem.copy(lastConsumptionDate = lastConsumptionDate)
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    ).apply { datePicker.maxDate = System.currentTimeMillis() }

    LaunchedEffect(showSuccess) {
        if (showSuccess) {
            delay(1200)
            (context as? ComponentActivity)?.finish()
        }
    }

    LaunchedEffect(foodItemName) {
        FirebaseFirestore.getInstance()
            .collection("fooditem")
            .whereEqualTo("userEmail", userEmail)
            .whereEqualTo("name", foodItemName)
            .get()
            .addOnSuccessListener { result ->
                result.documents.firstOrNull()?.data?.let { data ->
                    val types = (data["eatingTypes"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                    val repeat = (data["repeatAfter"] as? Long)?.toInt() ?: 0
                    foodItem = FoodItem(
                        name = data["name"] as? String ?: "",
                        userEmail = data["userEmail"] as? String ?: "",
                        type = data["type"] as? String ?: "",
                        eatingTypes = types,
                        lastConsumptionDate = data["lastConsumptionDate"] as? String ?: "",
                        repeatAfter = repeat
                    )
                    lastConsumptionDate = foodItem.lastConsumptionDate
                    eatingType = types.toSet()
                    selectedType = foodItem.type
                    repeatAfterText = repeat.toString()
                }
            }
            .addOnFailureListener { Log.e("Firestore", "Error fetching food item details", it) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Edit Food Item", color = Color.White) },
                    navigationIcon = {
                        IconButton(onClick = { (context as? ComponentActivity)?.finish() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                    modifier = Modifier.background(
                        Brush.horizontalGradient(listOf(Color(0xFF00ACC1), Color(0xFF26C6DA)))
                    )
                )
            }
        ) { padding ->
            Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                if (!showLoading) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                        ) {
                            Box(
                                Modifier
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(Color(0xFF42A5F5), Color(0xFF26C6DA))
                                        )
                                    )
                                    .padding(16.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    OutlinedTextField(
                                        value = foodItem.name,
                                        onValueChange = { foodItem = foodItem.copy(name = it) },
                                        label = { Text("Food Name", color = Color.White) },
                                        leadingIcon = {
                                            Icon(Icons.Default.Fastfood, contentDescription = null, tint = Color.White)
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Text("Select Type", color = Color.White, fontSize = 16.sp)
                                    SegmentedControl(
                                        options = listOf("Veg", "Non Veg", "Eggy", "Vegan"),
                                        selectedOption = selectedType,
                                        onOptionSelected = {
                                            selectedType = it
                                            foodItem = foodItem.copy(type = it)
                                        },
                                        columns = 2,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                    )

                                    Text("Meal Preferences", color = Color.White, fontSize = 16.sp)
                                    CheckboxGroup(
                                        options = listOf("Breakfast", "Lunch", "Dinner"),
                                        selectedOptions = eatingType,
                                        onOptionToggle = { meal ->
                                            eatingType = eatingType.toggle(meal)
                                            foodItem = foodItem.copy(eatingTypes = eatingType.toList())
                                        },
                                        columns = 1,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                    )

                                    OutlinedTextField(
                                        value = repeatAfterText,
                                        onValueChange = {
                                            if (it.all(Char::isDigit)) {
                                                repeatAfterText = it
                                                foodItem = foodItem.copy(repeatAfter = it.toIntOrNull())
                                            }
                                        },
                                        label = { Text("Repeat After (days)", color = Color.White) },
                                        leadingIcon = {
                                            Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White)
                                        },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Spacer(Modifier.padding(start = 8.dp))
                                        Text("Last Consumed On:", color = Color.White)
                                        Spacer(Modifier.padding(start = 8.dp))
                                        Button(
                                            onClick = { datePickerDialog.show() },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.8f))
                                        ) {
                                            Text(
                                                if (lastConsumptionDate.isEmpty()) "Choose date" else lastConsumptionDate,
                                                color = Color(0xFF26C6DA)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Button(
                        onClick = {
                            showLoading = true
                            onSaveClick(foodItem)
                            showSuccess = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF26C6DA))
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.padding(start = 8.dp))
                        Text("Save", color = Color.White)
                    }
                }
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
                    Card(
                        shape = CircleShape,
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(10.dp),
                        modifier = Modifier.size(120.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Success",
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(64.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Food item saved successfully", color = Color.White, fontSize = 18.sp)
                }
            }
        }
    }
}

private fun <T> Set<T>.toggle(item: T): Set<T> = if (contains(item)) minus(item) else plus(item)
