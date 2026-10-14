package com.tpl.fast_mover.uix

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.StateFlow

@Composable
fun ExpandedScreen(
    popBack: () -> Unit,
    lastMovedFileFlow: StateFlow<String?>,
    onStopService: () -> Unit
) {
    val lastMovedFile by lastMovedFileFlow.collectAsState()

    Card(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(6.dp)
    ) {
        Column(
            modifier = Modifier
                .background(Color(0xFFF5F5F5))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("📦 Fast Mover", fontSize = 20.sp, color = Color(0xFF333333))

            Spacer(Modifier.height(12.dp))

            if (lastMovedFile != null) {
                Text("Last Moved File:", fontSize = 14.sp, color = Color.DarkGray)
                Text(
                    text = lastMovedFile ?: "",
                    fontSize = 16.sp,
                    color = Color.Black,
                    modifier = Modifier
                        .padding(8.dp)
                        .background(Color.White, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            Spacer(Modifier.height(24.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(onClick = popBack, modifier = Modifier.weight(1f)) {
                    Text("↩️ Back")
                }
                Button(
                    onClick = onStopService,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("⛔ Stop Service", color = Color.White)
                }
            }
        }
    }
}
