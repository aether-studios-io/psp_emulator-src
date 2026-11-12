package app.letostudio.gameemulator.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import app.letostudio.gameemulator.ui.theme.AppTheme
import app.letostudio.gameemulator.ui.theme.ThemeMode

/**
 * Design-system dropdown menu — surface container, radius 12dp, outlineVariant hairline,
 * flat (no heavy elevation shadow). Populate with [AppMenuItem] and [AppMenuDivider].
 */
@Composable
fun AppMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset.Zero,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        offset = offset,
        shape = MaterialTheme.shapes.medium,
        containerColor = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 4.dp,
        content = content,
    )
}

/**
 * Menu row — optional 20dp leading icon; [selected] adds a trailing primary check + primary text;
 * [destructive] switches text and icon to error.
 */
@Composable
fun AppMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    selected: Boolean = false,
    destructive: Boolean = false,
) {
    val contentColor = when {
        destructive -> MaterialTheme.colorScheme.error
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val iconColor = when {
        destructive -> MaterialTheme.colorScheme.error
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    DropdownMenuItem(
        text = {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor,
            )
        },
        onClick = onClick,
        modifier = modifier,
        leadingIcon = icon?.let {
            {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = iconColor,
                )
            }
        },
        trailingIcon = if (selected) {
            {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        } else {
            null
        },
        colors = MenuDefaults.itemColors(
            textColor = contentColor,
            leadingIconColor = iconColor,
        ),
    )
}

/** Hairline divider between menu groups. */
@Composable
fun AppMenuDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 4.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun AppMenuPreviewLight() {
    AppTheme(ThemeMode.Light) {
        Box {
            AppMenu(expanded = true, onDismissRequest = {}) {
                AppMenuItem(text = "main-account", onClick = {}, selected = true)
                AppMenuItem(text = "backup-account", onClick = {})
                AppMenuDivider()
                AppMenuItem(text = "Add account", onClick = {}, icon = Icons.Outlined.Add)
                AppMenuItem(
                    text = "Delete account",
                    onClick = {},
                    icon = Icons.Outlined.Delete,
                    destructive = true,
                )
            }
        }
    }
}

@Preview(name = "Dark", showBackground = true, backgroundColor = 0xFF0E0F13)
@Composable
private fun AppMenuPreviewDark() {
    AppTheme(ThemeMode.Dark) {
        Box {
            AppMenu(expanded = true, onDismissRequest = {}) {
                AppMenuItem(text = "main-account", onClick = {}, selected = true)
                AppMenuItem(text = "backup-account", onClick = {})
                AppMenuDivider()
                AppMenuItem(text = "Add account", onClick = {}, icon = Icons.Outlined.Add)
                AppMenuItem(
                    text = "Delete account",
                    onClick = {},
                    icon = Icons.Outlined.Delete,
                    destructive = true,
                )
            }
        }
    }
}
