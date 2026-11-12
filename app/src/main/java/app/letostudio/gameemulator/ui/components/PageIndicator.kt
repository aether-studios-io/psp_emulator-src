package app.letostudio.gameemulator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.letostudio.gameemulator.ui.theme.AppTheme
import app.letostudio.gameemulator.ui.theme.ThemeMode

/**
 * Page-position pills for a pager. Every pill is the same size — only the fill
 * separates the current page from the rest, so the row never reflows on swipe.
 *
 * Decorative: the pager itself carries the semantics, so the pills stay out of
 * the accessibility tree.
 */
@Composable
fun PageIndicator(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.outlineVariant,
) {
    if (pageCount <= 1) return
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(PillGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            Box(
                modifier = Modifier
                    .size(width = PillWidth, height = PillHeight)
                    .clip(CircleShape)
                    .background(if (index == currentPage) activeColor else inactiveColor),
            )
        }
    }
}

private val PillWidth = 14.dp
private val PillHeight = 4.dp
private val PillGap = 4.dp

@Preview(name = "Light", showBackground = true)
@Composable
private fun PageIndicatorPreviewLight() {
    AppTheme(ThemeMode.Light) {
        Column(modifier = Modifier.padding(16.dp)) {
            PageIndicator(pageCount = 3, currentPage = 0)
            Box(modifier = Modifier.height(12.dp))
            PageIndicator(pageCount = 3, currentPage = 2)
        }
    }
}

@Preview(name = "Dark", showBackground = true, backgroundColor = 0xFF0E0F13)
@Composable
private fun PageIndicatorPreviewDark() {
    AppTheme(ThemeMode.Dark) {
        Column(modifier = Modifier.padding(16.dp)) {
            PageIndicator(pageCount = 3, currentPage = 0)
            Box(modifier = Modifier.height(12.dp))
            PageIndicator(pageCount = 3, currentPage = 2)
        }
    }
}
