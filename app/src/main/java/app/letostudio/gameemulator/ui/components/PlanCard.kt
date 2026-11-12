package app.letostudio.gameemulator.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.letostudio.gameemulator.ui.theme.AppTheme
import app.letostudio.gameemulator.ui.theme.ThemeMode

/**
 * Selectable paywall plan card (FR-042): plan name, billing period and the localized
 * price from Google Play. Selection uses tonal primaryContainer per design_system.md §8;
 * the "Best value" badge is a solid-primary pill straddling the top border (never the
 * brand gradient — §2.3 keeps gradients off badges).
 */
@Composable
fun PlanCard(
    name: String,
    price: String,
    periodLabel: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    bestValueLabel: String? = null,
    /**
     * Store screen only: marks the plan the user currently owns with a tertiary pill on
     * the leading edge. Independent of [bestValueLabel] (a card can be both), and drawn
     * in tertiary so it never reads as the primary "best value" call-to-action.
     */
    currentLabel: String? = null,
) {
    val border = if (selected) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    }
    val container = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val nameColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val secondaryColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    // The badge overhangs the card's top edge, so it can't live inside the clipped
    // card: the Box reserves half a badge of unclipped room above it.
    Box(modifier = modifier.fillMaxWidth().padding(top = BadgeOverhang)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(container)
                .border(border, MaterialTheme.shapes.medium)
                .clickable(role = Role.RadioButton, onClick = onClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    color = nameColor,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = periodLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryColor,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = price,
                style = MaterialTheme.typography.titleMedium,
                color = nameColor,
            )
        }
        if (bestValueLabel != null) {
            Text(
                text = bestValueLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-12).dp, y = -BadgeOverhang)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 10.dp, vertical = 2.dp),
            )
        }
        if (currentLabel != null) {
            // Leading edge (TopStart) so it never collides with the trailing "best
            // value" pill — a plan can be both the current one and the best offer.
            Text(
                text = currentLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onTertiary,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = 12.dp, y = -BadgeOverhang)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiary)
                    .padding(horizontal = 10.dp, vertical = 2.dp),
            )
        }
    }
}

/** Half the badge height — how far the pill rises above the card's top border. */
private val BadgeOverhang = 10.dp

@Preview(name = "Light", showBackground = true)
@Composable
private fun PlanCardPreviewLight() {
    AppTheme(ThemeMode.Light) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PlanCard(
                name = "Monthly",
                price = "$4.99",
                periodLabel = "Billed every month",
                selected = true,
                onClick = {},
                bestValueLabel = "Best value",
            )
            PlanCard(
                name = "Weekly",
                price = "$1.99",
                periodLabel = "Billed every week",
                selected = false,
                onClick = {},
            )
        }
    }
}

@Preview(name = "Dark", showBackground = true, backgroundColor = 0xFF0E0F13)
@Composable
private fun PlanCardPreviewDark() {
    AppTheme(ThemeMode.Dark) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PlanCard(
                name = "Monthly",
                price = "$4.99",
                periodLabel = "Billed every month",
                selected = true,
                onClick = {},
                bestValueLabel = "Best value",
            )
            PlanCard(
                name = "Yearly",
                price = "$29.99",
                periodLabel = "Billed every year",
                selected = false,
                onClick = {},
                currentLabel = "Current",
            )
        }
    }
}
