package app.letostudio.gameemulator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.letostudio.gameemulator.ui.theme.AppTheme
import app.letostudio.gameemulator.ui.theme.ThemeMode

/**
 * Container grouping a cluster of settings rows (design_system.md section 7):
 * surface card, radius 12dp (shapes.medium), outlineVariant hairline — the same
 * card recipe as AppDialog/AppMenu. Rows inside supply their own 16dp horizontal
 * padding; the clip keeps their ripples inside the rounded corners.
 */
@Composable
fun SettingsGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium),
        content = content,
    )
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun SettingsGroupPreviewLight() {
    AppTheme(ThemeMode.Light) {
        SettingsGroup {
            Text("Row one", modifier = Modifier.padding(16.dp))
            Text("Row two", modifier = Modifier.padding(16.dp))
        }
    }
}

@Preview(name = "Dark", showBackground = true, backgroundColor = 0xFF0E0F13)
@Composable
private fun SettingsGroupPreviewDark() {
    AppTheme(ThemeMode.Dark) {
        SettingsGroup {
            Text("Row one", modifier = Modifier.padding(16.dp))
            Text("Row two", modifier = Modifier.padding(16.dp))
        }
    }
}
