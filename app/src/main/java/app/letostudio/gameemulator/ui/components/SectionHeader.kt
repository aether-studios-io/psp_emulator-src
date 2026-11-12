package app.letostudio.gameemulator.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.letostudio.gameemulator.ui.theme.AppTheme
import app.letostudio.gameemulator.ui.theme.ThemeMode

/**
 * Section header (design_system.md section 7).
 *
 * Two levels of prominence:
 * - default ([prominent] = false): the quiet settings group label — `labelLarge`
 *   on `onSurfaceVariant` (design_system.md section 7 contract).
 * - [prominent] = true: a primary content-row title — `titleMedium` on
 *   `onBackground` (design_system.md section 4: "titleMedium — section headers"),
 *   so storefront rows read clearly as headings above their content.
 *
 * Optionally shows a trailing text action (e.g. "See All") on the right, rendered
 * in the brand primary color. The action is the single sanctioned way to add a
 * row-level affordance to a section header — screens must not fork this.
 */
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    action: Pair<String, () -> Unit>? = null,
    prominent: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = 16.dp,
                end = 16.dp,
                top = 24.dp,
                bottom = 8.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = text,
            style = if (prominent) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.labelLarge
            },
            color = if (prominent) {
                MaterialTheme.colorScheme.onBackground
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        if (action != null) {
            Text(
                text = action.first,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable(onClick = action.second)
                    .padding(start = 8.dp),
            )
        }
    }
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun SectionHeaderPreviewLight() {
    AppTheme(ThemeMode.Light) {
        SectionHeader(text = "Playback")
    }
}

@Preview(name = "Dark", showBackground = true, backgroundColor = 0xFF0E0F13)
@Composable
private fun SectionHeaderPreviewDark() {
    AppTheme(ThemeMode.Dark) {
        SectionHeader(text = "Movies", action = "See All" to {}, prominent = true)
    }
}
