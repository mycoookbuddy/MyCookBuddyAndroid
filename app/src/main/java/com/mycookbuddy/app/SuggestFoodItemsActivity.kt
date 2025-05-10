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
import androidx.compose.ui.draw.shadow

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
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SuggestFoodItemsScreen(userEmail: String, userName: String) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState()
    var showSheet by remember { mutableStateOf(false) }

////    val foodTypes = listOf("Veg", "Non Veg", "Eggy", "Vegan")
    val eatingTypes = listOf("Breakfast", "Lunch", "Snacks", "Dinner")
 //   var selectedFoodTypes by remember { mutableStateOf(foodTypes.toSet()) }
    var selectedEatingTypes by remember { mutableStateOf(eatingTypes.toSet()) }
//    var userCuisines by remember { mutableStateOf(setOf<String>()) }
 //   var selectedCuisines by remember { mutableStateOf(setOf<String>()) }

    var personalItems by remember { mutableStateOf(listOf<Pair<String, FoodItem>>()) }
    var generalItems by remember { mutableStateOf(listOf<Pair<String, FoodItem>>()) }
    var personalNames by remember { mutableStateOf(setOf<String>()) }

    val loadingState = remember { mutableStateMapOf<String, Boolean>() }

    fun getMealTypeBasedOnTime(): Set<String> {
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (currentHour) {
            in 5..11 -> setOf("Breakfast") // Morning
            in 12..16 -> setOf("Lunch")    // Afternoon
            in 17..20 -> setOf("Snacks")   // Evening
            else -> setOf("Dinner")        // Night
        }
    }

    fun getWelcomeMessageBasedOnTime(): String {
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (currentHour) {
            in 5..11 -> ("Morning")
            in 12..16 -> ("Afternoon")
            in 17..20 -> ("Evening")
            else -> ("Evening")
        }
    }

//    fun filterGeneral(item: FoodItem): Boolean =
//        (selectedFoodTypes.isNotEmpty() && selectedFoodTypes.contains(item.type)) &&
//                (selectedEatingTypes.isNotEmpty() && item.eatingTypes.any { it in selectedEatingTypes }) &&
//                (selectedCuisines.isNotEmpty() && item.cuisines.any { it in selectedCuisines })

    fun filterPersonal(item: FoodItem): Boolean =
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
                    personalItems = items
                    personalNames = items.map { it.second.name }.toSet()

                }

    }

    fun confirmItem(id: String) {
        loadingState[id] = true
        val today = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
        db.collection("fooditem").document(id)
            .update("lastConsumptionDate", today)
            .addOnSuccessListener {
//                Toast.makeText(context, "Marked as consumed", Toast.LENGTH_SHORT).show()
                val meal = getMealTypeBasedOnTime().first()
                val itemName = personalItems.find { it.first == id }?.second?.name ?: "your meal"
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
        db.collection("fooditem").add(item.copy(userEmail = userEmail, lastConsumptionDate = today))
            .addOnSuccessListener {
                Toast.makeText(context, "Marked as consumed", Toast.LENGTH_SHORT).show()
                generalItems = generalItems.filter { it.second.name != item.name }
                loadingState.remove(item.name)
            }
    }

    var isLoading by remember { mutableStateOf(true) }
    // Fetch data only once
    LaunchedEffect(Unit) {
        isLoading = true
        fetchPersonalFoodItems(db, userEmail) { personal ->
            filterEligiblePersonalFoodItemsForSuggestion(
                personal)  { personalFiltered ->
                personalItems = personalFiltered
                isLoading = false
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

            items(personalItems.filter { filterPersonal(it.second) }) { (id, item) ->
                Spacer(modifier = Modifier.height(16.dp))
                FoodItemCard(
                    item,
                    false,
                    onConfirm = { confirmItem(id) },
                    onAdd = {},
                    isLoading = loadingState[item.name] == true
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

fun Set<String>.toggle(item: String): Set<String> =
    if (contains(item)) this - item else this + item