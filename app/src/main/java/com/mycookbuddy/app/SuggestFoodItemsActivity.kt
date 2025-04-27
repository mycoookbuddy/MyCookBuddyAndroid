package com.mycookbuddy.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.platform.LocalContext

import androidx.compose.material3.Text
import com.google.android.gms.auth.api.signin.GoogleSignIn
import androidx.core.content.edit


class SuggestFoodItemsActivity : ComponentActivity() {

    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SuggestFoodItemsScreenWithNavBar(
                userEmail = GoogleSignIn.getLastSignedInAccount(this)?.email ?: "unknown@example.com",
                onConfirmClick = { foodItemId -> updateLastConsumptionDate(foodItemId) }
            )
        }
    }

    override fun onResume() {
        super.onResume()

        val sharedPreferences = getSharedPreferences("MyCookBuddyPrefs", MODE_PRIVATE)
        val shouldRefresh = sharedPreferences.getBoolean("shouldRefresh", false)

        if (shouldRefresh) {
            sharedPreferences.edit() { putBoolean("shouldRefresh", false) }
            val intent = Intent(this, SuggestFoodItemsActivity::class.java)
            finish()
            startActivity(intent)
        }
    }
    private fun updateLastConsumptionDate(foodItemId: String) {
        val today = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())

        // Update the lastConsumptionDate in the fooditem collection
        firestore.collection("fooditem")
            .document(foodItemId)
            .update("lastConsumptionDate", today)
            .addOnSuccessListener {
                // Create a record in the consumptionhistory collection
                val consumptionHistory = mapOf(
                    "userEmail" to (intent.getStringExtra("USER_EMAIL") ?: "unknown@example.com"),
                    "consumptionDate" to today,
                    "foodItemId" to foodItemId
                )
                firestore.collection("consumptionhistory")
                    .add(consumptionHistory)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Food item updated and history recorded successfully", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Log.e("Firestore", "Error adding to consumption history", e)
                        Toast.makeText(this, "Failed to record consumption history", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Error updating food item", e)
                Toast.makeText(this, "Failed to update food item", Toast.LENGTH_SHORT).show()
            }
    }
}

@Composable
fun SuggestFoodItemsScreenContent(userEmail: String,
                                  onConfirmClick: (String) -> Unit // Add onConfirmClick parameter
) {
    var personalFoodItems by remember { mutableStateOf<List<Pair<String, FoodItem>>>(emptyList()) }
    var generalFoodItems by remember { mutableStateOf<List<Pair<String, FoodItem>>>(emptyList()) }
    var selectedFilter by remember { mutableStateOf("All") }
    var selectedEatingTypes by remember { mutableStateOf(setOf<String>()) }

    LaunchedEffect(userEmail) {
        val firestore = FirebaseFirestore.getInstance()
        val today = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
        )

        // Fetch all personal food items (unfiltered)
        val allPersonalFoodItems = mutableListOf<Pair<String, FoodItem>>()
        firestore.collection("fooditem")
            .whereEqualTo("userEmail", userEmail)
            .get()
            .addOnSuccessListener { result ->
                allPersonalFoodItems.addAll(result.documents.mapNotNull { document ->
                    val foodItem = FoodItem(
                        name = document["name"] as? String ?: "",
                        userEmail = document["userEmail"] as? String ?: "",
                        type = document["type"] as? String ?: "",
                        eatingType = (document["eatingType"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                        lastConsumptionDate = document["lastConsumptionDate"] as? String ?: "",
                        repeatAfter = (document["repeatAfter"] as? Long)?.toInt() ?: 0
                    )
                    document.id to foodItem
                })

                // Fetch personal food items filtered by lastConsumptionDate
                val filteredPersonalItems = result.documents.mapNotNull { document ->
                    val foodItem = FoodItem(
                        name = document["name"] as? String ?: "",
                        userEmail = document["userEmail"] as? String ?: "",
                        type = document["type"] as? String ?: "",
                        eatingType = (document["eatingType"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                        lastConsumptionDate = document["lastConsumptionDate"] as? String ?: "",
                        repeatAfter = (document["repeatAfter"] as? Long)?.toInt() ?: 0
                    )
                    val id = document.id
                    val lastConsumptionDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                        .parse(foodItem.lastConsumptionDate)
                    val nextConsumptionDate = Calendar.getInstance().apply {
                        if (lastConsumptionDate != null) {
                            time = lastConsumptionDate
                        }
                        add(Calendar.DAY_OF_YEAR, foodItem.repeatAfter)
                    }.time
                    if (nextConsumptionDate < today) id to foodItem else null
                }
                personalFoodItems = filteredPersonalItems

                // Fetch general food items
                firestore.collection("commonfooditem")
                    .get()
                    .addOnSuccessListener { commonResult ->
                        val commonItems = commonResult.documents.mapNotNull { document ->
                            val foodItem = FoodItem(
                                name = document["name"] as? String ?: "",
                                userEmail = "", // No userEmail for common items
                                type = document["type"] as? String ?: "",
                                eatingType = (document["eatingType"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                                lastConsumptionDate = "", // No lastConsumptionDate for common items
                                repeatAfter = 0 // No repeatAfter for common items
                            )
                            val id = document.id
                            id to foodItem
                        }
                        // Exclude items with the same name as in allPersonalFoodItems
                        generalFoodItems = commonItems.filter { commonItem ->
                            allPersonalFoodItems.none { personalItem ->
                                personalItem.second.name.equals(commonItem.second.name, ignoreCase = true)
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e("Firestore", "Error fetching common food items", e)
                    }
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Error fetching personal food items", e)
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Title
        Text(
            text = "Today's Suggestions",
            style = androidx.compose.ui.text.TextStyle(
                fontSize = 24.sp,
                color = androidx.compose.ui.graphics.Color.Black
            ),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Veg/Non-Veg/All Filter
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            listOf("All", "Veg", "Non Veg", "Eggy", "Vegan").forEach { filter ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter }
                    )
                    Text(text = filter)
                }
            }
        }

        // Eating Type Filter
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            listOf("Breakfast", "Lunch", "Snacks", "Dinner").forEach { type ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = selectedEatingTypes.contains(type),
                        onCheckedChange = { isChecked ->
                            selectedEatingTypes = if (isChecked) {
                                selectedEatingTypes + type
                            } else {
                                selectedEatingTypes - type
                            }
                        }
                    )
                    Text(text = type)
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Personal Category
            item {
                Text(
                    text = "Personal",
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = 18.sp,
                        color = androidx.compose.ui.graphics.Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(androidx.compose.ui.graphics.Color.Green)
                        .padding(8.dp)
                )
            }
            items(personalFoodItems.filter { filterFoodItem(it.second, selectedFilter, selectedEatingTypes) }) { (id, foodItem) ->
                FoodItemRow(
                    id, foodItem, false, userEmail, onConfirmClick)
            }

            // General Category
            item {
                Text(
                    text = "General",
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = 18.sp,
                        color = androidx.compose.ui.graphics.Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(androidx.compose.ui.graphics.Color.Green)
                        .padding(8.dp)
                )
            }
            items(generalFoodItems.filter { filterFoodItem(it.second, selectedFilter, selectedEatingTypes) }) { (id, foodItem) ->
                LocalContext.current
                FoodItemRow(
                    id = id,
                    foodItem = foodItem,
                    isGeneralCategory = true,
                    userEmail = userEmail,
                    onConfirmClick
                )
            }
        }
    }
}

fun filterFoodItem(foodItem: FoodItem, selectedFilter: String, selectedEatingTypes: Set<String>): Boolean {
    val matchesFilter = when (selectedFilter) {
        "Veg" -> foodItem.type.equals("veg")
        "Non Veg" -> foodItem.type.equals("nonveg")
        "Eggy" -> foodItem.type.equals("eggy")
        "Vegan" -> foodItem.type.equals("vegan")
        else -> true
    }
    val matchesEatingType = selectedEatingTypes.isEmpty() || foodItem.eatingType.any { it in selectedEatingTypes }
    return matchesFilter && matchesEatingType
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuggestFoodItemsScreenWithNavBar(
    userEmail: String,
    onConfirmClick: (String) -> Unit // Add onConfirmClick parameter
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Suggested Food Items") }
            )
        },
        bottomBar = {
            NavBar(context = LocalContext.current) // Use LocalContext.current
        }
    ) { innerPadding ->
        // Apply innerPadding to the content
        Box(modifier = Modifier.padding(innerPadding)) {
            SuggestFoodItemsScreenContent(
                userEmail = userEmail,
                onConfirmClick = onConfirmClick // Pass the lambda to the content

            )
        }
    }
}

@Composable
fun FoodItemRow(
    id: String,
    foodItem: FoodItem,
    isGeneralCategory: Boolean = false,
    userEmail: String,
    onConfirmClick: (String) -> Unit // Add onConfirmClick parameter
) {
    val context = LocalContext.current // Use LocalContext inside a @Composable function
    val firestore = FirebaseFirestore.getInstance()
    val today = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = foodItem.name)
                if (foodItem.lastConsumptionDate.isNotEmpty()) {
                    Text(
                        text = "Last Consumed On: ${foodItem.lastConsumptionDate}",
                        style = androidx.compose.ui.text.TextStyle(
                            color = androidx.compose.ui.graphics.Color.Gray,
                            fontSize = 12.sp
                        )
                    )
                }
            }
            Column {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(
                            when (foodItem.type) {
                                "veg" -> androidx.compose.ui.graphics.Color.Green
                                "nonveg" -> androidx.compose.ui.graphics.Color.Red
                                "eggy" -> androidx.compose.ui.graphics.Color.Yellow
                                "vegan" -> androidx.compose.ui.graphics.Color.Blue
                                else -> androidx.compose.ui.graphics.Color.Gray
                            }
                        )
                )
             }

        }
        if (isGeneralCategory) {
            Button(onClick = {
                // Add item to /fooditem collection
                val newFoodItem = foodItem.copy(
                    userEmail = userEmail,
                    lastConsumptionDate = today,
                    repeatAfter = 7
                )
                firestore.collection("fooditem")
                    .add(newFoodItem)
                    .addOnSuccessListener { documentReference ->
                        // Add entry to /consumptionhistory collection
                        val consumptionHistory = mapOf(
                            "userEmail" to userEmail,
                            "consumptionDate" to today,
                            "id" to documentReference.id
                        )
                        firestore.collection("consumptionhistory")
                            .add(consumptionHistory)
                            .addOnSuccessListener {
                                Toast.makeText(
                                    context, // Use context here
                                    "Item added successfully",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            .addOnFailureListener { e ->
                                Log.e("Firestore", "Error adding to consumption history", e)
                                Toast.makeText(
                                    context, // Use context here
                                    "Failed to add to consumption history",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                    }
                    .addOnFailureListener { e ->
                        Log.e("Firestore", "Error adding food item", e)
                        Toast.makeText(
                            context, // Use context here
                            "Failed to add food item",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            }) {
                Text("Add")
            }
        } else {
            Button(onClick = { onConfirmClick(id) }) { // Use the lambda for the confirm action
                Text("Confirm")
            }
        }
    }
}