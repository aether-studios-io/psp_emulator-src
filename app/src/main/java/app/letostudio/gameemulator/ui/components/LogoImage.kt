package app.letostudio.gameemulator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.letostudio.gameemulator.ui.theme.AppTheme
import app.letostudio.gameemulator.ui.theme.ThemeMode
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade

/**
 * Channel/stream logo — 8dp-rounded box (design_system.md section 7).
 * Falls back to a 2-letter monogram on surfaceVariant when the url is missing,
 * while loading, or on load error.
 */
@Composable
fun LogoImage(url: String?, name: String, size: Dp = 40.dp) {
    val boxModifier = Modifier
        .size(size)
        .clip(MaterialTheme.shapes.small)
    if (url.isNullOrBlank()) {
        Monogram(name = name, modifier = boxModifier)
    } else {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(url)
                .crossfade(true)
                .build(),
            contentDescription = name,
            contentScale = ContentScale.Fit,
            modifier = boxModifier,
            loading = { Monogram(name = name, modifier = Modifier.fillMaxSize()) },
            error = { Monogram(name = name, modifier = Modifier.fillMaxSize()) },
        )
    }
}

@Composable
private fun Monogram(name: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = monogramOf(name),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** First letters of the first two words, or the first two letters of a single word. */
private fun monogramOf(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        words.isEmpty() -> ""
        words.size == 1 -> words[0].take(2).uppercase()
        else -> "${words[0].first()}${words[1].first()}".uppercase()
    }
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun LogoImagePreviewLight() {
    AppTheme(ThemeMode.Light) {
        Row(modifier = Modifier.padding(16.dp)) {
            LogoImage(url = null, name = "Sky News")
            Box(modifier = Modifier.width(12.dp))
            LogoImage(url = null, name = "Arte")
            Box(modifier = Modifier.width(12.dp))
            LogoImage(url = null, name = "", size = 56.dp)
        }
    }
}

@Preview(name = "Dark", showBackground = true, backgroundColor = 0xFF0E0F13)
@Composable
private fun LogoImagePreviewDark() {
    AppTheme(ThemeMode.Dark) {
        Row(modifier = Modifier.padding(16.dp)) {
            LogoImage(url = null, name = "Sky News")
            Box(modifier = Modifier.width(12.dp))
            LogoImage(url = null, name = "Arte")
            Box(modifier = Modifier.width(12.dp))
            LogoImage(url = null, name = "", size = 56.dp)
        }
    }
}
