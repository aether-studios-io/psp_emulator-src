package app.letostudio.gameemulator.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.letostudio.gameemulator.ui.theme.AppTheme
import app.letostudio.gameemulator.ui.theme.ThemeMode

/**
 * Secondary action button — secondaryContainer / onSecondaryContainer, radius 8dp, height 48dp.
 * [brand] = true switches to primaryContainer / onPrimaryContainer for a brand-tinted
 * secondary CTA that pairs with a gradient [PrimaryButton] (e.g. empty-state button stacks);
 * neutral actions like Retry/Cancel keep the default.
 */
@Composable
fun TonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    brand: Boolean = false,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = if (brand) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
            contentColor = if (brand) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            },
        ),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun TonalButtonPreviewLight() {
    AppTheme(ThemeMode.Light) {
        Column(modifier = Modifier.padding(16.dp)) {
            TonalButton(text = "Retry", onClick = {})
            Box(modifier = Modifier.height(12.dp))
            TonalButton(text = "Retry", onClick = {}, enabled = false)
            Box(modifier = Modifier.height(12.dp))
            TonalButton(text = "Connect Xtream", onClick = {}, brand = true)
        }
    }
}

@Preview(name = "Dark", showBackground = true, backgroundColor = 0xFF0E0F13)
@Composable
private fun TonalButtonPreviewDark() {
    AppTheme(ThemeMode.Dark) {
        Column(modifier = Modifier.padding(16.dp)) {
            TonalButton(text = "Retry", onClick = {})
            Box(modifier = Modifier.height(12.dp))
            TonalButton(text = "Retry", onClick = {}, enabled = false)
            Box(modifier = Modifier.height(12.dp))
            TonalButton(text = "Connect Xtream", onClick = {}, brand = true)
        }
    }
}
