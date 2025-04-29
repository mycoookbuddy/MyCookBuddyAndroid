package com.mycookbuddy.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.firestore.FirebaseFirestore

class ProfileActivity : ComponentActivity() {

    private lateinit var googleSignInClient: GoogleSignInClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val userEmail = GoogleSignIn.getLastSignedInAccount(this)?.email ?: "unknown@example.com"
        val userName = GoogleSignIn.getLastSignedInAccount(this)?.displayName ?: "Unknown User"

        // Configure Google Sign-In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        setContent {
            ProfileScreenWithNavBar(
                userName,
                userEmail,
                { signOut() }
            )
        }
    }

    private fun signOut() {
        googleSignInClient.signOut()
            .addOnCompleteListener(this) {
                Toast.makeText(this, "Signed out successfully", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                finish() // Close ProfileActivity
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreenWithNavBar(
    userName: String,
    userEmail: String,
    onSignOutClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile") }
            )
        },
        bottomBar = {
            NavBar(context = LocalContext.current)
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            ProfileScreenContent(
                userName,
                userEmail,
                onSignOutClick
            )
        }
    }
}

@Composable
fun ProfileScreenContent(
    userName: String,
    userEmail: String,
    onSignOutClick: () -> Unit
) {
    val context = LocalContext.current
    val firestore = FirebaseFirestore.getInstance()
    var showDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Hey, $userName!", fontSize = 20.sp, modifier = Modifier.padding(bottom = 8.dp))
        Text(text = userEmail, fontSize = 16.sp, modifier = Modifier.padding(bottom = 16.dp))
        Button(onClick = onSignOutClick) {
            Text(text = "Sign Out")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { showDialog = true }) {
            Text(text = "Delete Account")
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Delete Account") },
            text = { Text("Are you sure you want to delete your account?") },
            confirmButton = {
                TextButton(onClick = {
                    showDialog = false
                    deleteAccount(context, firestore, userEmail)
                }) {
                    Text("Yes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("No")
                }
            }
        )
    }
}

fun deleteAccount(
    context: Context,
    firestore: FirebaseFirestore,
    userEmail: String
) {
    // Sign out from Google
    GoogleSignIn.getClient(context, GoogleSignInOptions.DEFAULT_SIGN_IN).signOut()
        .addOnCompleteListener {
            // Delete user document
            firestore.collection("user").whereEqualTo("email", userEmail).get()
                .addOnSuccessListener { querySnapshot ->
                    querySnapshot.documents.forEach { it.reference.delete() }
                }

            // Delete food items
            firestore.collection("fooditem").whereEqualTo("userEmail", userEmail).get()
                .addOnSuccessListener { querySnapshot ->
                    querySnapshot.documents.forEach { it.reference.delete() }
                }

            // Delete consumption history
            firestore.collection("consumptionhistory").whereEqualTo("userEmail", userEmail).get()
                .addOnSuccessListener { querySnapshot ->
                    querySnapshot.documents.forEach { it.reference.delete() }
                }

            // Show success message and navigate to MainActivity
            Toast.makeText(context, "Account deleted successfully", Toast.LENGTH_SHORT).show()
            val intent = Intent(context, MainActivity::class.java)
            context.startActivity(intent)
            (context as? ComponentActivity)?.finish()
        }
        .addOnFailureListener { e ->
            Toast.makeText(context, "Failed to delete account: ${e.message}", Toast.LENGTH_SHORT).show()
        }
}