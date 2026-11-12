package app.letostudio.gameemulator.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import app.letostudio.gameemulator.R
import app.letostudio.gameemulator.ui.theme.AppTheme
import app.letostudio.gameemulator.ui.theme.ThemeMode

/**
 * Standard top app bar — screen title, optional back navigation, optional actions.
 *
 * [containerColor]/[contentColor] default to the app background scheme; pass
 * `Color.Transparent` + a light content color to overlay the bar on top of hero
 * artwork (Xtream storefront). Everything else keeps the standard look.
 *
 * [compactTitle] drops the title to titleMedium — for now-playing titles (channel
 * name), matching the fullscreen player overlays; screen-name titles stay titleLarge.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    centerTitle: Boolean = false,
    compactTitle: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.background,
    contentColor: Color = MaterialTheme.colorScheme.onBackground,
) {
    val titleText: @Composable () -> Unit = {
        Text(
            text = title,
            style = if (compactTitle) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.titleLarge
            },
        )
    }
    val navigationIcon: @Composable () -> Unit = {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                )
            }
        }
    }
    val colors = TopAppBarDefaults.topAppBarColors(
        containerColor = containerColor,
        titleContentColor = contentColor,
        navigationIconContentColor = contentColor,
        actionIconContentColor = contentColor,
    )
    if (centerTitle) {
        CenterAlignedTopAppBar(
            title = titleText,
            navigationIcon = navigationIcon,
            actions = actions,
            colors = colors,
        )
    } else {
        TopAppBar(
            title = titleText,
            navigationIcon = navigationIcon,
            actions = actions,
            colors = colors,
        )
    }
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun AppTopBarPreviewLight() {
    AppTheme(ThemeMode.Light) {
        AppTopBar(
            title = "Channels",
            onBack = {},
            actions = {
                IconButton(onClick = {}) {
                    Icon(imageVector = Icons.Outlined.Search, contentDescription = null)
                }
            },
        )
    }
}

@Preview(name = "Dark", showBackground = true, backgroundColor = 0xFF0E0F13)
@Composable
private fun AppTopBarPreviewDark() {
    AppTheme(ThemeMode.Dark) {
        AppTopBar(
            title = "Channels",
            onBack = {},
            actions = {
                IconButton(onClick = {}) {
                    Icon(imageVector = Icons.Outlined.Search, contentDescription = null)
                }
            },
        )
    }
}

@Preview(name = "Centered", showBackground = true, backgroundColor = 0xFF0E0F13)
@Composable
private fun AppTopBarPreviewCentered() {
    AppTheme(ThemeMode.Dark) {
        AppTopBar(
            title = "BBC News HD",
            onBack = {},
            centerTitle = true,
            compactTitle = true,
        )
    }
}
