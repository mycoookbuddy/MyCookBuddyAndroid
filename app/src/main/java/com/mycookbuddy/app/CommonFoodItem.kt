package com.mycookbuddy.app

data class CommonFoodItem(
    val name: String = "",
    val type: String = "",
    val eatingTypes: List<String> = listOf(),
    val cuisines: List<String> = listOf(),
)
