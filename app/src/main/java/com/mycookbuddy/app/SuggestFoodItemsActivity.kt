package com.mycookbuddy.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.mycookbuddy.app.ui.theme.MyApplicationTheme
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource

class SuggestFoodItemsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val user = GoogleSignIn.getLastSignedInAccount(this)
        val userEmail = user?.email ?: "unknown@example.com"
        val userName = user?.displayName ?: "Guest"
        setContent {
            MyApplicationTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    // ✅ Background image from drawable
                    Image(
                        painter = painterResource(id = R.drawable.background),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                    SuggestFoodItemsScreen(userEmail, userName)
                }
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
            add(Calendar.DAY_OF_YEAR, personalFoodItem.second.repeatAfter)
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
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
                    fetchCommonFoodItems(db, userFoodTypes, userCuisines, null, 50) { common, lastDoc ->
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
        fetchCommonFoodItems(db, userFoodTypes, userCuisines, lastVisibleDoc, 50) { common, lastDoc ->
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
            lastConsumptionDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()),
            userEmail = userEmail
        )

        db.collection("fooditem")
            .add(foodItem)
            .addOnSuccessListener {
                Toast.makeText(context, "Food item added successfully!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Failed to add food item: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    fun confirmPersonalFoodItemConsumption(id: String) {
        loadingState[id] = true
        val today = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
        db.collection("fooditem").document(id)
            .update("lastConsumptionDate", today)
            .addOnSuccessListener {
                val meal = getMealTypeBasedOnTime().first()
                val itemName = filteredPersonalItems.find { it.first == id }?.second?.name ?: "your meal"
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
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) { CircularProgressIndicator() }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.horizontalGradient(
                            listOf(Color(0xFF00ACC1), Color(0xFF26C6DA))
                        )
                    ),
                title = {
                    Column {
                        Text(
                            "Hello, $userName!",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Good ${getGreeting()} — Enjoy your ${getCurrentMeal()} suggestions!",
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
        floatingActionButton = {
            FloatingActionButton(onClick = { showSheet = true }) {
                Icon(Icons.Default.FilterList, contentDescription = "Filter")
            }
        },
        bottomBar = {
            NavBar(context = context)
        }
    ) { paddingValues ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = paddingValues,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement   = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Personal header (spans all three columns)
            item(span = { GridItemSpan(3) }) {
                GradientHeader("Your Food Items")
            }
            // Personal items
            items(filteredPersonalItems.filter { (_, item) ->
                item.eatingTypes.any { it in selectedEatingTypes }
            }) { (id, item) ->
                FoodItemCard(
                    item      = item,
                    isGeneral = false,
                    onConfirm = { confirmPersonalConsumption(id, db, context, loadingState) },
                    onAdd     = {},
                    isLoading = loadingState[id] == true,
                    modifier  = Modifier.fillMaxWidth()
                )
            }

            // Common header
            item(span = { GridItemSpan(3) }) {
                Spacer(Modifier.height(24.dp))
                GradientHeader("Common Food Items")
            }
            // Common items
            items(commonItems.filter { (_, item) ->
                item.eatingTypes.any { it in selectedEatingTypes }
            }) { (id, item) ->
                CommonFoodItemCard(
                    item      = item,
                    onConfirm = { confirmCommonConsumption(id, item, db, context, loadingState) },
                    isLoading = loadingState[id] == true,
                    modifier  = Modifier.fillMaxWidth()
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

    AnimatedVisibility(showSheet, enter = fadeIn(), exit = fadeOut()) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState       = sheetState
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("When to Eat", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    eatingTypes.forEach { type ->
                        FilterChip(
                            selected = type in selectedEatingTypes,
                            onClick  = {
                                selectedEatingTypes = if (type in selectedEatingTypes)
                                    selectedEatingTypes - type
                                else
                                    selectedEatingTypes + type
                            },
                            label = { Text(type) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GradientHeader(text: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.horizontalGradient(
                    listOf(Color(0xFF00ACC1), Color(0xFF26C6DA))
                ),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp)
    ) {
        Text(text, color = Color.White, fontSize = 18.sp)
    }
}

@Composable
fun FoodItemCard(
    item: FoodItem,
    isGeneral: Boolean,
    onConfirm: () -> Unit,
    onAdd: () -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val animatedScale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "consume-button-scale"
    )

    Card(
        modifier = modifier
            .width(180.dp)
            .height(120.dp)
            .padding(8.dp)
            .shadow(8.dp, RoundedCornerShape(16.dp), ambientColor = Color.LightGray),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color.LightGray),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.TopStart)
            ) {
                // 🟢 Food Name
                Text(
                    text = item.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                if (item.lastConsumptionDate.isNotEmpty()) {
                    Text(
                        text = "Consumed on: ${item.lastConsumptionDate}",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                } else if (!isGeneral) {
                    Text(
                        text = "Never consumed",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            }

            // 🟢 Consume Button (bottom center)
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(20.dp)
                        .align(Alignment.BottomCenter),
                    strokeWidth = 2.dp
                )
            } else {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .scale(animatedScale)
                        .shadow(6.dp, RoundedCornerShape(8.dp))
                        .background(
                            brush = Brush.horizontalGradient(
                                listOf(Color(0xFF388E3C), Color(0xFF66BB6A))
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .height(32.dp)
                        .width(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    TextButton(
                        onClick = onConfirm,
                        modifier = Modifier.fillMaxSize(),
                        interactionSource = interactionSource,
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("Consume", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}


@Composable
fun CommonFoodItemCard(
    item: CommonFoodItem,
    onConfirm: () -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val animatedScale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "consume-button-scale"
    )

    Card(
        modifier = modifier
            .width(180.dp)
            .height(120.dp)
            .padding(8.dp)
            .shadow(8.dp, RoundedCornerShape(16.dp), ambientColor = Color.LightGray),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color.LightGray),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            // 🟢 Food Name
            Text(
                text = item.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopStart)
            )

            // 🟢 Bottom Center Gradient Button
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(20.dp)
                        .align(Alignment.BottomCenter),
                    strokeWidth = 2.dp
                )
            } else {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .scale(animatedScale)
                        .shadow(6.dp, RoundedCornerShape(8.dp))
                        .background(
                            brush = Brush.horizontalGradient(
                                listOf(Color(0xFF388E3C), Color(0xFF66BB6A))
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .height(32.dp)
                        .width(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    TextButton(
                        onClick = onConfirm,
                        modifier = Modifier.fillMaxSize(),
                        interactionSource = interactionSource,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("Consume", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

fun Set<String>.toggle(item: String): Set<String> =
    if (contains(item)) this - item else this + item