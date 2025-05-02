package com.mycookbuddy.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.rememberAsyncImagePainter
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.google.android.gms.auth.api.signin.*
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
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
            var isLoading by remember { mutableStateOf(false) }
            var loadingMessage by remember { mutableStateOf("Loading...") }

            Box(modifier = Modifier.fillMaxSize()) {
                if (isLoading) {
                    FoodAnimatedLogo(isLoading = isLoading, message = loadingMessage)
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
        val userEmail = account.email ?: return

        val user = mutableMapOf<String, Any>(
            "name" to (userName ?: "User"),
            "email" to userEmail
        )

        firestore.collection("users").document(userEmail).get(Source.SERVER)
            .addOnSuccessListener { document ->
                if (!document.contains("preferences")) {
                    user["preferences"] = "NOT_SET"
                }

                firestore.collection("users")
                    .document(userEmail)
                    .set(user, SetOptions.merge())
                    .addOnSuccessListener {
                        Log.d("Firestore", "User data successfully merged!")
                        Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show()
                        navigateToNextScreen(account)
                    }
                    .addOnFailureListener { e ->
                        Log.e("Firestore", "Error saving user data", e)
                        Toast.makeText(this, "Login failed!", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Error checking existing user data", e)
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
                    startActivity(Intent(this, SettingsActivity::class.java).apply {
                        putExtra("USER_EMAIL", userEmail)
                    })
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

@Composable
fun FoodAnimatedLogo(isLoading: Boolean, message: String = "Loading...") {
    if (isLoading) {
        val context = LocalContext.current

        val imageLoader = ImageLoader.Builder(context)
            .components {
                if (android.os.Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()

        val painter = rememberAsyncImagePainter(
            model = "android.resource://${context.packageName}/raw/loading",
            imageLoader = imageLoader
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = painter,
                    contentDescription = "Loading",
                    modifier = Modifier.size(150.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}
