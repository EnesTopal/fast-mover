package com.tpl.fast_mover.uix

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking


@Composable
fun BubbleCompose(
    show: () -> Unit = {},
    hide: () -> Unit = {},
    animateToEdge: () -> Unit = {},
    expand: () -> Unit = {}
) {

//    val context = LocalContext.current
//    val scope = rememberCoroutineScope()


    Row(
        modifier = Modifier
            .height(50.dp)
            .clip(shape = RoundedCornerShape(100.dp))
            .background(Color.LightGray),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = {
            runBlocking {
                expand()
            }
        }) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "play arrow"
            )
        }

    }
}

