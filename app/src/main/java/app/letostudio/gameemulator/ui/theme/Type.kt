package app.letostudio.gameemulator.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.letostudio.gameemulator.R

// Mirrors design_token.json typography: Be Vietnam Pro (bundled, SIL OFL 1.1).

val BeVietnamPro = FontFamily(
    Font(R.font.be_vietnam_pro_regular, FontWeight.Normal),
    Font(R.font.be_vietnam_pro_medium, FontWeight.Medium),
    Font(R.font.be_vietnam_pro_semibold, FontWeight.SemiBold),
    Font(R.font.be_vietnam_pro_bold, FontWeight.Bold),
    Font(R.font.be_vietnam_pro_extrabold, FontWeight.ExtraBold),
)

private fun style(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0) = TextStyle(
    fontFamily = BeVietnamPro,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = weight,
    letterSpacing = tracking.sp,
)

/** The eight token styles by their design-system names. Read through [RnTheme.type]. */
@Immutable
data class RnType(
    val display: TextStyle = style(32, 38, FontWeight.ExtraBold, -0.5),
    val headline: TextStyle = style(24, 30, FontWeight.Bold, -0.3),
    val title: TextStyle = style(18, 24, FontWeight.Bold),
    val subtitle: TextStyle = style(16, 22, FontWeight.SemiBold),
    val body: TextStyle = style(15, 22, FontWeight.Normal),
    val label: TextStyle = style(14, 20, FontWeight.SemiBold),
    val caption: TextStyle = style(12, 16, FontWeight.Medium),
    /** Uppercase in use (textTransform). */
    val overline: TextStyle = style(11, 14, FontWeight.Bold, 0.8),
)

val RnTypeScale = RnType()

val AppTypography = Typography(
    displaySmall = RnTypeScale.display,
    headlineMedium = RnTypeScale.headline,
    headlineSmall = RnTypeScale.headline,
    titleLarge = RnTypeScale.title,
    titleMedium = RnTypeScale.subtitle,
    titleSmall = RnTypeScale.label,
    bodyLarge = RnTypeScale.body,
    bodyMedium = RnTypeScale.body.copy(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = RnTypeScale.caption,
    labelLarge = RnTypeScale.label,
    labelMedium = RnTypeScale.caption,
    labelSmall = RnTypeScale.overline,
)
