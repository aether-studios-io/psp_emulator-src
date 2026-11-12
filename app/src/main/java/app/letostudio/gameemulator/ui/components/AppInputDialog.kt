package app.letostudio.gameemulator.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.letostudio.gameemulator.ui.theme.AppTheme
import app.letostudio.gameemulator.ui.theme.ThemeMode

/**
 * Design-system single-field input dialog — the same surface card recipe as
 * [AppDialog] (radius 12dp, outlineVariant hairline, 24dp padding) with an
 * [AppTextField] and a TonalButton (dismiss) + solid-primary confirm pair.
 * Confirm hands the edited text back; dismiss discards it.
 */
@Composable
fun AppInputDialog(
    title: String,
    initialValue: String,
    confirmText: String,
    dismissText: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    message: String? = null,
) {
    var text by rememberSaveable { mutableStateOf(initialValue) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (message != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(16.dp))
                AppTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = label ?: title,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TonalButton(
                        text = dismissText,
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                    InputConfirmButton(
                        text = confirmText,
                        onClick = { onConfirm(text) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** Solid-primary confirm button — dialog-only (PrimaryButton's gradient stays max one per screen). */
@Composable
private fun InputConfirmButton(
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
private fun AppInputDialogPreviewLight() {
    AppTheme(ThemeMode.Light) {
        AppInputDialog(
            title = "Default User-Agent",
            initialValue = "Mozilla/5.0 (Linux; Android 14)",
            confirmText = "Save",
            dismissText = "Cancel",
            onConfirm = {},
            onDismiss = {},
        )
    }
}

@Preview(name = "Dark", showBackground = true, backgroundColor = 0xFF0E0F13)
@Composable
private fun AppInputDialogPreviewDark() {
    AppTheme(ThemeMode.Dark) {
        AppInputDialog(
            title = "Default User-Agent",
            initialValue = "Mozilla/5.0 (Linux; Android 14)",
            confirmText = "Save",
            dismissText = "Cancel",
            onConfirm = {},
            onDismiss = {},
            message = "Sent with every stream request unless a source overrides it.",
        )
    }
}
