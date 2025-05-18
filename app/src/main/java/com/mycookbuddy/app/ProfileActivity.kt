package com.mycookbuddy.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.mycookbuddy.app.ui.theme.MyApplicationTheme

class ProfileActivity : ComponentActivity() {
    private lateinit var googleSignInClient: GoogleSignInClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val user = GoogleSignIn.getLastSignedInAccount(this)
        val userEmail = user?.email ?: "unknown@example.com"
        val userName = user?.displayName ?: "Unknown User"
        val userPhoto = user?.photoUrl

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        setContent {
            MyApplicationTheme {
                var showDeleteDialog by remember { mutableStateOf(false) }

                ProfileScreenWithNavBar(
                    userName = userName,
                    userEmail = userEmail,
                    userPhotoUrl = userPhoto?.toString() ?: "",
                    onEditPreferenceClick = {
                        val intent = Intent(this, SettingsActivity::class.java)
                        intent.putExtra("USER_EMAIL", userEmail)
                        startActivity(intent)
                    },
                    onEditNotificationsClick = {
                        val intent = Intent(this, NotificationSettingsActivity::class.java)
                        intent.putExtra("USER_EMAIL", userEmail)
                        startActivity(intent)
                    },
                    onSignOutClick = {
                        googleSignInClient.signOut().addOnCompleteListener {
                            Toast.makeText(this, "Signed out successfully", Toast.LENGTH_SHORT).show()
                            startActivity(Intent(this, MainActivity::class.java))
                            finish()
                        }
                    },
                    onDeleteAccountClick = {
                        showDeleteDialog = true
                    }
                )

                if (showDeleteDialog) {
                    AlertDialog(
                        onDismissRequest = { showDeleteDialog = false },
                        title = { Text("Delete Account") },
                        text = { Text("Are you sure you want to delete your account?") },
                        confirmButton = {
                            TextButton(onClick = {
                                showDeleteDialog = false
                                deleteAccount(this@ProfileActivity, FirebaseFirestore.getInstance(), userEmail)
                            }) {
                                Text("Yes")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDeleteDialog = false }) {
                                Text("No")
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ProfileScreenWithNavBar(
    userName: String,
    userEmail: String,
    userPhotoUrl: String,
    onEditPreferenceClick: () -> Unit,
    onEditNotificationsClick: () -> Unit,
    onSignOutClick: () -> Unit,
    onDeleteAccountClick: () -> Unit
) {
    Scaffold(
        bottomBar = {
            NavBar(context = LocalContext.current)
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            ProfileScreenContent(
                userName = userName,
                userEmail = userEmail,
                userPhotoUrl = userPhotoUrl,
                onEditPreferenceClick = onEditPreferenceClick,
                onEditNotificationsClick = onEditNotificationsClick,
                onSignOutClick = onSignOutClick,
                onDeleteAccountClick = onDeleteAccountClick
            )
        }
    }
}

@Composable
fun ProfileScreenContent(
    userName: String,
    userEmail: String,
    userPhotoUrl: String,
    onEditPreferenceClick: () -> Unit,
    onEditNotificationsClick: () -> Unit,
    onSignOutClick: () -> Unit,
    onDeleteAccountClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = userPhotoUrl,
            contentDescription = "User Photo",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .border(2.dp, Color.Black, CircleShape)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(userName, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(userEmail, fontSize = 16.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(24.dp))

        ProfileOptionCard(
            icon = Icons.Default.Restaurant,
            label = "Edit Food Preference",
            iconTint = Color(0xFF7B1FA2),
            onClick = onEditPreferenceClick
        )
        Spacer(modifier = Modifier.height(12.dp))
        ProfileOptionCard(
            icon = Icons.Default.Notifications,
            label = "Edit Notifications",
            iconTint = Color(0xFF0288D1),
            onClick = onEditNotificationsClick
        )
        Spacer(modifier = Modifier.height(12.dp))
        ProfileOptionCard(
            icon = Icons.Default.Logout,
            label = "Logout",
            iconTint = Color(0xFFD32F2F),
            onClick = onSignOutClick
        )
        Spacer(modifier = Modifier.height(12.dp))
        ProfileOptionCard(
            icon = Icons.Default.Delete,
            label = "Delete Account",
            iconTint = Color(0xFFE53935),
            onClick = onDeleteAccountClick
        )
    }
}

@Composable
fun ProfileOptionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    iconTint: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFFDFDFD),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = label, tint = iconTint)
                Spacer(modifier = Modifier.width(12.dp))
                Text(label, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
            Icon(
                Icons.Default.ArrowForwardIos,
                contentDescription = "Arrow",
                tint = Color.LightGray,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

fun deleteAccount(context: Context, firestore: FirebaseFirestore, userEmail: String) {
    GoogleSignIn.getClient(context, GoogleSignInOptions.DEFAULT_SIGN_IN).signOut().addOnCompleteListener {
        firestore.collection("users").whereEqualTo("email", userEmail).get()
            .addOnSuccessListener { querySnapshot ->
                querySnapshot.documents.forEach { it.reference.delete() }
            }
        firestore.collection("fooditem").whereEqualTo("userEmail", userEmail).get()
            .addOnSuccessListener { querySnapshot ->
                querySnapshot.documents.forEach { it.reference.delete() }
            }
        firestore.collection("consumptionhistory").whereEqualTo("userEmail", userEmail).get()
            .addOnSuccessListener { querySnapshot ->
                querySnapshot.documents.forEach { it.reference.delete() }
            }
        Toast.makeText(context, "Account deleted successfully", Toast.LENGTH_SHORT).show()
        val intent = Intent(context, MainActivity::class.java)
        context.startActivity(intent)
        (context as? ComponentActivity)?.finish()
    }
        .addOnFailureListener { e ->
            Toast.makeText(context, "Failed to delete account: ${e.message}", Toast.LENGTH_SHORT).show()
        }
}
