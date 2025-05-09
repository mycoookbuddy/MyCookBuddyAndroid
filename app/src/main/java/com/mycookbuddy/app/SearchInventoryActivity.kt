package com.mycookbuddy.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.firebase.firestore.FirebaseFirestore
import com.mycookbuddy.app.Utils.Companion.refreshHomeScreen
import kotlinx.coroutines.launch

class SearchInventoryActivity : ComponentActivity() {
    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val user = GoogleSignIn.getLastSignedInAccount(this)
        val userEmail = user?.email

        setContent {
            if (userEmail != null) {
                SearchInventoryScreen(userEmail)
            }
        }
    }

    @Composable
    fun SearchInventoryScreen(userEmail: String) {
        val scope = rememberCoroutineScope()
        var allItems by remember { mutableStateOf<List<CommonFoodItem>>(emptyList()) }
        var filteredItems by remember { mutableStateOf<List<CommonFoodItem>>(emptyList()) }
        var selectedMealTypes by remember { mutableStateOf(setOf("Breakfast", "Lunch", "Snacks", "Dinner")) }
        var selectedFoodTypes by remember { mutableStateOf(setOf("Veg", "Non Veg", "Eggy", "Vegan")) }
        var selectedCuisines by remember { mutableStateOf<Set<String>>(emptySet()) }
        var cuisines by remember { mutableStateOf<List<String>>(emptyList()) }
        var searchText by remember { mutableStateOf(TextFieldValue("")) }

        LaunchedEffect(Unit) {
            // Fetch cuisines from /cuisine collection
            firestore.collection("cuisine").get()
                .addOnSuccessListener { result ->
                    cuisines = result.documents.mapNotNull { it.getString("name") }
                    selectedCuisines = cuisines.toSet() // Select all cuisines by default
                }

            // Fetch all items from /commonfooditem
            firestore.collection("commonfooditem").get()
                .addOnSuccessListener { commonItems ->
                    val commonFoodItems = commonItems.documents.mapNotNull { it.toObject(CommonFoodItem::class.java) }

                    // Fetch user's existing items from /fooditem
                    firestore.collection("fooditem")
                        .whereEqualTo("userEmail", userEmail)
                        .get()
                        .addOnSuccessListener { userItems ->
                            val userFoodNames = userItems.documents.mapNotNull { it.getString("name") }
                            allItems = commonFoodItems.filter { it.name !in userFoodNames }
                            filteredItems = allItems.sortedBy { it.name }
                        }
                }
        }

        Column(Modifier.fillMaxSize().padding(16.dp)) {
            // Search Text Box
            BasicTextField(
                value = searchText,
                onValueChange = {
                    searchText = it
                    updateFilteredItems(allItems, selectedMealTypes, selectedFoodTypes, selectedCuisines, searchText.text) { filtered ->
                        filteredItems = filtered
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                decorationBox = { innerTextField ->
                    Box(Modifier.padding(8.dp)) {
                        if (searchText.text.isEmpty()) {
                            Text("Search items...")
                        }
                        innerTextField()
                    }
                }
            )

            // Meal Type Filters
            Text("Meal Type Filters")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf("Breakfast", "Lunch", "Snacks", "Dinner").forEach { mealType ->
                    FilterChip(
                        selected = selectedMealTypes.contains(mealType),
                        onClick = {
                            selectedMealTypes = toggleSelection(selectedMealTypes, mealType)
                            updateFilteredItems(allItems, selectedMealTypes, selectedFoodTypes, selectedCuisines, searchText.text) { filtered ->
                                filteredItems = filtered
                            }
                        },
                        label = { Text(mealType) }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Food Type Filters
            Text("Food Type Filters")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf("Veg", "Non Veg", "Eggy", "Vegan").forEach { foodType ->
                    FilterChip(
                        selected = selectedFoodTypes.contains(foodType),
                        onClick = {
                            selectedFoodTypes = toggleSelection(selectedFoodTypes, foodType)
                            updateFilteredItems(allItems, selectedMealTypes, selectedFoodTypes, selectedCuisines, searchText.text) { filtered ->
                                filteredItems = filtered
                            }
                        },
                        label = { Text(foodType) }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Cuisine Filters
            Text("Cuisine Filters")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                cuisines.forEach { cuisine ->
                    FilterChip(
                        selected = selectedCuisines.contains(cuisine),
                        onClick = {
                            selectedCuisines = toggleSelection(selectedCuisines, cuisine)
                            updateFilteredItems(allItems, selectedMealTypes, selectedFoodTypes, selectedCuisines, searchText.text) { filtered ->
                                filteredItems = filtered
                            }
                        },
                        label = { Text(cuisine) }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Display Filtered Items
            Column(Modifier.verticalScroll(rememberScrollState())) {
                filteredItems.forEach { item ->
                    Row(
                        Modifier.fillMaxWidth().padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(item.name)
                        Button(onClick = {
                            scope.launch {
                                addItemToUserCollection(userEmail, item)
                            }
                        }) {
                            Icon(Icons.Default.Add, contentDescription = "Add")
                            Text("Add")
                        }
                    }
                }
            }
        }
    }

    private fun updateFilteredItems(
        allItems: List<CommonFoodItem>,
        selectedMealTypes: Set<String>,
        selectedFoodTypes: Set<String>,
        selectedCuisines: Set<String>,
        searchText: String,
        onUpdate: (List<CommonFoodItem>) -> Unit
    ) {
        val filtered = allItems.filter { item ->
            (item.type in selectedFoodTypes) &&
                    item.eatingTypes.any { it in selectedMealTypes } &&
                    (item.cuisines.any { it in selectedCuisines }) &&
                    (item.name.contains(searchText, ignoreCase = true))
        }.sortedBy { it.name }
        onUpdate(filtered)
    }

    private fun toggleSelection(set: Set<String>, item: String): Set<String> {
        return if (set.contains(item)) set - item else set + item
    }

    private fun addItemToUserCollection(userEmail: String, item: CommonFoodItem) {
        val newItem = PersonalFoodItem(
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