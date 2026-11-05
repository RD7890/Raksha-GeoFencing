package com.rohan.raksha.geofence.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rohan.raksha.geofence.ui.theme.Raksha

// ── Icons ────────────────────────────────────────────────────────────────────

/** Phosphor icon (MIT) as a tinted vector drawable. */
@Composable
fun PhIcon(
    @DrawableRes id: Int,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    size: Dp = 24.dp,
    contentDescription: String? = null
) {
    Icon(
        painter = painterResource(id),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = tint
    )
}

/** Soft rounded-square icon holder used on list rows. */
@Composable
fun IconBadge(
    @DrawableRes icon: Int,
    tint: Color,
    background: Color,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    shape: Shape = RoundedCornerShape(14.dp)
) {
    Box(
        modifier = modifier.size(size).clip(shape).background(background),
        contentAlignment = Alignment.Center
    ) { PhIcon(icon, tint = tint, size = iconSize) }
}

// ── Interaction ──────────────────────────────────────────────────────────────

/** Springy press feedback (no ripple) + click. */
fun Modifier.bounceClick(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "bounce"
    )
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(
            interactionSource = source,
            indication = null,
            enabled = enabled,
            role = Role.Button,
            onClick = onClick
        )
}

/** Soft floating shadow used by every bar / card that sits on top of the map. */
fun Modifier.floatingShadow(shape: Shape, elevation: Dp = 14.dp): Modifier =
    shadow(
        elevation, shape,
        ambientColor = Color.Black.copy(alpha = 0.18f),
        spotColor = Color.Black.copy(alpha = 0.18f)
    )

// ── Text that never wraps ────────────────────────────────────────────────────

/** Single line text that quietly shrinks (down to 9 sp) instead of wrapping. */
@Composable
fun OneLineText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = 14.sp,
    fontWeight: FontWeight? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textAlign: TextAlign? = null,
    style: TextStyle = LocalTextStyle.current
) {
    var fitted by remember(text, fontSize) { mutableStateOf(fontSize) }
    var ready by remember(text, fontSize) { mutableStateOf(false) }
    Text(
        text = text,
        modifier = modifier.drawWithContent { if (ready) drawContent() },
        color = color,
        fontSize = fitted,
        fontWeight = fontWeight,
        letterSpacing = letterSpacing,
        textAlign = textAlign,
        style = style,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        onTextLayout = { result ->
            if (result.didOverflowWidth && fitted.value > 9f) {
                fitted = (fitted.value * 0.92f).sp
            } else {
                ready = true
            }
        }
    )
}

// ── Surfaces ─────────────────────────────────────────────────────────────────

/** Flat soft-grey card. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val c = Raksha.colors
    val base = if (onClick != null) modifier.bounceClick(onClick = onClick) else modifier
    Column(
        modifier = base.clip(shape).background(c.card).padding(contentPadding),
        content = content
    )
}

/** White floating panel (sits on the map): rounded, hairline border, soft shadow. */
@Composable
fun FloatingPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val c = Raksha.colors
    Column(
        modifier = modifier
            .floatingShadow(shape)
            .clip(shape)
            .background(Color.White)
            .border(1.dp, c.border, shape)
            .padding(contentPadding),
        content = content
    )
}

/** Round white map button (follow me, add, ...). */
@Composable
fun MapFab(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    size: Dp = 52.dp
) {
    val c = Raksha.colors
    Box(
        modifier = modifier
            .size(size)
            .floatingShadow(CircleShape, 10.dp)
            .bounceClick(onClick = onClick)
            .clip(CircleShape)
            .background(if (filled) c.rose else Color.White)
            .border(1.dp, if (filled) c.rose else c.border, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        PhIcon(icon, tint = if (filled) Color.White else c.text, size = 24.dp, contentDescription = contentDescription)
    }
}

/** Round soft icon button for headers. */
@Composable
fun IconChip(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    size: Dp = 44.dp
) {
    val c = Raksha.colors
    Box(
        modifier = modifier.size(size).bounceClick(onClick = onClick).clip(CircleShape).background(c.card),
        contentAlignment = Alignment.Center
    ) {
        PhIcon(
            icon,
            tint = if (tint == Color.Unspecified) c.text else tint,
            size = 22.dp,
            contentDescription = contentDescription
        )
    }
}

/** Small rounded label: "Protected", "Home". */
@Composable
fun Pill(
    text: String,
    color: Color,
    background: Color,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null
) {
    Row(
        modifier = modifier.clip(RoundedCornerShape(100)).background(background)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            PhIcon(icon, tint = color, size = 13.dp)
            Spacer(Modifier.width(5.dp))
        }
        OneLineText(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp)
    }
}

// ── Buttons ──────────────────────────────────────────────────────────────────

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    height: Dp = 56.dp
) {
    val c = Raksha.colors
    val shape = RoundedCornerShape(18.dp)
    val fg = if (enabled) c.onAccent else c.textFaint
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .bounceClick(enabled = enabled && !loading, onClick = onClick)
            .clip(shape)
            .background(if (enabled) c.rose else c.cardHigh)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (loading) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(20.dp), color = fg, strokeWidth = 2.5.dp
                )
                Spacer(Modifier.width(10.dp))
            } else if (icon != null) {
                PhIcon(icon, tint = fg, size = 22.dp)
                Spacer(Modifier.width(10.dp))
            }
            OneLineText(text, color = fg, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.2.sp)
        }
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    contentColor: Color = Color.Unspecified,
    height: Dp = 52.dp
) {
    val c = Raksha.colors
    val tint = if (contentColor == Color.Unspecified) c.text else contentColor
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .bounceClick(onClick = onClick)
            .clip(shape)
            .background(Color.White)
            .border(1.5.dp, if (tint == c.text) c.border else tint, shape)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (icon != null) {
                PhIcon(icon, tint = tint, size = 21.dp)
                Spacer(Modifier.width(10.dp))
            }
            OneLineText(text, color = tint, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Compact pill button for rows ("Enable", "Open"). */
@Composable
fun SmallButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    filled: Boolean = true
) {
    val c = Raksha.colors
    val shape = RoundedCornerShape(100)
    Row(
        modifier = modifier
            .height(38.dp)
            .bounceClick(onClick = onClick)
            .clip(shape)
            .background(if (filled) c.rose else c.roseSoft)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            PhIcon(icon, tint = if (filled) c.onAccent else c.rose, size = 16.dp)
            Spacer(Modifier.width(6.dp))
        }
        OneLineText(text, color = if (filled) c.onAccent else c.rose, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

// ── Form styling ─────────────────────────────────────────────────────────────

@Composable
fun rakshaFieldColors(): TextFieldColors {
    val c = Raksha.colors
    return OutlinedTextFieldDefaults.colors(
        focusedBorderColor = c.rose,
        unfocusedBorderColor = c.border,
        focusedContainerColor = c.card,
        unfocusedContainerColor = c.card,
        focusedTextColor = c.text,
        unfocusedTextColor = c.text,
        cursorColor = c.rose,
        focusedLabelColor = c.rose,
        unfocusedLabelColor = c.textMuted,
        focusedPlaceholderColor = c.textFaint,
        unfocusedPlaceholderColor = c.textFaint,
    )
}

@Composable
fun rakshaSwitchColors() = Raksha.colors.let { c ->
    SwitchDefaults.colors(
        checkedThumbColor = Color.White,
        checkedTrackColor = c.rose,
        checkedBorderColor = c.rose,
        uncheckedThumbColor = Color.White,
        uncheckedTrackColor = c.cardHigh,
        uncheckedBorderColor = c.cardHigh,
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(horizontal = 4.dp),
        color = Raksha.colors.textMuted,
        fontSize = 12.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.8.sp
    )
}
