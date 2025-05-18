package com.mycookbuddy.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source

class MainActivity : ComponentActivity() {

    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val account = GoogleSignIn.getLastSignedInAccount(this)
        if (account != null) {
            // User is logged in, check Firestore for privacy policy and preferences
            checkLoggedInUserDetails(account.email ?: return)
        } else {
            // User is not logged in, navigate to LoginActivity
            navigateToLogin()
        }
    }

    private fun checkLoggedInUserDetails(userEmail: String) {
        firestore.collection("users").document(userEmail)
            .get(Source.SERVER)
            .addOnSuccessListener { document ->

                    val privacyPolicyAccepted = document.getBoolean("privacyPolicy") ?: false
                    if (!privacyPolicyAccepted) {
                        // Launch PrivacyPolicyActivity
                        val intent = Intent(this, PrivacyPolicyActivity::class.java)
                        startActivityForResult(intent, RC_PRIVACY_POLICY)
                    } else {
                        val preferences = document.getString("preferences") ?: "NOT_SET"
                        if (preferences != "SET") {
                            val intent = Intent(this, OnboardingActivity::class.java)
                            startActivityForResult(intent, RC_ONBOARDING)
                        } else {
                            // Launch SuggestedFoodItemsActivity
                            navigateToSuggestedItems(userEmail)
                        }
                    }

            }
            .addOnFailureListener { e ->
                // Handle Firestore error
                e.printStackTrace()
                navigateToLogin()
            }
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        startActivityForResult(intent, RC_LOGIN)
    }

    private fun navigateToSuggestedItems(userEmail: String) {
        val intent = Intent(this, SuggestFoodItemsActivity::class.java).apply {
            putExtra("USER_EMAIL", userEmail)
        }
        startActivity(intent)
        finish()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if ((requestCode == RC_LOGIN || requestCode == RC_PRIVACY_POLICY || requestCode == RC_ONBOARDING) && resultCode == RESULT_OK) {
            // Recheck user details after login
            val account = GoogleSignIn.getLastSignedInAccount(this)
            if (account != null) {
                checkLoggedInUserDetails(account.email ?: return)
            }
        }
    }

    companion object {
        private const val RC_LOGIN = 101
        private const val RC_PRIVACY_POLICY = 102
        private const val RC_ONBOARDING = 103
    }
}