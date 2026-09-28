package com.example.my_mpesa_tracker.ui.dashboard

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.my_mpesa_tracker.data.db.AppDatabase
import com.example.my_mpesa_tracker.data.model.MpesaTransaction
import com.example.my_mpesa_tracker.data.model.TransactionType
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// ── Drive appdata authorization. This is a separate consent from Google ──
// ── Sign-In (that's identity; this is permission to use a Google API) — ──
// ── Google's current guidance keeps the two as distinct flows.          ──

private const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"

object DriveAuthManager {
    private const val PREFS = "pesalyzer_prefs"
    private const val KEY_DRIVE_AUTHORIZED = "drive_authorized"

    fun isAuthorized(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DRIVE_AUTHORIZED, false)

    fun markAuthorized(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DRIVE_AUTHORIZED, true).apply()
        AutoBackupScheduler.scheduleIfEnabled(context)
    }
}

// ── Backup and restore: export the full transaction history to a stable JSON   ──
// ── shape (independent of the Room schema, so future migrations can't break    ──
// ── old backups), upload it to the app's private Drive appdata folder, and on  ──
// ── restore merge it back in using the app's existing mpesaCode+amount+isDebit ──
// ── dedup key so nothing gets duplicated against whatever's already local.     ──

private const val BACKUP_FILE_NAME = "pesalyzer_backup.json"
private const val DRIVE_FILES_URL = "https://www.googleapis.com/drive/v3/files"
private const val DRIVE_UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files"
private const val BACKUP_SCHEMA_VERSION = 1

/** found=false is a normal outcome (a fresh account, or a first-ever phone) — not a failure. */
data class RestoreResult(val found: Boolean, val added: Int, val skipped: Int)

object BackupManager {
    private const val PREFS = "pesalyzer_prefs"
    private const val KEY_LAST_BACKUP = "last_backup_time"

    fun getLastBackupTime(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_LAST_BACKUP, 0L)

    private fun setLastBackupTime(context: Context, time: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(KEY_LAST_BACKUP, time).apply()
    }

    suspend fun backupNow(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!isOnline(context)) {
                return@withContext Result.failure(Exception("No internet connection. Check your connection and try again."))
            }

            val token = freshAccessToken(context)
                ?: return@withContext Result.failure(Exception("Backup access needs to be re-enabled — tap Enable above."))

            val transactions = AppDatabase.getInstance(context).transactionDao().allTransactions().first()
            val json = exportToJson(transactions)

            val existingId = findExistingBackupId(token)
            if (existingId != null) deleteFile(token, existingId)
            uploadNewBackup(token, json)

            setLastBackupTime(context, System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Downloads the backup, and inserts only what isn't already present locally — reusing
     * the exact same mpesaCode+amount+isDebit key the SMS-sync path already dedupes on
     * (TransactionDao.countByCodeAndDetails), so restore can't disagree with live sync
     * about what counts as a duplicate. Existing local transactions are never touched.
     */
    suspend fun restoreNow(context: Context): Result<RestoreResult> = withContext(Dispatchers.IO) {
        try {
            if (!isOnline(context)) {
                return@withContext Result.failure(Exception("No internet connection. Check your connection and try again."))
            }

            val token = freshAccessToken(context)
                ?: return@withContext Result.failure(Exception("Backup access needs to be re-enabled — tap Enable above."))

            val fileId = findExistingBackupId(token)
                ?: return@withContext Result.success(RestoreResult(found = false, added = 0, skipped = 0))

            val backupTransactions = parseBackupJson(downloadFile(token, fileId))

            val dao = AppDatabase.getInstance(context).transactionDao()
            val existingKeys = dao.allTransactions().first()
                .mapTo(HashSet()) { dedupKey(it.mpesaCode, it.amount, it.isDebit) }

            val newOnes = backupTransactions.filter { dedupKey(it.mpesaCode, it.amount, it.isDebit) !in existingKeys }
            if (newOnes.isNotEmpty()) dao.insertAll(newOnes)

            Result.success(
                RestoreResult(found = true, added = newOnes.size, skipped = backupTransactions.size - newOnes.size)
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun dedupKey(code: String, amount: Double, isDebit: Boolean) = "$code|$amount|$isDebit"

    private fun downloadFile(accessToken: String, fileId: String): String {
        val conn = (URL("$DRIVE_FILES_URL/$fileId?alt=media").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $accessToken")
        }
        return try {
            val body = conn.readBody()
            if (conn.responseCode !in 200..299) throw Exception("Couldn't download backup (${conn.responseCode})")
            body
        } finally {
            conn.disconnect()
        }
    }

    private fun parseBackupJson(content: String): List<MpesaTransaction> {
        val array = JSONObject(content).optJSONArray("transactions") ?: JSONArray()
        val result = ArrayList<MpesaTransaction>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val type = try {
                TransactionType.valueOf(obj.optString("type", TransactionType.UNKNOWN.name))
            } catch (e: IllegalArgumentException) {
                TransactionType.UNKNOWN
            }
            result.add(
                MpesaTransaction(
                    mpesaCode = obj.getString("mpesaCode"),
                    amount = obj.getDouble("amount"),
                    type = type,
                    counterparty = obj.optString("counterparty", ""),
                    balanceAfter = obj.optDouble("balanceAfter", 0.0),
                    timestamp = obj.getLong("timestamp"),
                    rawSms = obj.optString("rawSms", ""),
                    isDebit = obj.getBoolean("isDebit"),
                    transactionCost = obj.optDouble("transactionCost", 0.0),
                    subscriptionId = obj.optInt("subscriptionId", -1)
                )
            )
        }
        return result
    }

    /**
     * OAuth access tokens are short-lived, so backup time always asks fresh rather than
     * reusing a stored one. Since the scope was already granted via DriveBackupSection's
     * Enable flow, this normally resolves instantly with no UI — hasResolution() is only
     * expected true if that grant was somehow revoked since, which is treated as a failure
     * here rather than re-opening the consent flow from a background action.
     */
    private suspend fun freshAccessToken(context: Context): String? {
        return try {
            val request = AuthorizationRequest.builder()
                .setRequestedScopes(listOf(Scope(DRIVE_APPDATA_SCOPE)))
                .build()
            val result = Identity.getAuthorizationClient(context).authorize(request).awaitResult()
            if (result.hasResolution()) null else result.accessToken
        } catch (e: Exception) {
            null
        }
    }

    private fun exportToJson(transactions: List<MpesaTransaction>): String {
        val array = JSONArray()
        transactions.forEach { tx ->
            array.put(
                JSONObject().apply {
                    put("mpesaCode", tx.mpesaCode)
                    put("amount", tx.amount)
                    put("type", tx.type.name)
                    put("counterparty", tx.counterparty)
                    put("balanceAfter", tx.balanceAfter)
                    put("timestamp", tx.timestamp)
                    put("rawSms", tx.rawSms)
                    put("isDebit", tx.isDebit)
                    put("transactionCost", tx.transactionCost)
                    put("subscriptionId", tx.subscriptionId)
                }
                // Local Room "id" is deliberately excluded — it's a local autogenerated
                // key with no meaning across devices; restore assigns fresh ones.
            )
        }
        return JSONObject().apply {
            put("schemaVersion", BACKUP_SCHEMA_VERSION)
            put("exportedAt", System.currentTimeMillis())
            put("transactionCount", transactions.size)
            put("transactions", array)
        }.toString()
    }

    private fun HttpURLConnection.readBody(): String {
        val stream = if (responseCode in 200..299) inputStream else errorStream
        return stream?.bufferedReader()?.use { it.readText() } ?: ""
    }

    private fun findExistingBackupId(accessToken: String): String? {
        val query = URLEncoder.encode("name='$BACKUP_FILE_NAME' and trashed=false", "UTF-8")
        val fields = URLEncoder.encode("files(id)", "UTF-8")
        val url = URL("$DRIVE_FILES_URL?spaces=appDataFolder&q=$query&fields=$fields")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $accessToken")
        }
        return try {
            val body = conn.readBody()
            if (conn.responseCode !in 200..299) return null
            val files = JSONObject(body).optJSONArray("files")
            if (files != null && files.length() > 0) files.getJSONObject(0).getString("id") else null
        } finally {
            conn.disconnect()
        }
    }

    /** DELETE is natively supported by HttpURLConnection (unlike PATCH, which isn't — */
    /** that's why this deletes and re-creates below instead of updating in place). */
    private fun deleteFile(accessToken: String, fileId: String) {
        val conn = (URL("$DRIVE_FILES_URL/$fileId").openConnection() as HttpURLConnection).apply {
            requestMethod = "DELETE"
            setRequestProperty("Authorization", "Bearer $accessToken")
        }
        try {
            conn.responseCode // triggers the request; a failure here isn't fatal to the backup
        } finally {
            conn.disconnect()
        }
    }

    private fun uploadNewBackup(accessToken: String, content: String) {
        val boundary = "pesalyzer_backup_${System.currentTimeMillis()}"
        val metadata = JSONObject().apply {
            put("name", BACKUP_FILE_NAME)
            put("parents", JSONArray().put("appDataFolder"))
        }
        val body = buildString {
            append("--").append(boundary).append("\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(metadata.toString())
            append("\r\n--").append(boundary).append("\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(content)
            append("\r\n--").append(boundary).append("--")
        }
        val conn = (URL("$DRIVE_UPLOAD_URL?uploadType=multipart").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
            doOutput = true
        }
        try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode !in 200..299) {
                throw Exception("Backup upload failed (${conn.responseCode}): ${conn.readBody()}")
            }
        } finally {
            conn.disconnect()
        }
    }
}

/**
 * Compact row for the Account dialog: shows backup status, and on tap requests the
 * Drive appdata scope. The appdata folder is private to this app — invisible in the
 * user's own Drive, inaccessible to other apps — so no extra client-side encryption
 * layer is planned on top of it; a user-chosen backup passphrase would risk permanent,
 * unrecoverable data loss if forgotten, which is worse than the problem it solves.
 */
@Composable
fun DriveBackupSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isAuthorized by remember { mutableStateOf(DriveAuthManager.isAuthorized(context)) }
    // Separate flags per operation — sharing one was exactly why "Restore" was
    // showing "Backing up...": both buttons read the same boolean's label.
    var isEnabling by remember { mutableStateOf(false) }
    var isBackingUp by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }
    val anyWorking = isEnabling || isBackingUp || isRestoring
    var errorMessage by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("") }
    var lastBackupTime by remember { mutableStateOf(BackupManager.getLastBackupTime(context)) }

    val authLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        isEnabling = false
        try {
            Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(activityResult.data)
            DriveAuthManager.markAuthorized(context)
            isAuthorized = true
        } catch (e: Exception) {
            errorMessage = e.message ?: "Backup authorization failed"
        }
    }

    fun requestDriveAccess() {
        if (!isOnline(context)) {
            errorMessage = "No internet connection. Check your connection and try again."
            return
        }
        errorMessage = ""
        isEnabling = true
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_APPDATA_SCOPE)))
            .build()
        Identity.getAuthorizationClient(context)
            .authorize(request)
            .addOnSuccessListener { authResult ->
                if (authResult.hasResolution()) {
                    val pendingIntent = authResult.pendingIntent
                    if (pendingIntent != null) {
                        authLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                    } else {
                        isEnabling = false
                        errorMessage = "Couldn't start authorization"
                    }
                } else {
                    isEnabling = false
                    DriveAuthManager.markAuthorized(context)
                    isAuthorized = true
                }
            }
            .addOnFailureListener { e ->
                isEnabling = false
                errorMessage = e.message ?: "Backup authorization failed"
            }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Cloud backup", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    when {
                        !isAuthorized -> "Not enabled yet"
                        lastBackupTime == 0L -> "Enabled — no backup made yet"
                        else -> "Last backed up ${formatTime(lastBackupTime)}"
                    },
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
            if (!isAuthorized) {
                Button(
                    onClick = { requestDriveAccess() },
                    enabled = !anyWorking,
                    colors = ButtonDefaults.buttonColors(containerColor = MpesaGreen)
                ) {
                    Text(if (isEnabling) "..." else "Enable", color = Color.White, fontSize = 13.sp)
                }
            } else {
                Button(
                    onClick = {
                        errorMessage = ""
                        statusMessage = ""
                        isBackingUp = true
                        scope.launch {
                            val result = BackupManager.backupNow(context)
                            isBackingUp = false
                            result.onSuccess { lastBackupTime = BackupManager.getLastBackupTime(context) }
                            result.onFailure { err -> errorMessage = err.message ?: "Backup failed" }
                        }
                    },
                    enabled = !anyWorking,
                    colors = ButtonDefaults.buttonColors(containerColor = MpesaGreen)
                ) {
                    Text(if (isBackingUp) "Backing up..." else "Back up now", color = Color.White, fontSize = 13.sp)
                }
            }
        }

        if (isAuthorized) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(
                    onClick = {
                        errorMessage = ""
                        statusMessage = ""
                        isRestoring = true
                        scope.launch {
                            val result = BackupManager.restoreNow(context)
                            isRestoring = false
                            result.onSuccess { r ->
                                statusMessage = when {
                                    !r.found -> "No backup found for this account"
                                    r.added > 0 -> "Restored ${r.added} new transaction${if (r.added == 1) "" else "s"}" +
                                            if (r.skipped > 0) " (${r.skipped} already had them)" else ""
                                    else -> "Nothing new to restore — already up to date"
                                }
                            }
                            result.onFailure { err -> errorMessage = err.message ?: "Restore failed" }
                        }
                    },
                    enabled = !anyWorking
                ) {
                    Text(if (isRestoring) "Restoring..." else "Restore from backup", color = MpesaGreen, fontSize = 13.sp)
                }
            }
        }

        if (errorMessage.isNotBlank()) {
            Text(errorMessage, color = NegativeRed, fontSize = 12.sp)
        }
        if (statusMessage.isNotBlank()) {
            Text(statusMessage, color = PositiveGreenSoft, fontSize = 12.sp)
        }
    }
}
