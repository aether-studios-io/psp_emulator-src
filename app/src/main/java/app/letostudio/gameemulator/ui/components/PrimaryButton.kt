package app.letostudio.gameemulator.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.letostudio.gameemulator.R
import app.letostudio.gameemulator.ui.theme.AppTheme
import app.letostudio.gameemulator.ui.theme.ThemeMode

/**
 * Primary CTA button — the ONLY place the brand gradient may fill a button.
 * Max one per screen (design_system.md section 2.3).
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    /** Single-colour icon, tinted white to sit on the gradient. */
    leadingIcon: ImageVector? = null,
    /**
     * Multi-colour art (the premium crown) drawn as-is — [leadingIcon] would tint it
     * flat white and lose the artwork. Ignored when [leadingIcon] is also set.
     */
    leadingPainter: Painter? = null,
    /** Smaller variant (40dp) for tight spots like a hero CTA. */
    compact: Boolean = false,
    /**
     * Overrides the label style. Defaults to the scale (labelLarge, or labelMedium when
     * [compact]).
     *
     * Only the two purchase-flow heroes override it, both to 16sp: onboarding and the
     * paywall it hands off to. Their CTAs are the screen, not a row action, and are
     * product-approved to run a little larger. Everywhere else keeps labelLarge — this
     * is not a general escape hatch from the type scale.
     */
    textStyle: TextStyle? = null,
) {
    val clickable = enabled && !loading
    Box(
        modifier = modifier
            .height(if (compact) 40.dp else 48.dp)
            .alpha(if (enabled) 1f else 0.38f)
            .clip(MaterialTheme.shapes.small)
            .background(AppTheme.extendedColors.brandGradient)
            .clickable(enabled = clickable, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (compact) 16.dp else 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            when {
                loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                    Box(modifier = Modifier.width(8.dp))
                }
                leadingIcon != null -> {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(if (compact) 18.dp else 20.dp),
                    )
                    Box(modifier = Modifier.width(6.dp))
                }
                leadingPainter != null -> {
                    Image(
                        painter = leadingPainter,
                        contentDescription = null,
                        modifier = Modifier.size(if (compact) 20.dp else 22.dp),
                    )
                    Box(modifier = Modifier.width(6.dp))
                }
            }
            Text(
                text = text,
                style = textStyle ?: if (compact) {
                    MaterialTheme.typography.labelMedium
                } else {
                    MaterialTheme.typography.labelLarge
                },
                color = Color.White,
            )
        }
    }
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun PrimaryButtonPreviewLight() {
    AppTheme(ThemeMode.Light) {
        Column(modifier = Modifier.padding(16.dp)) {
            PrimaryButton(text = "Import playlist", onClick = {})
            Box(modifier = Modifier.height(12.dp))
            PrimaryButton(text = "Importing", onClick = {}, loading = true)
            Box(modifier = Modifier.height(12.dp))
            PrimaryButton(text = "Import playlist", onClick = {}, enabled = false)
            Box(modifier = Modifier.height(12.dp))
            PrimaryButton(
                text = "Save",
                onClick = {},
                leadingPainter = painterResource(R.drawable.premium_icon),
            )
        }
    }
}

@Preview(name = "Dark", showBackground = true, backgroundColor = 0xFF0E0F13)
@Composable
private fun PrimaryButtonPreviewDark() {
    AppTheme(ThemeMode.Dark) {
        Column(modifier = Modifier.padding(16.dp)) {
            PrimaryButton(text = "Import playlist", onClick = {})
            Box(modifier = Modifier.height(12.dp))
            PrimaryButton(text = "Importing", onClick = {}, loading = true)
            Box(modifier = Modifier.height(12.dp))
            PrimaryButton(text = "Import playlist", onClick = {}, enabled = false)
            Box(modifier = Modifier.height(12.dp))
            PrimaryButton(
                text = "Save",
                onClick = {},
                leadingPainter = painterResource(R.drawable.premium_icon),
            )
        }
    }
}
