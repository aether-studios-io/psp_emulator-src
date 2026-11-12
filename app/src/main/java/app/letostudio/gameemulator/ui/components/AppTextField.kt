package app.letostudio.gameemulator.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.letostudio.gameemulator.R
import app.letostudio.gameemulator.ui.theme.AppTheme
import app.letostudio.gameemulator.ui.theme.ThemeMode

/**
 * The app's single text field — OutlinedTextField wrapper, radius 8dp,
 * error via supportingText + error color, optional password visibility toggle.
 * [onClear] adds a trailing X while the field has text (search-style fields);
 * mutually exclusive with [isPassword], whose toggle owns the trailing slot.
 */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    supporting: String? = null,
    isPassword: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    onClear: (() -> Unit)? = null,
) {
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(text = label) },
        isError = error != null,
        supportingText = when {
            error != null -> {
                { Text(text = error, color = MaterialTheme.colorScheme.error) }
            }
            supporting != null -> {
                { Text(text = supporting) }
            }
            else -> null
        },
        visualTransformation = if (isPassword && !passwordVisible) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        trailingIcon = when {
            isPassword -> {
                {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) {
                                Icons.Filled.VisibilityOff
                            } else {
                                Icons.Filled.Visibility
                            },
                            contentDescription = stringResource(
                                if (passwordVisible) R.string.cd_hide_password else R.string.cd_show_password
                            ),
                        )
                    }
                }
            }
            onClear != null && value.isNotEmpty() -> {
                {
                    IconButton(onClick = onClear) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.cd_clear_text),
                        )
                    }
                }
            }
            else -> null
        },
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = true,
        shape = MaterialTheme.shapes.small,
    )
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun AppTextFieldPreviewLight() {
    AppTheme(ThemeMode.Light) {
        Column(modifier = Modifier.padding(16.dp)) {
            AppTextField(
                value = "http://example.com/playlist.m3u",
                onValueChange = {},
                label = "Playlist URL",
                modifier = Modifier.fillMaxWidth(),
                supporting = "M3U or M3U8 link",
            )
            Spacer(modifier = Modifier.height(12.dp))
            AppTextField(
                value = "not-a-url",
                onValueChange = {},
                label = "Playlist URL",
                modifier = Modifier.fillMaxWidth(),
                error = "Enter a valid URL",
            )
            Spacer(modifier = Modifier.height(12.dp))
            AppTextField(
                value = "secret123",
                onValueChange = {},
                label = "Password",
                modifier = Modifier.fillMaxWidth(),
                isPassword = true,
            )
        }
    }
}

@Preview(name = "Dark", showBackground = true, backgroundColor = 0xFF0E0F13)
@Composable
private fun AppTextFieldPreviewDark() {
    AppTheme(ThemeMode.Dark) {
        Column(modifier = Modifier.padding(16.dp)) {
            AppTextField(
                value = "http://example.com/playlist.m3u",
                onValueChange = {},
                label = "Playlist URL",
                modifier = Modifier.fillMaxWidth(),
                supporting = "M3U or M3U8 link",
            )
            Spacer(modifier = Modifier.height(12.dp))
            AppTextField(
                value = "not-a-url",
                onValueChange = {},
                label = "Playlist URL",
                modifier = Modifier.fillMaxWidth(),
                error = "Enter a valid URL",
            )
            Spacer(modifier = Modifier.height(12.dp))
            AppTextField(
                value = "secret123",
                onValueChange = {},
                label = "Password",
                modifier = Modifier.fillMaxWidth(),
                isPassword = true,
            )
        }
    }
}
