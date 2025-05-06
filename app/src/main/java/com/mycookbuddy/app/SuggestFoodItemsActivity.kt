package com.mycookbuddy.app

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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.firebase.firestore.FirebaseFirestore
import com.mycookbuddy.app.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class SuggestFoodItemsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val userEmail = GoogleSignIn.getLastSignedInAccount(this)?.email ?: "unknown@example.com"
        setContent {
            MyApplicationTheme {
                SuggestFoodItemsScreen(userEmail)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SuggestFoodItemsScreen(userEmail: String) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState()
    var showSheet by remember { mutableStateOf(false) }

    val foodTypes = listOf("Veg", "Non Veg", "Eggy", "Vegan")
    val eatingTypes = listOf("Breakfast", "Lunch", "Snacks", "Dinner")
    var selectedFoodTypes by remember { mutableStateOf(foodTypes.toSet()) }
    var selectedEatingTypes by remember { mutableStateOf(eatingTypes.toSet()) }

    var personalItems by remember { mutableStateOf(listOf<Pair<String, FoodItem>>()) }
    var generalItems by remember { mutableStateOf(listOf<Pair<String, FoodItem>>()) }
    var personalNames by remember { mutableStateOf(setOf<String>()) }

    val loadingState = remember { mutableStateMapOf<String, Boolean>() }

    fun filter(item: FoodItem): Boolean =
        (selectedFoodTypes.isEmpty() || selectedFoodTypes.contains(item.type)) &&
                (selectedEatingTypes.isEmpty() || item.eatingTypes.any { it in selectedEatingTypes })

    fun fetch() {
        val today = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
        )
        db.collection("users").document(userEmail).get().addOnSuccessListener { userDoc ->
            if (userDoc.getString("preferences") != "SET") return@addOnSuccessListener
            val foodPrefs = userDoc["foodTypes"] as? List<String> ?: listOf()
            val cuisines = userDoc["cuisines"] as? List<String> ?: listOf()

            db.collection("fooditem").whereEqualTo("userEmail", userEmail).get()
                .addOnSuccessListener { result ->
                    val items = result.documents.mapNotNull { doc ->
                        val item = doc.toObject(FoodItem::class.java)?.copy(name = doc.getString("name") ?: "")
                        val last = doc.getString("lastConsumptionDate")?.let {
                            if (it.isNotEmpty()) {
                                SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(it)
                            } else {
                                SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse("01/01/1970")
                            }                        }
                        val next = Calendar.getInstance().apply {
                            time = last ?: Date(0)
                            add(Calendar.DAY_OF_YEAR, item?.repeatAfter ?: 0)
                        }.time
                        if (item != null && next < today) doc.id to item else null
                    }
                    personalItems = items
                    personalNames = items.map { it.second.name }.toSet()

                    db.collection("commonfooditem").get().addOnSuccessListener { common ->
                        generalItems = common.documents.mapNotNull { doc ->
                            val item = doc.toObject(FoodItem::class.java)?.copy(name = doc.getString("name") ?: "")
                            if (item != null && foodPrefs.contains(item.type) && item.cuisines.any { it in cuisines }) {
                                doc.id to item
                            } else null
                        }.filter { it.second.name !in personalNames }
                    }
                }
        }
    }

    fun confirmItem(id: String) {
        loadingState[id] = true
        val today = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
        db.collection("fooditem").document(id)
            .update("lastConsumptionDate", today)
            .addOnSuccessListener {
                Toast.makeText(context, "Marked as consumed", Toast.LENGTH_SHORT).show()
                loadingState.remove(id)
                fetch()
            }
    }

    fun addGeneral(item: FoodItem) {
        loadingState[item.name] = true
        val newItem = item.copy(userEmail = userEmail, repeatAfter = 7)
        db.collection("fooditem").add(newItem).addOnSuccessListener {
            Toast.makeText(context, "Added to personal", Toast.LENGTH_SHORT).show()
            personalNames = personalNames + item.name
            generalItems = generalItems.filter { it.second.name != item.name }
            loadingState.remove(item.name)
            fetch()
        }
    }

    fun consumeGeneral(item: FoodItem) {
        loadingState[item.name] = true
        val today = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
        db.collection("fooditem").add(item.copy(userEmail = userEmail, lastConsumptionDate = today)).addOnSuccessListener {
            Toast.makeText(context, "Marked as consumed", Toast.LENGTH_SHORT).show()
            generalItems = generalItems.filter { it.second.name != item.name }
            loadingState.remove(item.name)
        }
    }

    LaunchedEffect(Unit) { fetch() }

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
            CenterAlignedTopAppBar(title = { Text("Today's Suggestions") })
        },
        bottomBar = {
            NavBar(context = LocalContext.current)
        }
    ) { padding ->
        LazyColumn(
            contentPadding = padding,
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { GradientHeader("Personal") }
            items(personalItems.filter { filter(it.second) }) { (id, item) ->
                FoodItemCard(item, false, onConfirm = { confirmItem(id) }, onAdd = {}, isLoading = loadingState[item.name] == true)
            }

            item { GradientHeader("General") }
            items(generalItems.filter { filter(it.second) }) { (_, item) ->
                FoodItemCard(item, true, onConfirm = { consumeGeneral(item) }, onAdd = { addGeneral(item) }, isLoading = loadingState[item.name] == true)
            }
        }
    }

    AnimatedVisibility(showSheet, enter = fadeIn(), exit = fadeOut()) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }, sheetState = sheetState) {
            Column(Modifier.padding(16.dp)) {
                Text("Food Type", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    foodTypes.forEach {
                        FilterChip(selected = selectedFoodTypes.contains(it), onClick = {
                            selectedFoodTypes = selectedFoodTypes.toggle(it)
                        }, label = { Text(it) })
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("When to Eat", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    eatingTypes.forEach {
                        FilterChip(selected = selectedEatingTypes.contains(it), onClick = {
                            selectedEatingTypes = selectedEatingTypes.toggle(it)
                        }, label = { Text(it) })
                    }
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        scope.launch { sheetState.hide() }
                        showSheet = false
                        fetch()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00ACC1))
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Apply", color = Color.White)
                }
            }
        }
    }
}

@Composable
fun GradientHeader(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.horizontalGradient(listOf(Color(0xFF00ACC1), Color(0xFF26C6DA))),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp)
    ) {
        Text(text, color = Color.White, fontSize = 18.sp)
    }
}

@Composable
fun FoodItemCard(item: FoodItem, isGeneral: Boolean, onConfirm: () -> Unit, onAdd: () -> Unit, isLoading: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(6.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium)
                if (item.lastConsumptionDate.isNotEmpty()) {
                    Text("Last: ${item.lastConsumptionDate}", fontSize = 12.sp, color = Color.Gray)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    if (isGeneral) {
                        IconButton(onClick = onAdd) {
                            Icon(Icons.Default.AddCircle, contentDescription = "Add to Personal", tint = Color(0xFF26A69A))
                        }
                    }
                    IconButton(onClick = onConfirm) {
                        Icon(Icons.Default.Restaurant, contentDescription = "Ate", tint = Color(0xFFEF5350))
                    }
                }
            }
        }
    }
}

fun Set<String>.toggle(item: String): Set<String> =
    if (contains(item)) this - item else this + item