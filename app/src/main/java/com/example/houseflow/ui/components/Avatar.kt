package com.example.houseflow.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.houseflow.ui.theme.BrandIndigo
import com.example.houseflow.ui.theme.Gold
import com.example.houseflow.ui.theme.Mauve
import com.example.houseflow.ui.theme.Orange
import com.example.houseflow.ui.theme.Pink
import com.example.houseflow.ui.theme.Teal

// A fixed brand palette; a name maps deterministically to one of these so the
// same person always gets the same avatar color across screens.
private val AVATAR_COLORS = listOf(BrandIndigo, Teal, Mauve, Orange, Pink, Gold)

fun avatarColor(name: String): Color {
    if (name.isEmpty()) return AVATAR_COLORS.first()
    val hash = name.fold(0) { acc, c -> acc * 31 + c.code }
    return AVATAR_COLORS[(hash % AVATAR_COLORS.size + AVATAR_COLORS.size) % AVATAR_COLORS.size]
}

// A colored circle showing the name's initial. Color is derived from the name.
// Slightly higher background opacity and bolder initial for better legibility.
@Composable
fun Avatar(name: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val color = avatarColor(name)
    Surface(
        modifier = modifier.size(size),
        shape = CircleShape,
        color = color.copy(alpha = 0.14f),
        contentColor = color
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = name.take(1).uppercase(),
                fontSize = (size.value * 0.4f).sp,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleSmall,
                color = color
            )
        }
    }
}
