package com.mycookbuddy.app

import android.icu.util.Calendar
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.firebase.firestore.FirebaseFirestore
import com.mycookbuddy.app.Utils.Companion.refreshHomeScreen
import com.mycookbuddy.app.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Search


class SearchInventoryActivity : ComponentActivity() {
    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val user = GoogleSignIn.getLastSignedInAccount(this)
        val userEmail = user?.email ?: "unknown@example.com"
        val userName = user?.displayName ?: "Guest"

        setContent {
            MyApplicationTheme {
                SearchInventoryScreen(userEmail, userName)
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
    @Composable
    fun SearchInventoryScreen(userEmail: String, userName: String) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val sheetState = rememberModalBottomSheetState()
        var showSheet by remember { mutableStateOf(false) }

        var allItems by remember { mutableStateOf<List<FoodItem>>(emptyList()) }
        var filteredItems by remember { mutableStateOf<List<FoodItem>>(emptyList()) }
        var searchText by remember { mutableStateOf(TextFieldValue("")) }
        var cuisines by remember { mutableStateOf<List<String>>(emptyList()) }
        var selectedMealTypes by remember { mutableStateOf(setOf("Breakfast", "Lunch", "Snacks", "Dinner")) }
        var selectedFoodTypes by remember { mutableStateOf(setOf("Veg", "Non Veg", "Eggy", "Vegan")) }
        var selectedCuisines by remember { mutableStateOf(setOf<String>()) }

        LaunchedEffect(Unit) {
            firestore.collection("cuisine").get()
                .addOnSuccessListener { result ->
                    cuisines = result.documents.mapNotNull { it.getString("name") }
                    selectedCuisines = cuisines.toSet()
                }

            firestore.collection("commonfooditem").get()
                .addOnSuccessListener { commonItems ->
                    val commonFoodItems = commonItems.documents.mapNotNull { it.toObject(FoodItem::class.java) }
                    firestore.collection("fooditem")
                        .whereEqualTo("userEmail", userEmail)
                        .get()
                        .addOnSuccessListener { userItems ->
                            val userFoodNames = userItems.documents.mapNotNull { it.getString("name") }
                            allItems = commonFoodItems.filter { it.name !in userFoodNames }
                            updateFilteredItems(allItems, selectedMealTypes, selectedFoodTypes, selectedCuisines, searchText.text) {
                                filteredItems = it
                            }
                        }
                }
        }

        Scaffold(
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showSheet = true },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.FilterList, contentDescription = "Filter")
                }
            },
            topBar = {
                val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
                val greeting = when (hour) {
                    in 5..11 -> "Morning"
                    in 12..16 -> "Afternoon"
                    else -> "Evening"
                }
                TopAppBar(
                    modifier = Modifier.background(
                        brush = Brush.horizontalGradient(
                            listOf(Color(0xFF00ACC1), Color(0xFF26C6DA))
                        )
                    ),
                    title = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text("Hello, $userName!", color = Color.White, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(2.dp))
                            Text("Good $greeting — Search new food items", color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
                )
            },
            bottomBar = {
                NavBar(context = context)
            }
        ) { padding ->
            Column(modifier = Modifier.padding(padding).padding(horizontal = 16.dp)) {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = {
                        searchText = it
                        updateFilteredItems(allItems, selectedMealTypes, selectedFoodTypes, selectedCuisines, searchText.text) {
                            filteredItems = it
                        }
                    },
                    label = { Text("Search items...", fontWeight = FontWeight.SemiBold) },
                    placeholder = { Text("Type to search...", color = Color.Gray) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00ACC1),
                        unfocusedBorderColor = Color.LightGray,
                        focusedLeadingIconColor = Color(0xFF00ACC1),
                        unfocusedLeadingIconColor = Color.Gray
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )


                Spacer(Modifier.height(12.dp))

                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(filteredItems) { item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            elevation = CardDefaults.cardElevation(6.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                Color(0xFFE0F7FA),
                                                Color.White
                                            )
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(item.name, style = MaterialTheme.typography.titleMedium)
                                    }
                                    IconButton(onClick = {
                                        scope.launch {
                                            addItemToUserCollection(userEmail, item)
                                            allItems = allItems.filter { it.name != item.name }
                                            updateFilteredItems(allItems, selectedMealTypes, selectedFoodTypes, selectedCuisines, searchText.text) {
                                                filteredItems = it
                                            }
                                        }
                                    }) {
                                        Icon(Icons.Default.AddCircle, contentDescription = "Add", tint = Color(0xFF26A69A))
                                    }
                                }
                            }
                        }
                    }}
            }
        }



AnimatedVisibility(showSheet, enter = fadeIn(), exit = fadeOut()) {
    ModalBottomSheet(onDismissRequest = { showSheet = false }, sheetState = sheetState) {
        Column(Modifier.padding(16.dp)) {
            Text("Meal Types", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Breakfast", "Lunch", "Snacks", "Dinner").forEach { type ->
                    FilterChip(
                        selected = selectedMealTypes.contains(type),
                        onClick = {
                            selectedMealTypes = toggleSelection(selectedMealTypes, type)
                            updateFilteredItems(allItems, selectedMealTypes, selectedFoodTypes, selectedCuisines, searchText.text) {
                                filteredItems = it
                            }
                        },
                        label = { Text(type) }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("Food Types", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Veg", "Non Veg", "Eggy", "Vegan").forEach { type ->
                    FilterChip(
                        selected = selectedFoodTypes.contains(type),
                        onClick = {
                            selectedFoodTypes = toggleSelection(selectedFoodTypes, type)
                            updateFilteredItems(allItems, selectedMealTypes, selectedFoodTypes, selectedCuisines, searchText.text) {
                                filteredItems = it
                            }
                        },
                        label = { Text(type) }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("Cuisines", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                cuisines.forEach { cuisine ->
                    FilterChip(
                        selected = selectedCuisines.contains(cuisine),
                        onClick = {
                            selectedCuisines = toggleSelection(selectedCuisines, cuisine)
                            updateFilteredItems(allItems, selectedMealTypes, selectedFoodTypes, selectedCuisines, searchText.text) {
                                filteredItems = it
                            }
                        },
                        label = { Text(cuisine) }
                    )
                }
            }
        }
    }
}
}

private fun updateFilteredItems(
    allItems: List<FoodItem>,
    selectedMealTypes: Set<String>,
    selectedFoodTypes: Set<String>,
    selectedCuisines: Set<String>,
    searchText: String,
    onUpdate: (List<FoodItem>) -> Unit
) {
    val filtered = allItems.filter { item ->
        (item.type in selectedFoodTypes) &&
                item.eatingTypes.any { it in selectedMealTypes } &&
                item.cuisines.any { it in selectedCuisines } &&
                item.name.contains(searchText, ignoreCase = true)
    }.sortedBy { it.name }
    onUpdate(filtered)
}

private fun toggleSelection(set: Set<String>, item: String): Set<String> {
    return if (set.contains(item)) set - item else set + item
}

    private fun addItemToUserCollection(userEmail: String, item: CommonFoodItem) {
        val newItem = Fooditem(
            name = item.name,
            userEmail = userEmail,
            type = item.type,
            eatingTypes = item.eatingTypes,
            lastConsumptionDate = "",
            repeatAfter = 7
        )
        firestore.collection("fooditem")
            .whereEqualTo("userEmail", userEmail)
            .whereEqualTo("name", item.name)
            .get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) {
                    firestore.collection("fooditem").add(newItem)
                        .addOnSuccessListener {
                            Toast.makeText(this, "${item.name} added successfully", Toast.LENGTH_SHORT).show()
                            refreshHomeScreen(this,true)
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Failed to add item: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    Toast.makeText(this, "${item.name} is already in your collection", Toast.LENGTH_SHORT).show()
                }
            }
    }
}


