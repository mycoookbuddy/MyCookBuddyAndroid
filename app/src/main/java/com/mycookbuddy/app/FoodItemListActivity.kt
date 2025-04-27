package com.mycookbuddy.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.firebase.firestore.FirebaseFirestore

class FoodItemListActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val userEmail = GoogleSignIn.getLastSignedInAccount(this)?.email ?: "unknown@example.com";

        setContent {
            FoodItemListScreen(
                userEmail = userEmail,
                onItemClick = { foodItemName ->
                    val intent = Intent(this, FoodItemDetailActivity::class.java).apply {

                        putExtra("FOOD_ITEM_NAME", foodItemName)
                    }
                    startActivity(intent)
                },
                onAddItemClick = {
                    val intent = Intent(this, AddFoodItemActivity::class.java).apply {
                        putExtra("USER_EMAIL", userEmail)
                    }
                    startActivity(intent)
                },
                onRefreshHomeScreen = { refresh ->
                    refreshHomeScreen(refresh)
                }
            )
        }
    }
    private fun refreshHomeScreen(refresh: Boolean)
    {
        val sharedPreferences = getSharedPreferences("MyCookBuddyPrefs", MODE_PRIVATE)
        sharedPreferences.edit() { putBoolean("shouldRefresh", refresh) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodItemListScreen(
    userEmail: String,
    onItemClick: (String) -> Unit,
    onAddItemClick: () -> Unit,
    onRefreshHomeScreen: (Boolean) -> Unit

) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Food Items") },
                actions = {
                    IconButton(onClick = onAddItemClick) {
                        Icon(Icons.Filled.Add, contentDescription = "Add Food Item")
                    }
                }
            )
        }
    ) { innerPadding ->
        var foodItems by remember { mutableStateOf<List<Pair<String, FoodItem>>>(emptyList()) }
        val firestore = FirebaseFirestore.getInstance()

        // Fetch food items
        LaunchedEffect(userEmail) {
            fetchFoodItems(firestore, userEmail) { items ->
                foodItems = items
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(foodItems) { (id, foodItem) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = foodItem.name,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onItemClick(foodItem.name) }
                            .padding(end = 8.dp)
                    )
                    Button(onClick = {
                        // Delete the food item and refresh the list
                        deleteFoodItem(firestore, id) {
                            onRefreshHomeScreen(true)
                            fetchFoodItems(firestore, userEmail) { items ->
                                foodItems = items
                            }
                        }
                    }) {
                        Text("Delete")
                    }
                }
            }
        }
    }
}

// Function to fetch food items
private fun fetchFoodItems(
    firestore: FirebaseFirestore,
    userEmail: String,
    onResult: (List<Pair<String, FoodItem>>) -> Unit
) {
    firestore.collection("fooditem")
        .whereEqualTo("userEmail", userEmail)
        .get()
        .addOnSuccessListener { result ->
            val items = result.documents.mapNotNull { doc ->
                val foodItem = doc.toObject(FoodItem::class.java)
                if (foodItem != null) doc.id to foodItem else null
            }
            onResult(items)
        }
        .addOnFailureListener { e ->
            Log.e("Firestore", "Error fetching food items", e)
        }
}

// Function to delete a food item
private fun deleteFoodItem(
    firestore: FirebaseFirestore,
    documentId: String,
    onComplete: () -> Unit
) {
    firestore.collection("fooditem")
        .document(documentId)
        .delete()
        .addOnSuccessListener {
            Log.d("Firestore", "Food item deleted successfully")
            onComplete()
        }
        .addOnFailureListener { e ->
            Log.e("Firestore", "Error deleting food item", e)
        }
}