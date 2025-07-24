package com.tpl.fast_mover.uix


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpandedScreen(
    popBack: () -> Unit,
    lastMovedFileFlow: StateFlow<String?>,
    onStopService: () -> Unit
) {
    val lastMovedFile by lastMovedFileFlow.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(10.dp)
            .background(Color.LightGray),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Balon Aktif")
        Spacer(modifier = Modifier.height(12.dp))

        if (lastMovedFile != null) {
            Text("Son Taşınan Dosya: $lastMovedFile")
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = onStopService) {
            Text("Servisi Durdur")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(onClick = popBack) {
            Text("Geri Dön")
        }
    }
}
