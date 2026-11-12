package app.letostudio.gameemulator.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.letostudio.gameemulator.ui.theme.AppTheme
import app.letostudio.gameemulator.ui.theme.ThemeMode

/**
 * Consent checkbox with an inline link to the full agreement text. Gates the submit
 * button on the import forms — the raw M3 Checkbox may live only here
 * (design_system.md §7).
 */
@Composable
fun ConsentRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    text: String,
    linkText: String,
    onLinkClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        val annotated = buildAnnotatedString {
            append(text)
            withLink(
                LinkAnnotation.Clickable(
                    tag = "agreement",
                    styles = TextLinkStyles(
                        style = SpanStyle(
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                        ),
                    ),
                ) { onLinkClick() },
            ) { append(linkText) }
        }
        Text(
            text = annotated,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun ConsentRowPreviewLight() {
    AppTheme(ThemeMode.Light) {
        Column(Modifier.padding(16.dp)) {
            ConsentRow(
                checked = false,
                onCheckedChange = {},
                text = "I agree to the ",
                linkText = "License Agreement",
                onLinkClick = {},
            )
            ConsentRow(
                checked = true,
                onCheckedChange = {},
                text = "I agree to the ",
                linkText = "License Agreement",
                onLinkClick = {},
            )
        }
    }
}

@Preview(name = "Dark", showBackground = true, backgroundColor = 0xFF0E0F13)
@Composable
private fun ConsentRowPreviewDark() {
    AppTheme(ThemeMode.Dark) {
        Column(Modifier.padding(16.dp)) {
            ConsentRow(
                checked = false,
                onCheckedChange = {},
                text = "I agree to the ",
                linkText = "License Agreement",
                onLinkClick = {},
            )
            ConsentRow(
                checked = true,
                onCheckedChange = {},
                text = "I agree to the ",
                linkText = "License Agreement",
                onLinkClick = {},
            )
        }
    }
}
