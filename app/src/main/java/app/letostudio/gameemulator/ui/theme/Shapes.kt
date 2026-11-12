package app.letostudio.gameemulator.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Mirrors design_token.json radiusDp / spacingDp.

object RnRadius {
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val pill = 999.dp
}

object RnSpace {
    val s1 = 4.dp
    val s2 = 8.dp
    val s3 = 12.dp
    val s4 = 16.dp
    val s5 = 20.dp
    val s6 = 24.dp
    val s8 = 32.dp
}

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(RnRadius.sm),
    small = RoundedCornerShape(RnRadius.md),
    medium = RoundedCornerShape(RnRadius.lg),
    large = RoundedCornerShape(RnRadius.xl),
    extraLarge = RoundedCornerShape(28.dp),
)
