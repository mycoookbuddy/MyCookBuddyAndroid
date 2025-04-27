package com.mycookbuddy.app

import android.content.Context
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun NavBar(
    context: Context

) {
    NavigationBar {
        val navItems = listOf(
            "Home" to MainActivity::class.java,
            "Manage" to FoodItemListActivity::class.java,
            "Profile" to ProfileActivity::class.java,
            "Help" to HelpActivity::class.java
        )

        navItems.forEach { (label, activityClass) ->
            NavigationBarItem(
                selected = false, // No selection logic needed
                onClick = {
                    val intent = Intent(context, activityClass).apply {

                    }
                    context.startActivity(intent)
                },
                icon = {
                    Icon(
                        imageVector = when (label) {
                            "Home" -> Icons.Default.Home
                            "Manage" -> Icons.AutoMirrored.Filled.List
                            "Profile" -> Icons.Default.Person
                            "Help" -> Icons.Default.Info
                            else -> Icons.Default.Info
                        },
                        contentDescription = label
                    )
                },
                label = { Text(label) }
            )
        }
    }
}