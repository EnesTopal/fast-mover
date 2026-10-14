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

@Composable
fun BubbleCompose(
    expand: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .height(50.dp)
            .clip(RoundedCornerShape(100.dp))
            .background(Color.LightGray),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Note: with forceDragging(true) this click never fires — expand() is handled
        // via FloatingBubbleListener.onFingerUp in BubbleService instead.
        IconButton(onClick = expand) {
            Icon(Icons.Default.PlayArrow, contentDescription = "expand")
        }
    }
}
