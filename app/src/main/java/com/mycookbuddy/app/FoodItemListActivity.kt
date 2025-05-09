package com.mycookbuddy.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.firebase.firestore.FirebaseFirestore
import com.mycookbuddy.app.Utils.Companion.refreshHomeScreen

class FoodItemListActivity : ComponentActivity() {
    private lateinit var addFoodItemLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val userEmail = GoogleSignIn.getLastSignedInAccount(this)?.email ?: "unknown@example.com"

        addFoodItemLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val newFoodItemId = result.data?.getStringExtra("NEW_FOOD_ITEM_ID")
                if (newFoodItemId != null) {
                    refreshFoodItems(userEmail)
                }
            }
        }

        setContent {
            FoodItemListScreenWithNavBar(
                userEmail,
                onItemClick = { foodItemName ->
                    val intent = Intent(this, FoodItemDetailActivity::class.java).apply {
                        putExtra("FOOD_ITEM_NAME", foodItemName)
                    }
                    this.startActivity(intent)
                },
                onAddItemClick = {
                    val intent = Intent(this, AddFoodItemActivity::class.java).apply {
                        putExtra("USER_EMAIL", userEmail)
                    }
                    addFoodItemLauncher.launch(intent)
                },
                onRefreshHomeScreen = { refresh ->
                    refreshHomeScreen(this, refresh)
                }
            )
        }
    }

    private fun refreshFoodItems(userEmail: String) {
        val firestore = FirebaseFirestore.getInstance()
        fetchFoodItems(firestore, userEmail) { items ->
            // Logic to scroll to the newly added item can be implemented here if needed
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodItemListScreenWithNavBar(
    userEmail: String,
    onItemClick: (String) -> Unit,
    onAddItemClick: () -> Unit,
    onRefreshHomeScreen: (Boolean) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add/Edit/Delete Your Food Items") }
            )
        },
        bottomBar = {
            NavBar(context = LocalContext.current)
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            FoodItemListScreenContent(
                userEmail,
                onItemClick,
                onAddItemClick,
                onRefreshHomeScreen
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodItemListScreenContent(
    userEmail: String,
    onItemClick: (String) -> Unit,
    onAddItemClick: () -> Unit,
    onRefreshHomeScreen: (Boolean) -> Unit
) {
    var foodItems by remember { mutableStateOf<List<Pair<String, PersonalFoodItem>>>(emptyList()) }
    var showDialog by remember { mutableStateOf(false) }
    var foodItemIdToDelete by remember { mutableStateOf<String?>(null) }
    val firestore = FirebaseFirestore.getInstance()

    // Fetch food items
    LaunchedEffect(userEmail) {
        fetchFoodItems(firestore, userEmail) { items ->
            foodItems = items
        }
    }

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
                        foodItemIdToDelete = id
                        showDialog = true
                    }) {
                        Text("Delete")
                    }
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Delete Food Item") },
            text = { Text("Are you sure you want to delete this food item?") },
            confirmButton = {
                TextButton(onClick = {
                    foodItemIdToDelete?.let { id ->
                        deleteFoodItem(firestore, id) {
                            onRefreshHomeScreen(true)
                            fetchFoodItems(firestore, userEmail) { items ->
                                foodItems = items
                            }
                        }
                    }
                    showDialog = false
                }) {
                    Text("Yes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("No")
                }
            }
        )
    }
}


private fun fetchFoodItems(
    firestore: FirebaseFirestore,
    userEmail: String,
    onResult: (List<Pair<String, PersonalFoodItem>>) -> Unit
) {
    firestore.collection("fooditem")
        .whereEqualTo("userEmail", userEmail)
        .get()
        .addOnSuccessListener { result ->
            val items = result.documents.mapNotNull { doc ->
                val foodItem = doc.toObject(PersonalFoodItem::class.java)
                if (foodItem != null) doc.id to foodItem else null
            }
            onResult(items)
        }
        .addOnFailureListener { e ->
            Log.e("Firestore", "Error fetching food items", e)
        }
}

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