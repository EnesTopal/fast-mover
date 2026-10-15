package com.tpl.fast_mover.overlay

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tpl.fast_mover.R
import com.tpl.fast_mover.core.TransferState
import com.tpl.fast_mover.ui.theme.FastMoverTheme

internal val StatusGreen = Color(0xFF2E7D32)
internal val StatusAmber = Color(0xFFF9A825)
internal val StatusBlue = Color(0xFF1565C0)
internal val StatusRed = Color(0xFFC62828)
internal val StatusGrey = Color(0xFF9E9E9E)

internal fun TransferState.Status.color(): Color = when (this) {
    TransferState.Status.Watching -> StatusGreen
    TransferState.Status.Paused -> StatusAmber
    is TransferState.Status.Moving -> StatusBlue
    is TransferState.Status.Error -> StatusRed
    TransferState.Status.Idle -> StatusGrey
}

internal fun TransferState.Status.label(): String = when (this) {
    TransferState.Status.Watching -> "Watching"
    TransferState.Status.Paused -> "Paused"
    is TransferState.Status.Moving -> "Moving $name… $percent%"
    is TransferState.Status.Error -> "Error: $message"
    TransferState.Status.Idle -> "Idle"
}

/** The floating bubble (56dp circle in a 64dp box): status ring, icon, moved-count badge, progress while moving. */
@Composable
internal fun BubbleContent(dragging: Boolean, overClose: Boolean) {
    val status by TransferState.status.collectAsState()
    val count by TransferState.movedCount.collectAsState()
    val scale by animateFloatAsState(
        targetValue = when { overClose -> 0.8f; dragging -> 1.08f; else -> 1f },
        label = "bubbleScale"
    )
    val statusColor = status.color()

    FastMoverTheme {
        Box(
            modifier = Modifier
                .size(64.dp)
                .padding(4.dp)
                .scale(scale)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .shadow(6.dp, CircleShape)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(3.dp, statusColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_bubble),
                    contentDescription = "Fast Mover",
                    tint = statusColor,
                    modifier = Modifier.size(26.dp)
                )
                (status as? TransferState.Status.Moving)?.let { moving ->
                    CircularProgressIndicator(
                        progress = { moving.percent / 100f },
                        modifier = Modifier.fillMaxSize(),
                        color = StatusBlue,
                        strokeWidth = 3.dp,
                        trackColor = Color.Transparent
                    )
                }
            }
            if (count > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(StatusGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (count > 99) "99+" else count.toString(),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** Circle shown at the bottom of the screen while dragging; dropping the bubble on it stops the service. */
@Composable
internal fun CloseTarget(active: Boolean) {
    val scale by animateFloatAsState(targetValue = if (active) 1.2f else 1f, label = "closeScale")
    Box(
        modifier = Modifier
            .size(72.dp)
            .scale(scale)
            .padding(4.dp)
            .shadow(8.dp, CircleShape)
            .clip(CircleShape)
            .background(if (active) StatusRed else Color(0xCC212121)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_close),
            contentDescription = "Stop Fast Mover",
            tint = Color.White,
            modifier = Modifier.size(32.dp)
        )
    }
}
