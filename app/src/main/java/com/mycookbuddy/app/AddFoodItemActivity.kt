package com.mycookbuddy.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.firebase.firestore.FirebaseFirestore
import com.mycookbuddy.app.component.CheckboxGroup
import com.mycookbuddy.app.component.SegmentedControl
import com.mycookbuddy.app.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AddFoodItemActivity : ComponentActivity() {
    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val userEmail = GoogleSignIn.getLastSignedInAccount(this)?.email ?: "unknown@example.com"

        setContent {
            MyApplicationTheme {
                AddFoodItemScreen(userEmail = userEmail) { foodItem ->
                    saveFoodItemToFirestore(foodItem, userEmail)
                }
            }
        }
    }

    private fun saveFoodItemToFirestore(foodItem: FoodItem, userEmail: String) {
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
                        .add(foodItem.copy(userEmail = userEmail))
                        .addOnSuccessListener { documentReference ->
                            Toast.makeText(this, "Food item saved successfully", Toast.LENGTH_SHORT).show()
                            val intent = Intent().apply {
                                putExtra("NEW_FOOD_ITEM_ID", documentReference.id)
                            }
                            setResult(RESULT_OK, intent)
                            finish()
                        }
                        .addOnFailureListener { e ->
                            Log.e("Firestore", "Error saving food item", e)
                            Toast.makeText(this, "Failed to save food item", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Error checking food item existence", e)
                Toast.makeText(this, "Error checking food item existence", Toast.LENGTH_SHORT).show()
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddFoodItemScreen(userEmail: String, onSaveClick: (FoodItem) -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("Veg") }
    var selectedEatingTypes by remember { mutableStateOf(setOf<String>()) }
    var repeatAfter by remember { mutableStateOf("") }
    var showLoading by remember { mutableStateOf(false) }
    var showSuccess by remember { mutableStateOf(false) }

    LaunchedEffect(showSuccess) {
        if (showSuccess) {
            delay(1200)
            (context as? ComponentActivity)?.finish()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Add Food Item", color = Color.White) },
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
                                        value = name,
                                        onValueChange = { name = it },
                                        label = { Text("Food Name", color = Color.White) },
                                        leadingIcon = {
                                            Icon(Icons.Default.Fastfood, contentDescription = null, tint = Color.White)
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Text("Select Type", color = Color.White)
                                    SegmentedControl(
                                        options = listOf("Veg", "Non Veg", "Eggy", "Vegan"),
                                        selectedOption = selectedType,
                                        onOptionSelected = { selectedType = it },
                                        columns = 2,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                    )

                                    Text("Meal Preferences", color = Color.White)
                                    CheckboxGroup(
                                        options = listOf("Breakfast", "Lunch", "Dinner"),
                                        selectedOptions = selectedEatingTypes,
                                        onOptionToggle = { selectedEatingTypes = selectedEatingTypes.toggle(it) },
                                        columns = 1,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                    )

                                    OutlinedTextField(
                                        value = repeatAfter,
                                        onValueChange = {
                                            if (it.all(Char::isDigit)) repeatAfter = it
                                        },
                                        label = { Text("Repeat After (days)", color = Color.White) },
                                        leadingIcon = {
                                            Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White)
                                        },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = {
                                if (name.isBlank() || selectedEatingTypes.isEmpty() || repeatAfter.isBlank()) {
                                    Toast.makeText(context, "Please fill all fields", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                coroutineScope.launch {
                                    showLoading = true
                                    delay(500)
                                    onSaveClick(
                                        FoodItem(
                                            name = name,
                                            type = selectedType,
                                            eatingTypes = selectedEatingTypes.toList(),
                                            repeatAfter = repeatAfter.toIntOrNull() ?: 0,
                                            userEmail = userEmail
                                        )
                                    )
                                    showLoading = false
                                    showSuccess = true
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF26C6DA))
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, tint = Color.White)
                            Spacer(Modifier.padding(start = 8.dp))
                            Text("Save", color = Color.White)
                        }
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
                    Text("Food item added successfully.", color = Color.White, fontSize = 18.sp)
                }
            }
        }
    }
}

private fun <T> Set<T>.toggle(item: T): Set<T> = if (contains(item)) minus(item) else plus(item)

