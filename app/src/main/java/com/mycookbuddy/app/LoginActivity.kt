package com.mycookbuddy.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source

class LoginActivity : ComponentActivity() {

    private lateinit var googleSignInClient: GoogleSignInClient
    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        setContent {
            LoginScreen()
        }
    }
@Composable
fun LoadingIndicator(message: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}
    @Composable
    fun LoginScreen() {
        var isLoading by remember { mutableStateOf(false) }
        var loadingMessage by remember { mutableStateOf("Loading...") }

        Box(modifier = Modifier.fillMaxSize()) {
            if (isLoading) {
                LoadingIndicator(message = loadingMessage)
            } else {
                Button(
                    onClick = {
                        isLoading = true
                        loadingMessage = "Signing in..."
                        signIn()
                    },
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Text("Sign In with Google")
                }
            }
        }
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
                val userEmail = account.email ?: return

                firestore.collection("users").document(userEmail).get()
                    .addOnSuccessListener { document ->
                        if (document.exists()) {
                            val privacyPolicyAccepted = document.getBoolean("privacyPolicy") ?: false
                            if (!privacyPolicyAccepted) {
                                setContent {
                                    var showDialog by remember { mutableStateOf(false) }

                                    PrivacyPolicyScreen(
                                        userEmail = userEmail,
                                        onAccept = { updatePrivacyPolicyFlag(userEmail, account) },
                                        onReject = { showDialog = true }
                                    )

                                    if (showDialog) {
                                        RejectConfirmationDialog(
                                            onConfirm = { logOutUser() },
                                            onDismiss = { showDialog = false }
                                        )
                                    }
                                }
                            } else {
                                navigateToNextScreen(account)
                            }
                        } else {
                            saveUserToFirestore(account)
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e("Firestore", "Error checking user data", e)
                        Toast.makeText(this, "Error checking user data", Toast.LENGTH_SHORT).show()
                    }
            }
        } catch (e: Exception) {
            Log.e("GoogleSignIn", "Sign-in failed", e)
            Toast.makeText(this, "Sign-in failed", Toast.LENGTH_SHORT).show()
        }
    }

  @Composable
  fun PrivacyPolicyScreen(
      userEmail: String,
      onAccept: () -> Unit,
      onReject: () -> Unit
  ) {
      var showDialog by remember { mutableStateOf(false) }

      if (showDialog) {
          AlertDialog(
              onDismissRequest = { showDialog = false },
              title = { Text("Confirmation") },
              text = { Text("You will be logged out and presented with the Sign-in screen. Do you want to proceed?") },
              confirmButton = {
                  Button(onClick = {
                      showDialog = false
                      onReject()
                  }) {
                      Text("Reject")
                  }
              },
              dismissButton = {
                  Button(onClick = { showDialog = false }) {
                      Text("Cancel")
                  }
              }
          )
      }

      Column(
          modifier = Modifier.fillMaxSize().padding(16.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center
      ) {
          Text("Privacy Policy", style = MaterialTheme.typography.headlineMedium)
          Spacer(modifier = Modifier.height(16.dp))
          Text("Privacy policy content goes here...")
          Spacer(modifier = Modifier.height(16.dp))
          Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
              Button(onClick = onAccept) {
                  Text("Accept")
              }
              Button(onClick = { showDialog = true }) {
                  Text("Reject")
              }
          }
      }
  }

    @Composable
    fun RejectConfirmationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Confirmation") },
            text = { Text("You will be logged out and presented with the Sign-in screen. Do you want to proceed?") },
            confirmButton = {
                Button(onClick = onConfirm) {
                    Text("Reject")
                }
            },
            dismissButton = {
                Button(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        )
    }

    private fun updatePrivacyPolicyFlag(userEmail: String, account: GoogleSignInAccount) {
        firestore.collection("users").document(userEmail)
            .update("privacyPolicy", true)
            .addOnSuccessListener {
                Toast.makeText(this, "Privacy Policy Accepted", Toast.LENGTH_SHORT).show()
                navigateToNextScreen(account)
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Error updating privacy policy", e)
                Toast.makeText(this, "Error updating privacy policy", Toast.LENGTH_SHORT).show()
            }
    }

    private fun logOutUser() {
        GoogleSignIn.getClient(this, GoogleSignInOptions.DEFAULT_SIGN_IN).signOut()
            .addOnCompleteListener {
                Toast.makeText(this, "You have been logged out", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
    }

    private fun saveUserToFirestore(account: GoogleSignInAccount) {
        val userName = account.displayName
        val userEmail = account.email ?: return

        val user = mutableMapOf<String, Any>(
            "name" to (userName ?: "User"),
            "email" to userEmail,
            "privacyPolicy" to false
        )

        firestore.collection("users").document(userEmail)
            .set(user)
            .addOnSuccessListener {
                Log.d("Firestore", "User data successfully saved!")
                setContent {
                    var showDialog by remember { mutableStateOf(false) }

                    PrivacyPolicyScreen(
                        userEmail = userEmail,
                        onAccept = { updatePrivacyPolicyFlag(userEmail, account) },
                        onReject = { showDialog = true }
                    )

                    if (showDialog) {
                        RejectConfirmationDialog(
                            onConfirm = { logOutUser() },
                            onDismiss = { showDialog = false }
                        )
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Error saving user data", e)
                Toast.makeText(this, "Login failed!", Toast.LENGTH_SHORT).show()
            }
    }

    private fun navigateToNextScreen(account: GoogleSignInAccount) {
        val userEmail = account.email ?: return
        firestore.collection("users").document(userEmail)
            .get(Source.SERVER)
            .addOnSuccessListener { document ->
                val preferences = document.getString("preferences") ?: "NOT_SET"
                if (preferences == "NOT_SET") {
                    startActivity(Intent(this, SettingsActivity::class.java))
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
        finish()
    }

    companion object {
        private const val RC_SIGN_IN = 100
    }
}