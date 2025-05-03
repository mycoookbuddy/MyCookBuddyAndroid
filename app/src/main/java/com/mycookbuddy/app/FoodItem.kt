package com.mycookbuddy.app

data class FoodItem(
    val name: String = "",
    val userEmail: String = "",
    val type: String = "",
    val eatingTypes: List<String> = listOf(),
    val lastConsumptionDate: String = "",
    val repeatAfter: Int = 0,
    val cuisines: List<String> = listOf()
)
