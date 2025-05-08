package com.mycookbuddy.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val account = GoogleSignIn.getLastSignedInAccount(this)
        if (account != null) {
            // User is logged in, navigate to SuggestedFoodItemsActivity
            val intent = Intent(this, SuggestFoodItemsActivity::class.java)
            startActivity(intent)
            finish() // Close MainActivity
        } else {
            navigateToLogin()
        }
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
    }
}

