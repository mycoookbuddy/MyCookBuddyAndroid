package com.mycookbuddy.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : ComponentActivity() {

    private lateinit var googleSignInClient: GoogleSignInClient
    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure Google Sign-In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // Start the sign-in process
        signIn()
    }

    private fun signIn() {
        val signInIntent = googleSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            handleSignInResult(task)
        }
    }

    private fun handleSignInResult(completedTask: Task<GoogleSignInAccount>) {
        try {
            val account = completedTask.getResult(Exception::class.java)
            Log.d("GoogleSignIn", "Signed in as: ${account?.displayName}")
            if (account != null) {
                saveUserToFirestore(account)
            }
        } catch (e: Exception) {
            Log.e("GoogleSignIn", "Sign-in failed", e)
            Toast.makeText(this, "Sign-in failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveUserToFirestore(account: GoogleSignInAccount) {
        val userName = account.displayName
        val userEmail = account.email
        if (userName != null && userEmail != null) {
            val user = hashMapOf(
                "name" to userName,
                "email" to userEmail,
                "preferences" to "NOT_SET" // Ensure preferences field is initialized
            )

            firestore.collection("users")
                .document(userEmail)
                .set(user)
                .addOnSuccessListener {
                    Log.d("Firestore", "User data successfully written!")
                    Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show()
                    navigateToNextScreen(account) // Call navigateToNextScreen here
                }
                .addOnFailureListener { e ->
                    Log.e("Firestore", "Error writing user data", e)
                    Toast.makeText(this, "Login failed!", Toast.LENGTH_SHORT).show()
                }
        }
    }
    private fun navigateToNextScreen(account: GoogleSignInAccount) {
        val userEmail = account.email ?: return
        firestore.collection("users").document(userEmail).get()
            .addOnSuccessListener { document ->
                val preferences = document.getString("preferences") ?: "NOT_SET"
                if (preferences == "NOT_SET") {
                    val intent = Intent(this, SettingsActivity::class.java).apply {
                        putExtra("USER_EMAIL", userEmail)
                    }
                    startActivity(intent)
                } else {
                    navigateToSuggestedItems(account)
                }
                finish()
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Error fetching user preferences", e)
                Toast.makeText(this, "Error fetching user data", Toast.LENGTH_SHORT).show()
            }
    }

    private fun navigateToSuggestedItems(account: GoogleSignInAccount) {
        val intent = Intent(this, SuggestFoodItemsActivity::class.java).apply {
            putExtra("USER_EMAIL", account.email)
        }
        startActivity(intent)
        finish() // Close LoginActivity
    }

    companion object {
        private const val RC_SIGN_IN = 100
    }
}