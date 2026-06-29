package com.example.my_mpesa_tracker.ui.dashboard

import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.fragment.app.FragmentActivity
import com.example.my_mpesa_tracker.data.model.MpesaTransaction
import com.example.my_mpesa_tracker.data.model.TransactionType
import com.example.my_mpesa_tracker.util.ManualEntryType
import com.example.my_mpesa_tracker.util.ManualNetWorthManager
import com.example.my_mpesa_tracker.util.NetWorthEngine
import com.example.my_mpesa_tracker.util.label
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ── Recurring Transaction Detection ───────────────────────────────────────────

data class RecurringPattern(
    val counterparty: String,
    val averageAmount: Double,
    val occurrences: Int,
    val type: TransactionType,
    val dayOfWeek: String?,     // e.g. "Monday" if weekly
    val dayOfMonth: Int?,       // e.g. 1 if monthly
    val patternLabel: String    // "Weekly", "Monthly", "Frequent"
)

object RecurringDetector {

    fun detect(transactions: List<MpesaTransaction>): List<RecurringPattern> {
        val debits = transactions.filter { it.isDebit }
        val patterns = mutableListOf<RecurringPattern>()

        // Group by counterparty
        val grouped = debits.groupBy { it.counterparty }

        grouped.forEach { (counterparty, txs) ->
            if (txs.size < 2) return@forEach

            val avgAmount = txs.sumOf { it.amount } / txs.size
            val type = txs.first().type

            // Check weekly pattern — same day of week
            val dayGroups = txs.groupBy {
                val cal = Calendar.getInstance()
                cal.timeInMillis = it.timestamp
                cal.get(Calendar.DAY_OF_WEEK)
            }
            val dominantDay = dayGroups.maxByOrNull { it.value.size }
            if (dominantDay != null && dominantDay.value.size >= txs.size * 0.6 && txs.size >= 3) {
                val dayName = listOf("Sun","Mon","Tue","Wed","Thu","Fri","Sat")[dominantDay.key - 1]
                patterns.add(
                    RecurringPattern(
                        counterparty = counterparty,
                        averageAmount = avgAmount,
                        occurrences = txs.size,
                        type = type,
                        dayOfWeek = dayName,
                        dayOfMonth = null,
                        patternLabel = "Weekly ($dayName)"
                    )
                )
                return@forEach
            }

            // Check monthly pattern — same day of month
            val dateGroups = txs.groupBy {
                val cal = Calendar.getInstance()
                cal.timeInMillis = it.timestamp
                cal.get(Calendar.DAY_OF_MONTH)
            }
            val dominantDate = dateGroups.maxByOrNull { it.value.size }
            if (dominantDate != null && dominantDate.value.size >= txs.size * 0.5 && txs.size >= 2) {
                patterns.add(
                    RecurringPattern(
                        counterparty = counterparty,
                        averageAmount = avgAmount,
                        occurrences = txs.size,
                        type = type,
                        dayOfWeek = null,
                        dayOfMonth = dominantDate.key,
                        patternLabel = "Monthly (${dominantDate.key}th)"
                    )
                )
                return@forEach
            }

            // Frequent — appears 3+ times without clear pattern
            if (txs.size >= 3) {
                patterns.add(
                    RecurringPattern(
                        counterparty = counterparty,
                        averageAmount = avgAmount,
                        occurrences = txs.size,
                        type = type,
                        dayOfWeek = null,
                        dayOfMonth = null,
                        patternLabel = "Frequent"
                    )
                )
            }
        }

        return patterns.sortedByDescending { it.occurrences }
    }
}

@Composable
fun PinSetupDialog(onDismiss: () -> Unit, onPinSet: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var step by remember { mutableIntStateOf(1) }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardDark,
        title = {
            Text(
                if (step == 1) "Set PIN" else "Confirm PIN",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    if (step == 1) "Enter a 4-digit PIN" else "Enter PIN again to confirm",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
                // PIN dots
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val current = if (step == 1) pin else confirm
                    repeat(4) { index ->
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .background(
                                    if (index < current.length) MpesaGreen else CardDark.copy(alpha = 0f),
                                    androidx.compose.foundation.shape.CircleShape
                                )
                                .then(
                                    if (index >= current.length) Modifier.background(
                                        Color.White.copy(alpha = 0.2f),
                                        androidx.compose.foundation.shape.CircleShape
                                    ) else Modifier
                                )
                        )
                    }
                }
                if (error.isNotBlank()) {
                    Text(error, color = Color(0xFFFF4444), fontSize = 12.sp)
                }
                PinPad(
                    onDigit = { digit ->
                        error = ""
                        if (step == 1) {
                            if (pin.length < 4) {
                                pin += digit
                                if (pin.length == 4) step = 2
                            }
                        } else {
                            if (confirm.length < 4) {
                                confirm += digit
                                if (confirm.length == 4) {
                                    if (confirm == pin) {
                                        onPinSet(pin)
                                    } else {
                                        error = "PINs do not match"
                                        confirm = ""
                                        pin = ""
                                        step = 1
                                    }
                                }
                            }
                        }
                    },
                    onDelete = {
                        error = ""
                        if (step == 2) confirm = confirm.dropLast(1)
                        else pin = pin.dropLast(1)
                    }
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}
@Composable
fun RecurringTransactionsCard(transactions: List<MpesaTransaction>) {
    val patterns = remember(transactions) { RecurringDetector.detect(transactions) }
    if (patterns.isEmpty()) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Recurring Transactions", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)

            patterns.take(5).forEach { pattern ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(pattern.type.emoji(), fontSize = 16.sp)
                        Column {
                            Text(
                                pattern.counterparty,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                            Text(
                                "${pattern.patternLabel} · ${pattern.occurrences}x",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                    Text(
                        "~${formatKsh(pattern.averageAmount)}",
                        color = Color(0xFFFF6B6B),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

// ── Transaction Notes ──────────────────────────────────────────────────────────

object NoteStorage {
    private const val PREFS = "pesalyzer_notes"

    fun getNote(context: Context, mpesaCode: String): String {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(mpesaCode, "") ?: ""
    }

    fun saveNote(context: Context, mpesaCode: String, note: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit { putString(mpesaCode, note) }
    }

    fun deleteNote(context: Context, mpesaCode: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit { remove(mpesaCode) }
    }
}

@Composable
fun TransactionNoteDialog(
    tx: MpesaTransaction,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var note by remember { mutableStateOf(NoteStorage.getNote(context, tx.mpesaCode)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardDark,
        title = {
            Text("Add Note", color = Color.White, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${tx.counterparty} · ${formatKsh(tx.amount)}",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    placeholder = { Text("e.g. Rent payment, lunch with team...", color = TextSecondary.copy(0.5f)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MpesaGreen,
                        unfocusedBorderColor = Color.White.copy(0.2f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = MpesaGreen
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (note.isBlank()) {
                    NoteStorage.deleteNote(context, tx.mpesaCode)
                } else {
                    NoteStorage.saveNote(context, tx.mpesaCode, note)
                }
                onDismiss()
            }) { Text("Save", color = MpesaGreen) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        }
    )
}

// ── CSV Export ────────────────────────────────────────────────────────────────

object CsvExporter {

    fun export(context: Context, transactions: List<MpesaTransaction>, periodLabel: String): File? {
        return try {
            val sdf = SimpleDateFormat("d MMM yyyy h:mm a", Locale.US)
            val fileName = "pesalyzer_${periodLabel.replace(" ", "_").replace("–", "-")}.csv"

            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                ?: context.filesDir
            val file = File(dir, fileName)

            FileWriter(file).use { writer ->
                // Header
                writer.appendLine("M-Pesa Code,Date,Type,Counterparty,Amount,Transaction Cost,Direction,Balance After,Note")

                // Rows
                transactions.sortedBy { it.timestamp }.forEach { tx ->
                    val note = NoteStorage.getNote(context, tx.mpesaCode)
                    writer.appendLine(
                        listOf(
                            tx.mpesaCode,
                            sdf.format(Date(tx.timestamp)),
                            tx.type.label(),
                            tx.counterparty.replace(",", " "),
                            tx.amount.toString(),
                            tx.transactionCost.toString(),
                            if (tx.isDebit) "Debit" else "Credit",
                            tx.balanceAfter.toString(),
                            note.replace(",", " ")
                        ).joinToString(",")
                    )
                }
            }
            file
        } catch (_: Exception) {
            null
        }
    }

    fun share(context: Context, file: File) {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Pesalyzer Export")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share CSV via"))
    }

    fun exportNetWorthSummary(context: Context, transactions: List<MpesaTransaction>): File? {
        return try {
            val breakdown = NetWorthEngine.compute(transactions)
            val manualEntries = ManualNetWorthManager.getAllEntries(context)
            val sdf = SimpleDateFormat("d MMM yyyy", Locale.US)
            val fileName = "pesalyzer_networth_${sdf.format(Date())}.csv"
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
            val file = File(dir, fileName)

            FileWriter(file).use { writer ->
                writer.appendLine("Category,Item,Amount,Confidence")
                writer.appendLine("Cash,M-Pesa Balance,${breakdown.mpesaCash},Live")
                writer.appendLine("Savings,Ziidi Wallet,${breakdown.ziidiEstimated},Estimated")
                breakdown.otherInvestmentsContributed.forEach { (label, amount) ->
                    writer.appendLine("Investment,$label,$amount,Contributed via M-Pesa - not current value")
                }
                manualEntries.forEach { entry ->
                    val category = if (entry.type == ManualEntryType.ASSET) "Manual Asset" else "Manual Liability"
                    writer.appendLine("$category,${entry.name},${entry.amount},Manually entered")
                }
            }
            file
        } catch (_: Exception) {
            null
        }
    }
}

// ── App Lock ──────────────────────────────────────────────────────────────────

object AppLockManager {
    private const val PREFS = "pesalyzer_lock"
    private const val PIN_KEY = "app_pin"
    private const val LOCK_ENABLED = "lock_enabled"
    private const val BIOMETRIC_ENABLED = "biometric_enabled"

//    fun isPinSet(context: Context): Boolean {
//        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
//            .getString(PIN_KEY, null) != null
//    }

    fun isLockEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(LOCK_ENABLED, false)
    }

    fun isBiometricEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(BIOMETRIC_ENABLED, false)
    }

    fun setPin(context: Context, pin: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit {
                putString(PIN_KEY, pin)
                    .putBoolean(LOCK_ENABLED, true)
            }
    }

    fun verifyPin(context: Context, pin: String): Boolean {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(PIN_KEY, null)
        return stored == pin
    }

    fun enableBiometric(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit { putBoolean(BIOMETRIC_ENABLED, enabled) }
    }

    fun disableLock(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit {
                remove(PIN_KEY)
                    .putBoolean(LOCK_ENABLED, false)
                    .putBoolean(BIOMETRIC_ENABLED, false)
            }
    }

    fun isBiometricAvailable(context: Context): Boolean {
        return BiometricManager.from(context)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
                BiometricManager.BIOMETRIC_SUCCESS
    }
}

@Composable
fun AppLockScreen(onUnlocked: () -> Unit) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var attemptBiometric by remember { mutableStateOf(true) }

    // Try biometric on launch
    LaunchedEffect(attemptBiometric) {
        if (attemptBiometric && AppLockManager.isBiometricEnabled(context) &&
            AppLockManager.isBiometricAvailable(context)) {
            val executor = ContextCompat.getMainExecutor(context)
            val prompt = BiometricPrompt(
                context as FragmentActivity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        onUnlocked()
                    }
                    override fun onAuthenticationFailed() {
                        attemptBiometric = false
                    }
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        attemptBiometric = false
                    }
                }
            )
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Pesalyzer")
                .setSubtitle("Verify your identity")
                .setNegativeButtonText("Use PIN")
                .build()
            prompt.authenticate(info)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceDark),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = MpesaGreen,
                modifier = Modifier.size(48.dp)
            )

            Text(
                "Pesalyzer",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                "Enter your PIN",
                color = TextSecondary,
                fontSize = 14.sp
            )

            // PIN dots
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(4) { index ->
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(
                                if (index < pin.length) MpesaGreen else CardDark,
                                androidx.compose.foundation.shape.CircleShape
                            )
                    )
                }
            }

            if (error.isNotBlank()) {
                Text(error, color = Color(0xFFFF4444), fontSize = 13.sp)
            }

            // PIN pad
            PinPad(
                onDigit = { digit ->
                    if (pin.length < 4) {
                        pin += digit
                        if (pin.length == 4) {
                            if (AppLockManager.verifyPin(context, pin)) {
                                onUnlocked()
                            } else {
                                error = "Incorrect PIN"
                                pin = ""
                            }
                        }
                    }
                },
                onDelete = {
                    if (pin.isNotEmpty()) pin = pin.dropLast(1)
                    error = ""
                }
            )

            if (AppLockManager.isBiometricEnabled(context) &&
                AppLockManager.isBiometricAvailable(context)) {
                TextButton(onClick = { attemptBiometric = true }) {
                    Text("Use fingerprint", color = MpesaGreen, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun PinPad(onDigit: (String) -> Unit, onDelete: () -> Unit) {
    val keys = listOf("1","2","3","4","5","6","7","8","9","","0","⌫")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        keys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                row.forEach { key ->
                    if (key.isEmpty()) {
                        Spacer(Modifier.size(64.dp))
                    } else {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(CardDark, androidx.compose.foundation.shape.CircleShape)
                                .clickable {
                                    if (key == "⌫") onDelete() else onDigit(key)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                key,
                                color = Color.White,
                                fontSize = if (key == "⌫") 18.sp else 20.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}
