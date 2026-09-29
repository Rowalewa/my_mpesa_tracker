package com.example.my_mpesa_tracker.ui.dashboard

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.example.my_mpesa_tracker.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import androidx.core.content.edit

// ── Local username store — separate from the Google account, since a display ──
// ── name shouldn't require sign-in. Reuses the app's existing prefs file. ──

object UserProfileStore {
    private const val PREFS = "pesalyzer_prefs"
    private const val KEY_USERNAME = "username"

    fun getUsername(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_USERNAME, "") ?: ""

    fun setUsername(context: Context, name: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit { putString(KEY_USERNAME, name.trim()) }
    }
}

// ── Google Sign-In via Credential Manager, exchanged for a Firebase session. ──
// ── This is what will authorize Drive backup/restore once that's wired up.  ──

/** Checks for a genuinely working connection, not just "connected to a network" — */
/** catches the wifi-with-no-real-internet case, not only fully offline. Shared with */
/** DriveBackup.kt, since Drive authorization hits the same class of failure. */
fun isOnline(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return false
    val network = cm.activeNetwork ?: return false
    val capabilities = cm.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

object AuthManager {
    fun currentUser(): FirebaseUser? = FirebaseAuth.getInstance().currentUser

    suspend fun signInWithGoogle(context: Context): Result<FirebaseUser> {
        if (!isOnline(context)) {
            return Result.failure(Exception("No internet connection. Check your connection and try again."))
        }
        return try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(context.getString(R.string.default_web_client_id))
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()
            val response = CredentialManager.create(context).getCredential(context, request)
            val credential = response.credential

            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val firebaseCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                val authResult = FirebaseAuth.getInstance().signInWithCredential(firebaseCredential).awaitResult()
                val user = authResult.user
                if (user != null) Result.success(user) else Result.failure(Exception("Sign-in returned no user"))
            } else {
                Result.failure(Exception("Unrecognized credential type"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signOut(context: Context) {
        FirebaseAuth.getInstance().signOut()
        try {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        } catch (_: Exception) {
            // Non-critical — the Firebase sign-out above is what actually matters.
        }
    }
}

/** Bridges a Play Services [com.google.android.gms.tasks.Task] into a suspend call. */
/** Not private — DriveBackup.kt reuses this for the same Task-based Play Services APIs. */
suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitResult(): T =
    suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
    }

// ── Account dialog — sign in, or view/edit the local display name and sign out ──

@Composable
fun AccountDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var user by remember { mutableStateOf(AuthManager.currentUser()) }
    var username by remember { mutableStateOf(UserProfileStore.getUsername(context)) }
    var isSigningIn by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardDark,
        title = { Text("Account", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (user == null) {
                    Text(
                        "Sign in with Google to enable cloud backup later — this is optional, " +
                                "Pesalyzer works fully offline without it.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                    if (errorMessage.isNotBlank()) {
                        Text(errorMessage, color = NegativeRed, fontSize = 12.sp)
                    }
                    Button(
                        onClick = {
                            errorMessage = ""
                            isSigningIn = true
                            scope.launch {
                                val result = AuthManager.signInWithGoogle(context)
                                isSigningIn = false
                                result.onSuccess { signedInUser -> user = signedInUser }
                                result.onFailure { err -> errorMessage = err.message ?: "Sign-in failed" }
                            }
                        },
                        enabled = !isSigningIn,
                        colors = ButtonDefaults.buttonColors(containerColor = MpesaGreen),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isSigningIn) "Signing in..." else "Sign in with Google", color = Color.White)
                    }
                } else {
                    var isEditingName by remember { mutableStateOf(false) }
                    var nameDraft by remember { mutableStateOf(username) }
                    val initial = (username.ifBlank { user?.email.orEmpty() })
                        .firstOrNull()?.uppercaseChar()?.toString() ?: "?"

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .background(MpesaGreen, RoundedCornerShape(26.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(initial, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        }
                        Column(Modifier.weight(1f)) {
                            if (!isEditingName) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        username.ifBlank { "Add your name" },
                                        color = if (username.isBlank()) TextSecondary else Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { nameDraft = username; isEditingName = true },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit name", tint = TextSecondary, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                            Text(user?.email ?: "—", color = TextSecondary, fontSize = 12.sp)
                        }
                    }

                    if (isEditingName) {
                        OutlinedTextField(
                            value = nameDraft,
                            onValueChange = { nameDraft = it },
                            label = { Text("Display name", color = TextSecondary) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MpesaGreen,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = MpesaGreen
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { isEditingName = false }) {
                                Text("Cancel", color = TextSecondary)
                            }
                            Button(
                                onClick = {
                                    username = nameDraft
                                    UserProfileStore.setUsername(context, nameDraft)
                                    isEditingName = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MpesaGreen)
                            ) {
                                Text("Save", color = Color.White)
                            }
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                    DriveBackupSection()

                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                    TextButton(
                        onClick = {
                            scope.launch {
                                AuthManager.signOut(context)
                                user = null
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Sign out", color = NegativeRed)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = MpesaGreen)
            }
        }
    )
}
