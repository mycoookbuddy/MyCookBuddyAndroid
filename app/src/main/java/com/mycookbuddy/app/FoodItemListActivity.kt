package com.mycookbuddy.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.firebase.firestore.FirebaseFirestore
import com.mycookbuddy.app.Utils.Companion.refreshHomeScreen
import com.mycookbuddy.app.ui.theme.MyApplicationTheme
import androidx.activity.compose.rememberLauncherForActivityResult

class FoodItemListActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val user = GoogleSignIn.getLastSignedInAccount(this)
        val userEmail = user?.email ?: "unknown@example.com"
        val userName = user?.displayName ?: "Guest"

        setContent {
            var refreshTrigger by remember { mutableStateOf(false) }

            val addFoodItemLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == RESULT_OK) {
                    refreshTrigger = !refreshTrigger
                }
            }

            MyApplicationTheme {
                FoodItemListScreen(
                    userEmail = userEmail,
                    userName = userName,
                    refreshTrigger = refreshTrigger,
                    onAdd = {
                        val intent = Intent(this, AddFoodItemActivity::class.java)
                        addFoodItemLauncher.launch(intent)
                    },
                    onClick = { name ->
                        val intent = Intent(this, FoodItemDetailActivity::class.java)
                        intent.putExtra("FOOD_ITEM_NAME", name)
                        addFoodItemLauncher.launch(intent) // Updated to trigger refresh
                    },
                    onRefresh = {
                        refreshHomeScreen(this, true)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodItemListScreen(
    userEmail: String,
    userName: String,
    refreshTrigger: Boolean,
    onAdd: () -> Unit,
    onClick: (String) -> Unit,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    var foodItems by remember { mutableStateOf<List<Pair<String, FoodItem>>>(emptyList()) }
    var showDialog by remember { mutableStateOf(false) }
    var toDeleteId by remember { mutableStateOf<String?>(null) }
    val db = FirebaseFirestore.getInstance()

    LaunchedEffect(refreshTrigger) {
        fetchFoodItems(db, userEmail) { foodItems = it }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.background(
                    Brush.horizontalGradient(listOf(Color(0xFF00ACC1), Color(0xFF26C6DA)))
                ),
                title = {
                    Column {
                        Text("Hello, $userName!", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Manage your food items", color = Color.White.copy(alpha = 0.9f), fontSize = MaterialTheme.typography.bodySmall.fontSize)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        },
        bottomBar = {
            NavBar(context = context)
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            Spacer(modifier = Modifier.height(12.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(foodItems) { (id, item) ->
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
                                    Brush.verticalGradient(
                                        colors = listOf(Color(0xFFE0F7FA), Color.White)
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    item.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f)
                                )

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            onClick(item.name)
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit",
                                            tint = Color(0xFF1976D2),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            toDeleteId = id
                                            showDialog = true
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = Color.Red,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDialog && toDeleteId != null) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Delete Food Item") },
            text = { Text("Are you sure you want to delete this item?") },
            confirmButton = {
                TextButton(onClick = {
                    deleteFoodItem(db, toDeleteId!!) {
                        onRefresh()
                        fetchFoodItems(db, userEmail) { foodItems = it }
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

private fun fetchFoodItems(db: FirebaseFirestore, userEmail: String, onResult: (List<Pair<String, FoodItem>>) -> Unit) {
    db.collection("fooditem")
        .whereEqualTo("userEmail", userEmail)
        .get()
        .addOnSuccessListener { result ->
            val items = result.documents.mapNotNull { doc ->
                val item = doc.toObject(FoodItem::class.java)
                if (item != null) doc.id to item else null
            }
            onResult(items)
        }
        .addOnFailureListener { e ->
            Log.e("Firestore", "Error fetching food items", e)
        }
}

private fun deleteFoodItem(db: FirebaseFirestore, id: String, onSuccess: () -> Unit) {
    db.collection("fooditem").document(id).delete()
        .addOnSuccessListener { onSuccess() }
        .addOnFailureListener { Log.e("Firestore", "Delete failed", it) }
}
