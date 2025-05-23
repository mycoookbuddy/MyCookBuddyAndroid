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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.mycookbuddy.app.ui.theme.MyApplicationTheme
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.text.style.TextOverflow

class SuggestFoodItemsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val user = GoogleSignIn.getLastSignedInAccount(this)
        val userEmail = user?.email ?: "unknown@example.com"
        val userName = user?.displayName ?: "Guest"
        setContent {
            MyApplicationTheme {
                SuggestFoodItemsScreen(userEmail, userName)
            }
        }
    }
}

// --- FILTER FUNCTIONS ---

fun filterCommonItemsNotInFoodItems(
    commonItems: List<Pair<String, CommonFoodItem>>,
    foodItems: List<Pair<String, FoodItem>>
): List<Pair<String, CommonFoodItem>> {
    val foodItemNames = foodItems.map { it.second.name }.toSet()
    return commonItems.filter { it.second.name !in foodItemNames }
}

fun filterEligiblePersonalFoodItemsForSuggestion(
    personalItems: List<Pair<String, FoodItem>>,
    onResult: (List<Pair<String, FoodItem>>) -> Unit
) {
    val today = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
    )
    val items = personalItems.mapNotNull { personalFoodItem ->
        val last = personalFoodItem.second.lastConsumptionDate.let {
            if (it.isNotEmpty()) {
                SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(it)
            } else {
                SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse("01/01/1970")
            }
        }
        val next = Calendar.getInstance().apply {
            time = last ?: Date(0)
            personalFoodItem.second.repeatAfter?.let { add(Calendar.DAY_OF_YEAR, it) }
        }.time
        if (next < today) personalFoodItem else null
    }
    onResult(items)
}

fun fetchPersonalFoodItems(
    db: FirebaseFirestore,
    userEmail: String,
    onResult: (List<Pair<String, FoodItem>>) -> Unit
) {
    db.collection("fooditem").whereEqualTo("userEmail", userEmail).get()
        .addOnSuccessListener { personalResult ->
            val personalItems = personalResult.documents.mapNotNull { doc ->
                val item = doc.toObject(FoodItem::class.java)
                if (item != null) doc.id to item else null
            }
            onResult(personalItems)
        }
}

// --- PAGINATED COMMON FOOD FETCH ---

fun fetchCommonFoodItems(
    db: FirebaseFirestore,
    userFoodTypes: List<String>,
    userCuisines: List<String>,
    lastVisibleDoc: DocumentSnapshot?,
    pageSize: Long = 50,
    onResult: (List<Pair<String, CommonFoodItem>>, DocumentSnapshot?) -> Unit
) {
    val collection = db.collection("commonfooditem")
    var query = when {
        userFoodTypes.isEmpty() && userCuisines.isEmpty() -> collection
        userFoodTypes.isEmpty() -> collection.whereArrayContainsAny("cuisines", userCuisines.take(10))
        userCuisines.isEmpty() -> collection.whereIn("type", userFoodTypes.take(10))
        else -> collection
            .whereIn("type", userFoodTypes.take(10))
            .whereArrayContainsAny("cuisines", userCuisines.take(10))
    }
    query = query.limit(pageSize)
    if (lastVisibleDoc != null) {
        query = query.startAfter(lastVisibleDoc)
    }
    query.get().addOnSuccessListener { result ->
        val items = result.documents.mapNotNull { doc ->
            doc.toObject(CommonFoodItem::class.java)?.let { doc.id to it }
        }
        val newLastVisible = result.documents.lastOrNull()
        onResult(items, newLastVisible)
    }
}

@Composable
fun CommonFoodItemsGrid(
    items: List<CommonFoodItem>,
    onConfirm: (CommonFoodItem) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items) { item ->
            // For CommonFoodItemsGrid and FoodItemsGrid
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(2.dp)
                ) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .fillMaxWidth(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Button(
                        onClick = { onConfirm(item) }, // or onConfirm(id) for FoodItemsGrid
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 2.dp)
                    ) {
                        Text("Go for it")
                    }
                }
            }
        }
    }
}

@Composable
fun FoodItemsGrid(
    items: List<Pair<String, FoodItem>>,
    onConfirm: (String) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items) { (id, item) ->
            // For CommonFoodItemsGrid and FoodItemsGrid
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(2.dp)
                ) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .fillMaxWidth(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Button(
                        onClick = { onConfirm(id) }, // or onConfirm(id) for FoodItemsGrid
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 2.dp)
                    ) {
                        Text("Go for it")
                    }
                }
            }
        }
    }
}
@Composable
fun GradientHeader(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
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
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SuggestFoodItemsScreen(userEmail: String, userName: String) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState()
    var showSheet by remember { mutableStateOf(false) }

    val eatingTypes = listOf("Breakfast", "Lunch", "Dinner")
    var selectedEatingTypes by remember { mutableStateOf(setOf<String>()) }

    var filteredPersonalItems by remember { mutableStateOf(listOf<Pair<String, FoodItem>>()) }
    var allPersonalItems by remember { mutableStateOf(listOf<Pair<String, FoodItem>>()) }

    val loadingState = remember { mutableStateMapOf<String, Boolean>() }
    var commonItems by remember { mutableStateOf(listOf<Pair<String, CommonFoodItem>>()) }
    var userFoodTypes by remember { mutableStateOf(listOf<String>()) }
    var userCuisines by remember { mutableStateOf(listOf<String>()) }

    // Pagination states
    var lastVisibleDoc by remember { mutableStateOf<DocumentSnapshot?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(true) }

    fun getMealTypeBasedOnTime(): Set<String> {
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (currentHour) {
            in 5..11 -> setOf("Breakfast")
            in 12..16 -> setOf("Lunch")
            else -> setOf("Dinner")
        }
    }

    fun getWelcomeMessageBasedOnTime(): String {
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (currentHour) {
            in 5..11 -> ("Morning")
            in 12..16 -> ("Afternoon")
            else -> ("Evening")
        }
    }

    fun fetchUserPreferences(
        onResult: () -> Unit
    ) {
        db.collection("users").document(userEmail).get()
            .addOnSuccessListener { document ->
                userFoodTypes = document.get("foodTypes") as? List<String> ?: emptyList()
                userCuisines = document.get("cuisines") as? List<String> ?: emptyList()
                onResult()
            }
    }

    fun applyMealFilterForPersonalFoodItem(item: FoodItem): Boolean =
        (selectedEatingTypes.isNotEmpty() && item.eatingTypes.any { it in selectedEatingTypes })

    fun applyMealFilterForCommonFoodItem(item: CommonFoodItem): Boolean =
        (selectedEatingTypes.isNotEmpty() && item.eatingTypes.any { it in selectedEatingTypes })

    fun fetchInitial() {
        isLoading = true
        fetchUserPreferences {
            fetchPersonalFoodItems(db, userEmail) { personal ->
                allPersonalItems = personal
                filterEligiblePersonalFoodItemsForSuggestion(personal) { personalFiltered ->
                    filteredPersonalItems = personalFiltered
                    // Fetch first page of common items
                    fetchCommonFoodItems(
                        db,
                        userFoodTypes,
                        userCuisines,
                        null,
                        50
                    ) { common, lastDoc ->
                        val filtered = filterCommonItemsNotInFoodItems(common, allPersonalItems)
                        commonItems = filtered.shuffled().take(15)
                        lastVisibleDoc = lastDoc
                        hasMore = lastDoc != null && common.isNotEmpty()
                        isLoading = false
                    }
                }
            }
        }
    }

    fun fetchMore() {
        if (isLoadingMore || !hasMore) return
        isLoadingMore = true
        fetchCommonFoodItems(
            db,
            userFoodTypes,
            userCuisines,
            lastVisibleDoc,
            50
        ) { common, lastDoc ->
            val filtered = filterCommonItemsNotInFoodItems(common, allPersonalItems)
            val newItems = filtered.shuffled().take(15)
            commonItems = commonItems + newItems
            lastVisibleDoc = lastDoc
            hasMore = lastDoc != null && common.isNotEmpty()
            isLoadingMore = false
        }
    }

    fun confirmCommonFoodItemConsumption(
        commonFoodItem: CommonFoodItem
    ) {
        val foodItem = FoodItem(
            name = commonFoodItem.name,
            type = commonFoodItem.type,
            eatingTypes = commonFoodItem.eatingTypes,
            lastConsumptionDate = SimpleDateFormat(
                "dd/MM/yyyy",
                Locale.getDefault()
            ).format(Date()),
            userEmail = userEmail
        )

        db.collection("fooditem")
            .add(foodItem)
            .addOnSuccessListener {
                Toast.makeText(context, "Food item added successfully!", Toast.LENGTH_SHORT).show()
                fetchInitial() // Refresh the screen
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Failed to add food item: ${e.message}", Toast.LENGTH_SHORT)
                    .show()
            }
    }

    fun confirmPersonalFoodItemConsumption(id: String) {
        loadingState[id] = true
        val today = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
        db.collection("fooditem").document(id)
            .update("lastConsumptionDate", today)
            .addOnSuccessListener {
                val meal = getMealTypeBasedOnTime().first()
                val itemName =
                    filteredPersonalItems.find { it.first == id }?.second?.name ?: "your meal"
                val message = "Hope you enjoyed $itemName in $meal!"
                val spannable = android.text.SpannableString(message)

                val itemStart = message.indexOf(itemName)
                if (itemStart >= 0) {
                    spannable.setSpan(
                        android.text.style.StyleSpan(android.graphics.Typeface.ITALIC),
                        itemStart,
                        itemStart + itemName.length,
                        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }

                val mealStart = message.indexOf(meal)
                if (mealStart >= 0) {
                    spannable.setSpan(
                        android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                        mealStart,
                        mealStart + meal.length,
                        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }

                Toast.makeText(context, spannable, Toast.LENGTH_SHORT).show()
                loadingState.remove(id)
                fetchInitial()
            }
    }

    // Fetch data only once
    LaunchedEffect(Unit) {
        fetchInitial()
    }

    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else {
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
                val message =
                    "Hello $userName! Good ${getWelcomeMessageBasedOnTime()} Today's ${getMealTypeBasedOnTime().first()} Suggestions!"
                CenterAlignedTopAppBar(title = { Text(message) })
                val meal = getMealTypeBasedOnTime().first()
                if (selectedEatingTypes.isEmpty())
                    selectedEatingTypes = getMealTypeBasedOnTime()
                val greeting = getWelcomeMessageBasedOnTime()

                TopAppBar(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.horizontalGradient(
                                listOf(Color(0xFF00ACC1), Color(0xFF26C6DA))
                            )
                        ),
                    title = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Hello, $userName!",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "Good $greeting — Enjoy your $meal suggestions!",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent
                    )
                )
            },
            bottomBar = {
                NavBar(context = LocalContext.current)
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Inside the Column in Scaffold content
                // Replace LazyColumn with Column in your Scaffold content

                    if (filteredPersonalItems.any { applyMealFilterForPersonalFoodItem(it.second) }) {
                        val sortedPersonalItems = filteredPersonalItems
                            .filter { applyMealFilterForPersonalFoodItem(it.second) }
                            .sortedBy {
                                val dateStr = it.second.lastConsumptionDate
                                if (dateStr.isEmpty()) {
                                    Date(0) // epoch as fallback for empty date
                                } else {
                                    SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(dateStr) ?: Date(0)
                                }
                            }
                        GradientHeader(
                            text = "Smart Suggestions",
                            modifier = Modifier.padding(top = 12.dp)
                        )
                        FoodItemsGrid(
                            items = sortedPersonalItems.filter {
                                applyMealFilterForPersonalFoodItem(
                                    it.second
                                )
                            },
                            onConfirm = { confirmPersonalFoodItemConsumption(it) }
                        )
                    }
                GradientHeader(
                    text = "Popular Recipes",
                    modifier = Modifier.padding(top = 12.dp)
                )

                    val filteredPopularRecipes =
                        commonItems.filter { applyMealFilterForCommonFoodItem(it.second) }
                    if (filteredPopularRecipes.isEmpty()) {
                        hasMore = false
                        Text(
                            "I am sorry, no suggestions for today",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            color = Color.Gray,
                            fontSize = 16.sp
                        )
                    } else {
                        CommonFoodItemsGrid(
                            items = filteredPopularRecipes.map { it.second },
                            onConfirm = { confirmCommonFoodItemConsumption(it) }
                        )
                    }
                    if (hasMore) {
                        Button(
                            onClick = { fetchMore() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            enabled = !isLoadingMore
                        ) {
                            if (isLoadingMore) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            } else {
                                Text("More")
                            }
                        }
                    }
                }
            }
        }
        AnimatedVisibility(showSheet, enter = fadeIn(), exit = fadeOut()) {
            ModalBottomSheet(onDismissRequest = { showSheet = false }, sheetState = sheetState) {
                Column(Modifier.padding(16.dp)) {
                    Text("When to Eat", style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        eatingTypes.forEach {
                            FilterChip(selected = selectedEatingTypes.contains(it), onClick = {
                                selectedEatingTypes = selectedEatingTypes.toggle(it)
                            }, label = { Text(it) })
                        }
                    }
                }
            }
        }
    }


fun Set<String>.toggle(item: String): Set<String> =
        if (contains(item)) this - item else this + item
