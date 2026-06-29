package com.example.my_mpesa_tracker.ui.dashboard

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── No transactions yet ───────────────────────────────────────────────────────

@Composable
fun EmptyTransactionsState() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Pulsing icon
        Box(
            modifier = Modifier
                .scale(scale)
                .size(100.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MpesaGreen.copy(alpha = 0.2f),
                            Color.Transparent
                        )
                    ),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text("📱", fontSize = 48.sp)
        }

        Text(
            "No transactions yet",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Serif,
            textAlign = TextAlign.Center
        )

        Text(
            "Send or receive M-Pesa and it will appear here instantly.",
            color = TextSecondary,
            fontSize = 14.sp,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        // Action hint card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MpesaGreen.copy(alpha = 0.1f)
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("💡", fontSize = 20.sp)
                Text(
                    "Make sure to grant SMS permission so Pesalyzer can read your M-Pesa messages.",
                    color = MpesaGreen,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// ── No results for search/filter ──────────────────────────────────────────────

@Composable
fun EmptySearchState(query: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("🔍", fontSize = 40.sp)
        Text(
            "No results for \"$query\"",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Text(
            "Try a different name, amount, or M-Pesa code.",
            color = TextSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
    }
}

// ── No transactions in selected period ───────────────────────────────────────

@Composable
fun EmptyPeriodState(period: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("📅", fontSize = 40.sp)
        Text(
            "Nothing recorded for $period",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Text(
            "Try a different period or pull down to refresh.",
            color = TextSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            fontStyle = FontStyle.Italic
        )
    }
}

// ── History import in progress ────────────────────────────────────────────────

@Composable
fun ImportingHistoryState() {
    val infiniteTransition = rememberInfiniteTransition(label = "loading")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                color = MpesaGreen,
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp
            )
            Column {
                Text(
                    "Importing your M-Pesa history...",
                    color = Color.White.copy(alpha = alpha),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "This only happens once. Please wait.",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }
    }
}
