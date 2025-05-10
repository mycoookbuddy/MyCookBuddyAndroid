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
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults

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
                SimpleDateFormat(
                    "dd/MM/yyyy",
                    Locale.getDefault()
                ).parse("01/01/1970")
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
    onResult: (List<Pair<String, CommonFoodItem>>) -> Unit
) {
    db.collection("commonfooditem").get()
        .addOnSuccessListener { result ->
            val items = result.documents.mapNotNull { doc ->
                val item = doc.toObject(CommonFoodItem::class.java)
                if (item != null &&
                    item.type in userFoodTypes &&
                    item.cuisines.any { it in userCuisines }
                ) doc.id to item else null
            }
            onResult(items)
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

////    val foodTypes = listOf("Veg", "Non Veg", "Eggy", "Vegan")
    val eatingTypes = listOf("Breakfast", "Lunch", "Dinner")
 //   var selectedFoodTypes by remember { mutableStateOf(foodTypes.toSet()) }
    var selectedEatingTypes by remember { mutableStateOf(eatingTypes.toSet()) }
//    var userCuisines by remember { mutableStateOf(setOf<String>()) }
 //   var selectedCuisines by remember { mutableStateOf(setOf<String>()) }

    var filteredPersonalItems by remember { mutableStateOf(listOf<Pair<String, FoodItem>>()) }
    var allPersonalItems by remember { mutableStateOf(listOf<Pair<String, FoodItem>>()) }


    val loadingState = remember { mutableStateMapOf<String, Boolean>() }
    var commonItems by remember { mutableStateOf(listOf<Pair<String, CommonFoodItem>>()) }
    var userFoodTypes by remember { mutableStateOf(listOf<String>()) }
    var userCuisines by remember { mutableStateOf(listOf<String>()) }

    fun getMealTypeBasedOnTime(): Set<String> {
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (currentHour) {
            in 5..11 -> setOf("Breakfast") // Morning
            in 12..16 -> setOf("Lunch")    // Afternoon
            else -> setOf("Dinner")        // Night
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
                onResult();
            }

    }

    fun applyMealFilterForPersonalFoodItem(item: FoodItem): Boolean =
        (selectedEatingTypes.isNotEmpty() && item.eatingTypes.any { it in selectedEatingTypes })

    fun applyMealFilterForCommonFoodItem(item: CommonFoodItem): Boolean =
        (selectedEatingTypes.isNotEmpty() && item.eatingTypes.any { it in selectedEatingTypes })

    fun fetch() {
        val today = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
        )

            db.collection("fooditem").whereEqualTo("userEmail", userEmail).get()
                .addOnSuccessListener { result ->
                    val items = result.documents.mapNotNull { doc ->
                        val item = doc.toObject(FoodItem::class.java)
                            ?.copy(name = doc.getString("name") ?: "")
                        val last = doc.getString("lastConsumptionDate")?.let {
                            if (it.isNotEmpty()) {
                                SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(it)
                            } else {
                                SimpleDateFormat(
                                    "dd/MM/yyyy",
                                    Locale.getDefault()
                                ).parse("01/01/1970")
                            }
                        }
                        val next = Calendar.getInstance().apply {
                            time = last ?: Date(0)
                            add(Calendar.DAY_OF_YEAR, item?.repeatAfter ?: 0)
                        }.time
                        if (item != null && next < today) doc.id to item else null
                    }
                    filteredPersonalItems = items


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
//                Toast.makeText(context, "Marked as consumed", Toast.LENGTH_SHORT).show()
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
                fetch()
            }
    }

    var isLoading by remember { mutableStateOf(true) }
    // Fetch data only once
    LaunchedEffect(Unit) {
        isLoading = true
        fetchUserPreferences {
            fetchPersonalFoodItems(db, userEmail) { personal ->
                allPersonalItems = personal
                filterEligiblePersonalFoodItemsForSuggestion(
                    personal)  { personalFiltered ->
                    filteredPersonalItems = personalFiltered
                    fetchCommonFoodItems(db, userFoodTypes, userCuisines) { common ->
                        commonItems = filterCommonItemsNotInFoodItems(common, allPersonalItems)
                        isLoading = false
                    }

                }
            }

        }


    }

    if (isLoading) {
        // Show loading indicator
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
            val message = "Hello " + userName + "! " + "Good " + getWelcomeMessageBasedOnTime() + " Today's " + getMealTypeBasedOnTime().first() + " Suggestions!"
            CenterAlignedTopAppBar(title = { Text(message) })
            val meal = getMealTypeBasedOnTime().first()
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
        LazyColumn(
            contentPadding = padding,
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
//            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Personal Items Section
            item {
                GradientHeader("Your Food Items")
            }
            items(filteredPersonalItems.filter { applyMealFilterForPersonalFoodItem(it.second) }) { (id, item) ->
                Spacer(modifier = Modifier.height(16.dp))
                FoodItemCard(
                    item,
                    false,
                    onConfirm = { confirmPersonalFoodItemConsumption(id) },
                    onAdd = {},
                    isLoading = loadingState[item.name] == true
                )
            }
            // Common Items Section
            item {
                GradientHeader("Common Food Items")
            }
            items(commonItems.filter { applyMealFilterForCommonFoodItem(it.second) }) { (_, item) ->
                Spacer(modifier = Modifier.height(16.dp))
                CommonFoodItemCard(
                    item = item,
                    onConfirm = { confirmCommonFoodItemConsumption(item) },
                    isLoading = false
                )
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
fun FoodItemCard(
    item: FoodItem,
    isGeneral: Boolean,
    onConfirm: () -> Unit,
    onAdd: () -> Unit,
    isLoading: Boolean
) {
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
                            Color(0xFFE0F7FA), // light cyan
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
                    if (item.lastConsumptionDate.isNotEmpty()) {
                        Text(
                            "Consumed on: ${item.lastConsumptionDate}",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    } else if (!isGeneral) {
                        Text(
                            "Never consumed",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        if (isGeneral) {
                            IconButton(onClick = onAdd) {
                                Icon(
                                    Icons.Default.AddCircle,
                                    contentDescription = "Add to Personal",
                                    tint = Color(0xFF26A69A)
                                )
                            }
                        }
                        IconButton(onClick = onConfirm) {
                            Icon(
                                Icons.Default.Restaurant,
                                contentDescription = "Ate",
                                tint = Color(0xFFEF5350)
                            )
                        }
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
    isLoading: Boolean
) {
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
                            Color(0xFFE0F7FA), // light cyan
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

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {

                        IconButton(onClick = onConfirm) {
                            Icon(
                                Icons.Default.Restaurant,
                                contentDescription = "Ate",
                                tint = Color(0xFFEF5350)
                            )
                        }
                    }
                }
            }
        }
    }
}

fun Set<String>.toggle(item: String): Set<String> =
    if (contains(item)) this - item else this + item