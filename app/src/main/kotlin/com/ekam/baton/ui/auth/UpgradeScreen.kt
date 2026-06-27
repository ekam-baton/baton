package com.ekam.baton.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.ekam.baton.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpgradeScreen(
    mainViewModel: MainViewModel = koinViewModel(),
    onUpgradeSuccess: () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var showBillingDialog by remember { mutableStateOf(false) }
    var isProcessingPayment by remember { mutableStateOf(false) }
    var paymentSuccess by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F1623), // BatonSurface
                        Color(0xFF070B14)  // BatonBackground
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Icon
            Surface(
                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f),
                shape = CircleShape,
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.tertiary),
                modifier = Modifier.size(80.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Premium Icon",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "BATON Premium",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Your 6-month free trial has ended. Subscribe to unlock unlimited access and continue orchestrating secure AI agents.",
                fontSize = 15.sp,
                color = Color(0xFF7A8B9E),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Pricing Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1C2237)), // BatonSlate
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "ANNUAL PLAN",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = "₹250",
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = " / year",
                            fontSize = 16.sp,
                            color = Color(0xFF7A8B9E),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Includes a 6-month free trial for new users. Cancel anytime in Google Play.",
                        fontSize = 12.sp,
                        color = Color(0xFF7A8B9E),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Premium features
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                PremiumFeatureItem("Unlimited Agents (up to 100+ profiles)")
                PremiumFeatureItem("Sovereign encrypted database & local memory")
                PremiumFeatureItem("API Key & OAuth 2.1 secure client gateways")
                PremiumFeatureItem("No data tracking or external storage")
            }

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showBillingDialog = true
                },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Text(
                    text = "Subscribe Now",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    // Google Play Billing Simulation
    if (showBillingDialog) {
        Dialog(
            onDismissRequest = {
                if (!isProcessingPayment) showBillingDialog = false
            }
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2436)),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Google Play Banner
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Google Play",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF34A853)
                        )
                        Text(
                            text = "Secure Checkout",
                            fontSize = 12.sp,
                            color = Color(0xFF7A8B9E)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = Color(0xFF2C354E))
                    Spacer(modifier = Modifier.height(16.dp))

                    if (!isProcessingPayment && !paymentSuccess) {
                        Text(
                            text = "BATON Premium Subscription",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Billed annually. 6 months free, then ₹250.00/year.",
                            fontSize = 13.sp,
                            color = Color(0xFF7A8B9E),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showBillingDialog = false },
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, Color(0xFFFF453A)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color(0xFFFF453A)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Text("Cancel", fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        isProcessingPayment = true
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        delay(2000) // Simulate network/auth delay
                                        mainViewModel.unlockPremium()
                                        isProcessingPayment = false
                                        paymentSuccess = true
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        delay(1500) // Show success checkmark
                                        showBillingDialog = false
                                        paymentSuccess = false
                                        onUpgradeSuccess()
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF34A853) // Google Green
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Text("1-Tap Buy", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }

                    } else if (isProcessingPayment) {
                        CircularProgressIndicator(color = Color(0xFF34A853))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Processing transaction securely...",
                            fontSize = 14.sp,
                            color = Color(0xFF7A8B9E)
                        )
                    } else if (paymentSuccess) {
                        Surface(
                            color = Color(0xFF34A853).copy(alpha = 0.1f),
                            shape = CircleShape,
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Success",
                                    tint = Color(0xFF34A853),
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Subscription Activated!",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Thank you for supporting BATON.",
                            fontSize = 13.sp,
                            color = Color(0xFF7A8B9E)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PremiumFeatureItem(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            fontSize = 14.sp,
            color = Color(0xFFCFD8DC)
        )
    }
}
