package com.example.houseflow.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// HF-14 shared component library. Flat-design, theme-driven building blocks.
// Aesthetic: zero elevation, soft hairline borders, tinted icon containers,
// pill badges, subtle press feedback. "Soft Signal" design direction.

// A subtle scale-down on press (~0.97), driven by an interaction source. Kept
// short and small so it reads as tactile feedback, not a distracting animation.
@Composable
fun Modifier.pressScale(interactionSource: MutableInteractionSource): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "pressScale")
    return this.graphicsLayer { scaleX = scale; scaleY = scale }
}

// The canonical flat card: white surface, soft border, generous rounding, no
// elevation. When [onClick] is provided it becomes pressable with feedback.
@Composable
fun HFCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentPadding: Dp = 18.dp,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val clickModifier = if (onClick != null) {
        Modifier
            .pressScale(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    } else Modifier

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(clickModifier),
        shape = MaterialTheme.shapes.large,
        color = containerColor,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(contentPadding)) { content() }
    }
}

// A large title block for the top of a scrollable screen. Slightly more
// vertical breathing room and tighter subtitle coupling.
@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (trailing != null) trailing()
    }
}

// A section label with an optional trailing text action ("See all").
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

// A solid colored rounded-square icon container. The signature element: at
// size >= 40dp, a radial gradient adds a subtle dimensional quality.
@Composable
fun IconChip(
    icon: ImageVector,
    contentDescription: String? = null,
    tint: Color = MaterialTheme.colorScheme.primary,
    background: Color = tint.copy(alpha = 0.14f),
    size: Dp = 40.dp,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(size / 3)
    val bgModifier = if (size >= 40.dp) {
        // Signature: subtle radial gradient for dimensionality
        Modifier.background(
            brush = Brush.radialGradient(
                colors = listOf(
                    tint.copy(alpha = 0.18f),
                    tint.copy(alpha = 0.10f)
                ),
                center = Offset(size.value * 0.4f, size.value * 0.4f),
                radius = size.value * 0.9f
            ),
            shape = shape
        )
    } else {
        Modifier.background(color = background, shape = shape)
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .then(bgModifier),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(size * 0.52f)
        )
    }
}

// A pill badge — slightly more horizontal padding and tighter vertical for a
// more refined capsule shape.
@Composable
fun Pill(
    text: String,
    tint: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = tint.copy(alpha = 0.12f),
        contentColor = tint
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}

// A compact stat tile (icon + big value + label). Meant to sit in a Row.
@Composable
fun StatTile(
    label: String,
    value: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    HFCard(modifier = modifier, onClick = onClick, contentPadding = 16.dp) {
        IconChip(icon = icon, tint = tint)
        Spacer(Modifier.height(12.dp))
        Text(value, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// A centered empty-state placeholder.
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconChip(icon = icon, tint = MaterialTheme.colorScheme.onSurfaceVariant, size = 52.dp)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

// Full-width primary button with press feedback and friendlier shape.
@Composable
fun HFPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interaction = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(),
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .pressScale(interaction)
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

// Full-width secondary (outlined) button with press feedback.
@Composable
fun HFSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interaction = remember { MutableInteractionSource() }
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .pressScale(interaction)
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}
