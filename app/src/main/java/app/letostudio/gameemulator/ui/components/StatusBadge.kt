package app.letostudio.gameemulator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.letostudio.gameemulator.ui.theme.AppTheme
import app.letostudio.gameemulator.ui.theme.ThemeMode

enum class StatusType { Success, Error }

/**
 * Status badge (design_system.md §7): 8dp colored dot + labelMedium text, 6dp gap.
 * Success uses extendedColors.success, Error uses colorScheme.error.
 */
@Composable
fun StatusBadge(text: String, type: StatusType) {
    val color = when (type) {
        StatusType.Success -> AppTheme.extendedColors.success
        StatusType.Error -> MaterialTheme.colorScheme.error
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color = color, shape = CircleShape),
        )
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun StatusBadgeLightPreview() {
    AppTheme(ThemeMode.Light) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusBadge(text = "Connected", type = StatusType.Success)
            StatusBadge(text = "Connection failed", type = StatusType.Error)
        }
    }
}

@Preview(name = "Dark", showBackground = true, backgroundColor = 0xFF0E0F13)
@Composable
private fun StatusBadgeDarkPreview() {
    AppTheme(ThemeMode.Dark) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusBadge(text = "Connected", type = StatusType.Success)
            StatusBadge(text = "Connection failed", type = StatusType.Error)
        }
    }
}
