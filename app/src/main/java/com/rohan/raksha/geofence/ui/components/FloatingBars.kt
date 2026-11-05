package com.rohan.raksha.geofence.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rohan.raksha.geofence.R
import com.rohan.raksha.geofence.ui.theme.Raksha
import kotlin.math.roundToInt

/** Height of a floating bar including its 12 dp margin above and below. */
val FloatingBarSpace = 88.dp

/**
 * Floating header pill: round app icon (or back button), title + subtitle, buttons on the right.
 * Sits on top of the map; pad scrolling content with [FloatingBarSpace] + status bar inset.
 */
@Composable
fun FloatingTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val c = Raksha.colors
    val shape = RoundedCornerShape(32.dp)
    Box(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .floatingShadow(shape)
                .clip(shape)
                .background(Color.White)
                .border(1.dp, c.border, shape)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconChip(R.drawable.ph_arrow_left_bold, "Back", onBack, size = 44.dp)
            } else {
                Image(
                    painter = painterResource(R.drawable.ic_raksha_circle),
                    contentDescription = "Raksha",
                    modifier = Modifier.size(44.dp).clip(CircleShape)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                OneLineText(
                    title,
                    color = if (onBack == null) c.rose else c.text,
                    fontSize = if (onBack == null) 20.sp else 17.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                if (subtitle != null) {
                    OneLineText(subtitle, color = c.textMuted, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                }
            }
            actions()
        }
    }
}

private class NavEntry(val label: String, val icon: Int, val iconFill: Int)

private val entries = listOf(
    NavEntry("Map", R.drawable.ph_map_trifold, R.drawable.ph_map_trifold_fill),
    NavEntry("Zones", R.drawable.ph_shield_check, R.drawable.ph_shield_check_fill),
    NavEntry("Settings", R.drawable.ph_gear_six, R.drawable.ph_gear_six_fill)
)

/**
 * Floating pill navigation: the selected item grows into a #FF2541 pill with its label.
 * Icon and label live in a clipped slot, so nothing pokes out while animating.
 */
@Composable
fun FloatingBottomBar(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = Raksha.colors
    val shape = RoundedCornerShape(32.dp)
    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .floatingShadow(shape)
                .clip(shape)
                .background(Color.White)
                .border(1.dp, c.border, shape)
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            entries.forEachIndexed { i, item ->
                val sel = i == selected
                val tState = animateFloatAsState(
                    targetValue = if (sel) 1f else 0f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 380f),
                    label = "navT"
                )
                val t = tState.value.coerceIn(0f, 1f)
                Box(
                    Modifier
                        .weight(1f + 0.9f * t)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(26.dp))
                        .background(c.rose.copy(alpha = t))
                        .bounceClick { onSelect(i) }
                        .clipToBounds(),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PhIcon(
                            if (sel) item.iconFill else item.icon,
                            tint = lerp(c.textMuted, Color.White, t),
                            size = 23.dp,
                            contentDescription = item.label
                        )
                        Box(
                            Modifier
                                .layout { measurable, constraints ->
                                    val p = measurable.measure(
                                        constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity)
                                    )
                                    val w = (p.width * tState.value.coerceIn(0f, 1f))
                                        .roundToInt()
                                        .coerceAtMost(constraints.maxWidth)
                                    layout(w, p.height) { p.placeRelative(0, 0) }
                                }
                                .graphicsLayer { alpha = tState.value.coerceIn(0f, 1f) }
                        ) {
                            Text(
                                item.label,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(start = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
