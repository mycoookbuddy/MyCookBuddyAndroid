package com.mycookbuddy.app

data class PersonalFoodItem(
    val name: String = "",
    val userEmail: String = "",
    val type: String = "",
    val eatingTypes: List<String> = listOf(),
    val lastConsumptionDate: String = "",
    val repeatAfter: Int = 0,
)
