package com.tpl.fast_mover.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tpl.fast_mover.R
import com.tpl.fast_mover.core.TransferRecord
import com.tpl.fast_mover.core.TransferState
import com.tpl.fast_mover.transfer.FileNames
import com.tpl.fast_mover.ui.theme.FastMoverTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The card shown when the bubble is tapped. */
@Composable
internal fun ExpandedPanel(
    onCollapse: () -> Unit,
    onTogglePause: () -> Unit,
    onStop: () -> Unit
) {
    val status by TransferState.status.collectAsState()
    val count by TransferState.movedCount.collectAsState()
    val lastMoved by TransferState.lastMoved.collectAsState()
    val history by TransferState.history.collectAsState()
    val sourceName by TransferState.sourceName.collectAsState()
    val destName by TransferState.destName.collectAsState()

    val paused = status == TransferState.Status.Paused
    val canToggle = status !is TransferState.Status.Error && status != TransferState.Status.Idle

    FastMoverTheme {
        Surface(
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 3.dp,
            shadowElevation = 10.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 16.dp)) {

                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Fast Mover",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onCollapse) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = "Collapse")
                    }
                }

                // A → B (folder names only)
                Text(
                    text = "${sourceName.ifEmpty { "?" }}  →  ${destName.ifEmpty { "?" }}",
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(10.dp))

                // Status line with colored dot
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(status.color())
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = status.label(),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Moved this session: $count",
                    style = MaterialTheme.typography.bodyMedium
                )
                lastMoved?.let {
                    Text(
                        text = "Last: ${it.name} (${FileNames.humanSize(it.bytes)})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (history.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Recent",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    history.take(RECENT_ROWS).forEach { HistoryRow(it) }
                }

                Spacer(Modifier.height(14.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().padding(end = 8.dp)
                ) {
                    Button(
                        onClick = onTogglePause,
                        enabled = canToggle,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            painterResource(if (paused) R.drawable.ic_play else R.drawable.ic_pause),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (paused) "Resume" else "Pause")
                    }
                    OutlinedButton(
                        onClick = onStop,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_stop),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Stop")
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(record: TransferRecord) {
    val time = remember(record.timeMs) { TIME_FORMAT.format(Date(record.timeMs)) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (record.ok) StatusGreen else StatusRed)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = record.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (record.ok) time else "failed",
            style = MaterialTheme.typography.bodySmall,
            color = if (record.ok) MaterialTheme.colorScheme.onSurfaceVariant else StatusRed
        )
    }
}

private const val RECENT_ROWS = 5
private val TIME_FORMAT = SimpleDateFormat("HH:mm", Locale.getDefault())
