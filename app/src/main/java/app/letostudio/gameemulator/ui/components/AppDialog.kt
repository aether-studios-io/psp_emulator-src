package app.letostudio.gameemulator.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.letostudio.gameemulator.ui.theme.AppTheme
import app.letostudio.gameemulator.ui.theme.ThemeMode

/**
 * Design-system confirmation dialog — surface card (radius 12dp, outlineVariant hairline),
 * optional tonal icon circle, centered titleMedium + bodyMedium copy, full-width button pair:
 * TonalButton (dismiss) + DestructiveButton or solid-primary confirm.
 *
 * [dismissText] = null renders a single full-width confirm button (informational dialogs).
 * [onDismiss] also handles taps outside / back.
 */
@Composable
fun AppDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissText: String? = null,
    icon: ImageVector? = null,
    destructive: Boolean = false,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (icon != null) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                color = if (destructive) {
                                    MaterialTheme.colorScheme.errorContainer
                                } else {
                                    MaterialTheme.colorScheme.primaryContainer
                                },
                                shape = CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = if (destructive) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            },
                        )
                    }
                    Box(Modifier.height(16.dp))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Box(Modifier.height(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Box(Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (dismissText != null) {
                        TonalButton(
                            text = dismissText,
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (destructive) {
                        DestructiveButton(
                            text = confirmText,
                            onClick = onConfirm,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        DialogConfirmButton(
                            text = confirmText,
                            onClick = onConfirm,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/** Solid-primary confirm button — dialog-only (PrimaryButton's gradient stays max one per screen). */
@Composable
private fun DialogConfirmButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun AppDialogPreviewLight() {
    AppTheme(ThemeMode.Light) {
        AppDialog(
            title = "Remove account?",
            message = "This removes user123 and its saved progress. You can reconnect later.",
            confirmText = "Remove",
            onConfirm = {},
            onDismiss = {},
            dismissText = "Cancel",
            icon = Icons.Outlined.Delete,
            destructive = true,
        )
    }
}

@Preview(name = "Dark", showBackground = true, backgroundColor = 0xFF0E0F13)
@Composable
private fun AppDialogPreviewDark() {
    AppTheme(ThemeMode.Dark) {
        AppDialog(
            title = "Remove account?",
            message = "This removes user123 and its saved progress. You can reconnect later.",
            confirmText = "Remove",
            onConfirm = {},
            onDismiss = {},
            dismissText = "Cancel",
            icon = Icons.Outlined.Delete,
            destructive = true,
        )
    }
}

@Preview(name = "Dark single button", showBackground = true, backgroundColor = 0xFF0E0F13)
@Composable
private fun AppDialogPreviewSingleButton() {
    AppTheme(ThemeMode.Dark) {
        AppDialog(
            title = "Playlist imported",
            message = "Found 1,240 channels in 32 groups.",
            confirmText = "OK",
            onConfirm = {},
            onDismiss = {},
            icon = Icons.Outlined.CheckCircle,
        )
    }
}
