package com.example.my_mpesa_tracker.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.my_mpesa_tracker.data.model.TransactionType
import com.example.my_mpesa_tracker.util.label

// ── Brand colours ─────────────────────────────────────────────────────────────
//val OnboardBg        = Color(0xFFF5F9F6)
val OnboardGreen     = Color(0xFF00A550)
//val OnboardGreenDark = Color(0xFF007A3C)
val OnboardText      = Color(0xFF1A2E24)
val OnboardSubtext   = Color(0xFF5A7A68)
val OnboardCard      = Color(0xFFFFFFFF)

// ── Onboarding pages ──────────────────────────────────────────────────────────

data class OnboardPage(
    val emoji: String,
    val title: String,
    val subtitle: String,
    val description: String
)

val onboardPages = listOf(
    OnboardPage(
        emoji = "💚",
        title = "Pesalyzer",
        subtitle = "Know your money.",
        description = "Every M-Pesa transaction you make is automatically captured, categorised, and analysed; live, on your device. No internet needed. No data leaves your phone."
    ),
    OnboardPage(
        emoji = "📱",
        title = "How it works",
        subtitle = "Your SMS. Your data.",
        description = "Pesalyzer reads your M-Pesa confirmation SMS messages to extract transaction details. We need SMS permission for this. Your messages are never uploaded; everything stays on your phone."
    ),
    OnboardPage(
        emoji = "📊",
        title = "Your dashboard",
        subtitle = "Insight at a glance.",
        description = "See spending trends, cash flow, category breakdowns, and smart insights for today, this week, this month, or any custom range. Pull to refresh anytime."
    )
)

// ── Main Onboarding Screen ────────────────────────────────────────────────────

@Composable
fun OnboardingScreen(onComplete: (monthlyBudget: Double?) -> Unit) {
    var currentPage by remember { mutableIntStateOf(0) }
    var showBudgetSetup by remember { mutableStateOf(false) }

    if (showBudgetSetup) {
        BudgetSetupScreen(
            onSkip = { onComplete(null) },
            onConfirm = { budget -> onComplete(budget) }
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFE8F5EE),
                        Color(0xFFF5F9F6),
                        Color(0xFFFFFFFF)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Spacer(Modifier.height(60.dp))

            // Animated page content
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(tween(400)) + slideInHorizontally(tween(400)),
                exit = fadeOut(tween(300)) + slideOutHorizontally(tween(300))
            ) {
                OnboardPageContent(page = onboardPages[currentPage])
            }

            Spacer(Modifier.weight(1f))

            // Page indicators
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                onboardPages.forEachIndexed { index, _ ->
                    val width by animateDpAsState(
                        targetValue = if (index == currentPage) 24.dp else 8.dp,
                        animationSpec = tween(300),
                        label = "indicator"
                    )
                    Box(
                        modifier = Modifier
                            .height(8.dp)
                            .width(width)
                            .clip(CircleShape)
                            .background(
                                if (index == currentPage) OnboardGreen
                                else OnboardGreen.copy(alpha = 0.25f)
                            )
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            // Primary button
            Button(
                onClick = {
                    if (currentPage < onboardPages.size - 1) {
                        currentPage++
                    } else {
                        showBudgetSetup = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = OnboardGreen
                )
            ) {
                Text(
                    if (currentPage < onboardPages.size - 1) "Next" else "Set up budget",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }

            Spacer(Modifier.height(12.dp))

            // Skip to app
            if (currentPage == onboardPages.size - 1) {
                TextButton(onClick = { onComplete(null) }) {
                    Text(
                        "Skip for now",
                        color = OnboardSubtext,
                        fontSize = 14.sp
                    )
                }
            } else {
                TextButton(onClick = { onComplete(null) }) {
                    Text(
                        "Skip onboarding",
                        color = OnboardSubtext.copy(alpha = 0.6f),
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun OnboardPageContent(page: OnboardPage) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Large emoji icon
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            OnboardGreen.copy(alpha = 0.15f),
                            Color.Transparent
                        )
                    ),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(page.emoji, fontSize = 56.sp)
        }

        Spacer(Modifier.height(8.dp))

        // App name / title
        Text(
            page.title,
            color = OnboardText,
            fontSize = if (page.title == "Pesalyzer") 36.sp else 26.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Serif,
            textAlign = TextAlign.Center
        )

        // Subtitle
        Text(
            page.subtitle,
            color = OnboardGreen,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            fontStyle = FontStyle.Italic,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        // Description card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = OnboardCard),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Text(
                page.description,
                color = OnboardSubtext,
                fontSize = 15.sp,
                lineHeight = 24.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(20.dp)
            )
        }
    }
}

// ── Budget Setup Screen ───────────────────────────────────────────────────────

@Composable
fun BudgetSetupScreen(onSkip: () -> Unit, onConfirm: (Double) -> Unit) {
    var monthlyBudget by remember { mutableStateOf("") }
    var categoryBudgets by remember { mutableStateOf(
        mapOf(
            TransactionType.SEND_MONEY to "",
            TransactionType.BUY_GOODS to "",
            TransactionType.PAY_BILL to "",
            TransactionType.AIRTIME to "",
            TransactionType.POCHI_LA_BIASHARA to ""
        )
    ) }
    var error by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFFE8F5EE), Color(0xFFFFFFFF))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(48.dp))

            Text("💰", fontSize = 48.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                "Set your budget",
                color = OnboardText,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif
            )
            Text(
                "Optional — you can update this anytime",
                color = OnboardSubtext,
                fontSize = 13.sp,
                fontStyle = FontStyle.Italic
            )

            Spacer(Modifier.height(24.dp))

            // Monthly total budget
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = OnboardCard),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Monthly total budget",
                        color = OnboardText,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = monthlyBudget,
                        onValueChange = { monthlyBudget = it },
                        placeholder = { Text("e.g. 15000", color = OnboardSubtext.copy(0.5f)) },
                        prefix = { Text("KES ", color = OnboardSubtext) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = OnboardGreen,
                            unfocusedBorderColor = OnboardSubtext.copy(0.3f),
                            focusedTextColor = OnboardText,
                            unfocusedTextColor = OnboardText,
                            cursorColor = OnboardGreen
                        )
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Per category budgets
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = OnboardCard),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Per category (optional)",
                        color = OnboardText,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    categoryBudgets.entries.forEach { (type, value) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                type.label(),
                                color = OnboardSubtext,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = value,
                                onValueChange = { new ->
                                    categoryBudgets = categoryBudgets.toMutableMap().also { it[type] = new }
                                },
                                placeholder = { Text("KES", color = OnboardSubtext.copy(0.4f), fontSize = 12.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.width(100.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = OnboardGreen,
                                    unfocusedBorderColor = OnboardSubtext.copy(0.3f),
                                    focusedTextColor = OnboardText,
                                    unfocusedTextColor = OnboardText,
                                    cursorColor = OnboardGreen
                                ),
                                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                            )
                        }
                    }
                }
            }

            if (error.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(error, color = Color(0xFFCC0000), fontSize = 13.sp)
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = {
                    val budget = monthlyBudget.toDoubleOrNull()
                    if (monthlyBudget.isNotBlank() && budget == null) {
                        error = "Enter a valid number"
                    } else {
                        onConfirm(budget ?: 0.0)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = OnboardGreen)
            ) {
                Text("Start using Pesalyzer", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }

            Spacer(Modifier.height(12.dp))

            TextButton(onClick = onSkip) {
                Text("Skip budget setup", color = OnboardSubtext, fontSize = 14.sp)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
