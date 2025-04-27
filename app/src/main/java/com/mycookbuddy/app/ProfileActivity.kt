package com.mycookbuddy.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions

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
            ProfileScreen(
                userName = userName,
                userEmail = userEmail,
                onSignOutClick = { signOut() }
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

@Composable
fun ProfileScreen(userName: String, userEmail: String, onSignOutClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Hey, $userName!" , fontSize = 20.sp, modifier = Modifier.padding(bottom = 8.dp))
        Text(text = userEmail, fontSize = 16.sp, modifier = Modifier.padding(bottom = 16.dp))
        Button(onClick = onSignOutClick) {
            Text(text = "Sign Out")
        }
    }
}