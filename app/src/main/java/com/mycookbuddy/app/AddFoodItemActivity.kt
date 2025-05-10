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
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.firebase.firestore.FirebaseFirestore
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
    var name by remember { mutableStateOf(TextFieldValue("")) }
    var selectedType by remember { mutableStateOf("Veg") } // Default selected
    var selectedEatingTypes by remember { mutableStateOf(setOf("Breakfast")) } // Default selected
    var repeatAfter by remember { mutableStateOf(TextFieldValue("")) }
    var showLoading by remember { mutableStateOf(false) }
    var nameShake by remember { mutableStateOf(false) }
    var typeShake by remember { mutableStateOf(false) }
    var mealShake by remember { mutableStateOf(false) }
    var repeatShake by remember { mutableStateOf(false) }

    val foodTypeColors = mapOf(
        "Veg" to Color(0xFFA5D6A7),
        "Non Veg" to Color(0xFFEF9A9A),
        "Eggy" to Color(0xFFFFE082),
        "Vegan" to Color(0xFF80CBC4)
    )

    val mealTypeColors = mapOf(
        "Breakfast" to Color(0xFFFFF59D),
        "Lunch" to Color(0xFF90CAF9),
        "Dinner" to Color(0xFFCE93D8)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Food Item", color = Color.White) },
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
                        nameShake = name.text.isBlank()
                        typeShake = selectedType.isBlank()
                        mealShake = selectedEatingTypes.isEmpty()
                        repeatShake = repeatAfter.text.toIntOrNull() == null

                        if (nameShake || typeShake || mealShake || repeatShake) return@FloatingActionButton

                        coroutineScope.launch {
                            showLoading = true
                            delay(1000)
                            showLoading = false
                            onSaveClick(
                                FoodItem(
                                    name = name.text,
                                    type = selectedType,
                                    eatingTypes = selectedEatingTypes.toList(),
                                    repeatAfter = repeatAfter.text.toIntOrNull() ?: 0,
                                    userEmail = userEmail
                                )
                            )
                        }
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
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (showLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Fastfood, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Food Name", style = MaterialTheme.typography.bodyMedium)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, shape = RoundedCornerShape(12.dp)),
                    isError = nameShake
                )

                Text("Select Type", style = MaterialTheme.typography.titleMedium)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf("Veg", "Non Veg", "Eggy", "Vegan").forEach { type ->
                        AssistChip(
                            onClick = { selectedType = type },
                            label = { Text(type, style = MaterialTheme.typography.bodyMedium) },
                            shape = CircleShape,
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (selectedType == type) foodTypeColors[type] ?: Color(0xFFB2EBF2) else Color.White
                            )
                        )
                    }
                }

                Text("Meal Preferences", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf("Breakfast", "Lunch", "Dinner").forEach { type ->
                        ElevatedFilterChip(
                            selected = selectedEatingTypes.contains(type),
                            onClick = {
                                selectedEatingTypes = if (selectedEatingTypes.contains(type))
                                    selectedEatingTypes - type else selectedEatingTypes + type
                            },
                            label = { Text(type, style = MaterialTheme.typography.bodyMedium) },
                            colors = FilterChipDefaults.elevatedFilterChipColors(
                                selectedContainerColor = mealTypeColors[type] ?: Color(0xFFE0F7FA)
                            )
                        )
                    }
                }

                OutlinedTextField(
                    value = repeatAfter,
                    onValueChange = { repeatAfter = it },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Repeat After (days)", style = MaterialTheme.typography.bodyMedium)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, shape = RoundedCornerShape(12.dp)),
                    isError = repeatShake
                )
            }
        }
    }
}
