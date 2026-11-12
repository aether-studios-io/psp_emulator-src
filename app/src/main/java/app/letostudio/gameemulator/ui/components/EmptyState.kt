package app.letostudio.gameemulator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.letostudio.gameemulator.ui.theme.AppTheme
import app.letostudio.gameemulator.ui.theme.ThemeMode

/** Fixed width for the empty-state CTA stack so every tab renders an identically shaped button. */
private val EmptyStateActionWidth = 220.dp

/**
 * Empty-state placeholder — one of the 4 allowed brand-gradient usages
 * (design_system.md section 2.3: icon circle, 135° diagonal).
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    primaryAction: (Pair<String, () -> Unit>)? = null,
    secondaryAction: (Pair<String, () -> Unit>)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 135° diagonal (top-left → bottom-right): linearGradient defaults run
        // from Offset.Zero to the bottom-right corner — exact for a square/circle.
        val gradient = Brush.linearGradient(
            colors = listOf(
                AppTheme.extendedColors.gradientStart,
                AppTheme.extendedColors.gradientEnd,
            ),
        )
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(brush = gradient, shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (primaryAction != null || secondaryAction != null) {
            Spacer(modifier = Modifier.height(24.dp))
            // Fixed-width CTA stack, centered by the parent Column. Pinning the width here
            // (rather than hugging the label) is what keeps every tab's empty-state CTA the
            // same shape — Home/Channels/Xtream all render an identical button regardless of
            // label length. 220dp clears the longest label ("Add single stream", ~168dp to
            // fit on one line at labelLarge) with headroom so it never wraps and clips.
            Column(
                modifier = Modifier.width(EmptyStateActionWidth),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (primaryAction != null) {
                    PrimaryButton(
                        text = primaryAction.first,
                        onClick = primaryAction.second,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (secondaryAction != null) {
                    TonalButton(
                        text = secondaryAction.first,
                        onClick = secondaryAction.second,
                        modifier = Modifier.fillMaxWidth(),
                        brand = true,
                    )
                }
            }
        }
    }
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun EmptyStatePreviewLight() {
    AppTheme(ThemeMode.Light) {
        EmptyState(
            icon = Icons.Outlined.LiveTv,
            title = "No channels yet",
            description = "Import an M3U playlist or add a single stream to start watching.",
            primaryAction = "Import playlist" to {},
            secondaryAction = "Add single stream" to {},
        )
    }
}

@Preview(name = "Dark", showBackground = true, backgroundColor = 0xFF0E0F13)
@Composable
private fun EmptyStatePreviewDark() {
    AppTheme(ThemeMode.Dark) {
        EmptyState(
            icon = Icons.Outlined.LiveTv,
            title = "No channels yet",
            description = "Import an M3U playlist or add a single stream to start watching.",
            primaryAction = "Import playlist" to {},
            secondaryAction = "Add single stream" to {},
        )
    }
}
