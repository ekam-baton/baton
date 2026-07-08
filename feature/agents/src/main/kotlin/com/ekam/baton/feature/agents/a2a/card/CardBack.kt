package com.ekam.baton.feature.agents.a2a.card

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ekam.baton.core.data.model.AgentRole

@Composable
fun CardBack(
    agentId: String,
    role: AgentRole,
    modifier: Modifier = Modifier
) {
    val serial = CardCrypto.generateSerialNumber(agentId)

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF111116))
            .border(1.dp, Color(0xFF333344), RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            // Magnetic Stripe
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(Color(0xFF050508))
            )
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Disclaimer text
                Text(
                    text = "PROPERTY OF BATON AUTONOMOUS SYSTEM.\n" +
                           "This identity card is a sovereign credential for A2A operations. " +
                           "Unauthorized duplication or modification is strictly prohibited. " +
                           "Agent activities are monitored and logged.",
                    color = Color.Gray,
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 12.sp,
                    textAlign = TextAlign.Justify
                )
                
                Spacer(modifier = Modifier.weight(1f))
                
                // Barcode simulation
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    BarcodeLines(agentId)
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = serial,
                    color = Color.DarkGray,
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun BarcodeLines(agentId: String) {
    val fingerprint = CardCrypto.generateVisualFingerprint(agentId)
    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val totalBars = fingerprint.size // 64
        val barWidth = width / totalBars
        
        for (i in 0 until totalBars) {
            if (fingerprint[i] || i % 7 == 0) { // Add some guaranteed lines for aesthetics
                drawRect(
                    color = Color.Black,
                    topLeft = androidx.compose.ui.geometry.Offset(i * barWidth, 0f),
                    size = androidx.compose.ui.geometry.Size(barWidth * (if (i%3==0) 2f else 0.8f), height)
                )
            }
        }
    }
}
