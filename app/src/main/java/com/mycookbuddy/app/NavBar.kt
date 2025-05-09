package com.mycookbuddy.app

import android.content.Context
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun NavBar(context: Context) {
    val navItems = listOf(
        "Home" to MainActivity::class.java,
        "Search" to SearchInventoryActivity::class.java,
        "Manage" to FoodItemListActivity::class.java,
        "Profile" to ProfileActivity::class.java,
        "Help" to HelpActivity::class.java
    )

    val currentActivityName = context::class.java.simpleName
    val selectedIndex = navItems.indexOfFirst { it.second.simpleName == currentActivityName }

    NavigationBar(
        containerColor = Color(0xFFFAFAFA),
        tonalElevation = 10.dp
    ) {
        navItems.forEachIndexed { index, (label, activityClass) ->
            val icon = when (label) {
                "Home" -> Icons.Default.Home
                "Search" -> Icons.Default.Search
                "Manage" -> Icons.AutoMirrored.Filled.List
                "Profile" -> Icons.Default.Person
                "Help" -> Icons.Default.Info
                else -> Icons.Default.Info
            }

            NavigationBarItem(
                selected = index == selectedIndex,
                onClick = {
                    if (context::class.java != activityClass) {
                        context.startActivity(Intent(context, activityClass))
                    }
                },
                icon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = if (index == selectedIndex) MaterialTheme.colorScheme.primary else Color.Gray
                    )
                },
                label = {
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        color = if (index == selectedIndex) MaterialTheme.colorScheme.primary else Color.Gray
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = if (index == selectedIndex) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent
                )
            )
        }
    }
}

