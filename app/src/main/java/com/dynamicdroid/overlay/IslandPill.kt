package com.dynamicdroid.overlay

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dynamicdroid.R
import com.dynamicdroid.liveupdate.LiveUpdateContent
import com.dynamicdroid.liveupdate.ManeuverIcons
import kotlinx.coroutines.delay

/** Gemeinsame Maße von Pille und Overlay-Fenster (in dp). */
internal object IslandDimens {
    const val EXPANDED_WIDTH = 344f
    const val EXPANDED_HEIGHT = 128f
    const val ACTIONS_EXTRA_HEIGHT = 56f
    const val TOP_PADDING = 4f
    /** Luft um die Pille im Fenster, damit der Spring-Überschwinger nicht abgeschnitten wird. */
    const val WINDOW_MARGIN = 16f
    const val SIDE_SLOT = 64f

    fun collapsedWidth(cutoutWidth: Float) = (cutoutWidth + 2 * SIDE_SLOT).coerceAtLeast(160f)
    fun collapsedHeight(cutoutHeight: Float) = (cutoutHeight + 8f).coerceAtLeast(36f)

    fun windowWidth(expanded: Boolean, cutoutWidth: Float) =
        (if (expanded) EXPANDED_WIDTH else collapsedWidth(cutoutWidth)) + 2 * WINDOW_MARGIN

    fun windowHeight(expanded: Boolean, cutoutHeight: Float) =
        (if (expanded) EXPANDED_HEIGHT + ACTIONS_EXTRA_HEIGHT else collapsedHeight(cutoutHeight)) + TOP_PADDING + WINDOW_MARGIN
}

private const val AUTO_EXPAND_MS = 4_000L
private const val ACTIONS_VISIBLE_MS = 6_000L
/** So lange braucht das Zuklappen, bis das Fenster verkleinert werden darf (Spring ist dann ausgeschwungen). */
internal const val COLLAPSE_SETTLE_MS = 700L

private val PillBlack = Color(0xFF000000)
private val Accent = Color(0xFF0A84FF)
private val AccentSoft = Color(0xFF64A8FF)
private val TextPrimary = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFF9A9AA0)
private val Track = Color(0xFF2C2C2E)
private val Danger = Color(0xFFFF453A)
private val Tabular = TextStyle(fontFeatureSettings = "tnum")

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

/**
 * Dynamic-Island-artige Pille.
 *
 * Aufbau für ruhige Animationen:
 *  - **Ein** Fortschrittswert `p` (0 = Pille, 1 = Karte) steuert Breite, Höhe und Radius; der Spring läuft darüber.
 *  - Die Karte ist immer in ihrer Endgröße gelayoutet und wird nur beschnitten. Text bricht dadurch
 *    während der Animation nie um und überlappt nichts.
 *  - Pillen- und Karteninhalt werden gestaffelt eingeblendet (erst raus, dann rein), nie gleichzeitig.
 *  - Ein-/Ausblenden der gesamten Pille ist ebenfalls ein Spring (Skalieren aus der Kameramitte + Alpha).
 *
 * Tap öffnet die App, Long-Tap zeigt Quick-Actions.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IslandPill(
    content: LiveUpdateContent?,
    visible: Boolean,
    cutoutWidthDp: Float,
    cutoutHeightDp: Float,
    onOpenApp: () -> Unit,
    onStop: () -> Unit,
    onWindowExpanded: (Boolean) -> Unit,
) {
    if (content == null) return

    var autoExpanded by remember { mutableStateOf(false) }
    var showActions by remember { mutableStateOf(false) }

    // Neue Abbiegung -> kurz aufklappen.
    LaunchedEffect(content.maneuverKey) {
        autoExpanded = true
        delay(AUTO_EXPAND_MS)
        autoExpanded = false
    }
    LaunchedEffect(showActions) {
        if (showActions) {
            delay(ACTIONS_VISIBLE_MS)
            showActions = false
        }
    }
    val expanded = autoExpanded || showActions

    // Fenster vor dem Aufklappen vergrößern, erst nach dem Ausschwingen wieder verkleinern.
    LaunchedEffect(expanded) {
        if (expanded) onWindowExpanded(true) else {
            delay(COLLAPSE_SETTLE_MS)
            onWindowExpanded(false)
        }
    }

    val appear = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        appear.animateTo(if (visible) 1f else 0f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow))
    }
    val p by animateFloatAsState(
        if (expanded) 1f else 0f,
        spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow),
        label = "expand",
    )
    val actionsP by animateFloatAsState(
        if (showActions) 1f else 0f,
        spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow),
        label = "actions",
    )

    val collapsedW = IslandDimens.collapsedWidth(cutoutWidthDp)
    val collapsedH = IslandDimens.collapsedHeight(cutoutHeightDp)
    val expandedH = IslandDimens.EXPANDED_HEIGHT + IslandDimens.ACTIONS_EXTRA_HEIGHT * actionsP
    val width = lerp(collapsedW, IslandDimens.EXPANDED_WIDTH, p).coerceAtLeast(collapsedW * 0.96f)
    val height = lerp(collapsedH, expandedH, p).coerceAtLeast(collapsedH * 0.96f)
    val corner = lerp(collapsedH / 2f, 32f, p.coerceIn(0f, 1f)).coerceAtLeast(0f)

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(
            Modifier
                .padding(top = IslandDimens.TOP_PADDING.dp)
                .graphicsLayer {
                    val a = appear.value
                    alpha = a.coerceIn(0f, 1f)
                    val s = 0.72f + 0.28f * a
                    scaleX = s
                    scaleY = s
                    transformOrigin = TransformOrigin(0.5f, 0f)
                }
                .size(width.dp, height.dp)
                .clip(RoundedCornerShape(corner.dp))
                .background(PillBlack)
                .combinedClickable(onClick = onOpenApp, onLongClick = { showActions = true }),
        ) {
            val collapsedAlpha = (1f - p * 2.2f).coerceIn(0f, 1f)
            val expandedAlpha = ((p - 0.55f) / 0.45f).coerceIn(0f, 1f)
            if (collapsedAlpha > 0f) {
                CollapsedRow(content, cutoutWidthDp, Modifier.graphicsLayer { alpha = collapsedAlpha })
            }
            if (expandedAlpha > 0f) {
                Box(
                    Modifier
                        .graphicsLayer { alpha = expandedAlpha }
                        .wrapContentSize(Alignment.TopCenter, unbounded = true)
                        .requiredSize(IslandDimens.EXPANDED_WIDTH.dp, (IslandDimens.EXPANDED_HEIGHT + IslandDimens.ACTIONS_EXTRA_HEIGHT).dp),
                ) {
                    ExpandedCard(content, actionsP, onOpenApp, onStop)
                }
            }
        }
    }
}

@Composable
private fun CollapsedRow(content: LiveUpdateContent, cutoutWidthDp: Float, modifier: Modifier) {
    Row(modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Image(
                painterResource(ManeuverIcons.forType(content.maneuverType)),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                colorFilter = ColorFilter.tint(AccentSoft),
            )
        }
        Spacer(Modifier.width(cutoutWidthDp.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            SwapText(
                content.shortCriticalText,
                style = Tabular.copy(color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ExpandedCard(content: LiveUpdateContent, actionsP: Float, onOpenApp: () -> Unit, onStop: () -> Unit) {
    val progress by animateFloatAsState(
        (content.progressMeters.toFloat() / content.progressMaxMeters).coerceIn(0f, 1f),
        tween(1_000, easing = LinearEasing),
        label = "routeProgress",
    )
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(Accent.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                SwapIcon(content)
            }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                SwapText(
                    content.distanceToManeuver,
                    style = Tabular.copy(color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.SemiBold),
                )
                SwapText(
                    content.instruction,
                    style = TextStyle(color = TextSecondary, fontSize = 14.sp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                content.remainingText,
                style = Tabular.copy(color = TextSecondary, fontSize = 12.sp),
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            Text(content.etaText, style = Tabular.copy(color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Track)) {
            Box(Modifier.fillMaxWidth(progress).height(4.dp).clip(CircleShape).background(Accent))
        }
        if (actionsP > 0.15f) {
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth().graphicsLayer { alpha = ((actionsP - 0.15f) / 0.6f).coerceIn(0f, 1f) },
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ActionButton(stringResource(R.string.action_open_app), Color(0xFF2C2C2E), TextPrimary, onOpenApp, Modifier.weight(1f))
                ActionButton(stringResource(R.string.action_stop_navigation), Danger.copy(alpha = 0.2f), Danger, onStop, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ActionButton(label: String, background: Color, textColor: Color, onClick: () -> Unit, modifier: Modifier) {
    Box(
        modifier.height(36.dp).clip(CircleShape).background(background).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = textColor, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp))
    }
}

/** Icon tauscht per Fade (erst alt raus, dann neu rein) statt hart zu springen. */
@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun SwapIcon(content: LiveUpdateContent) {
    AnimatedContent(
        targetState = content.maneuverType,
        transitionSpec = { fadeIn(tween(220, delayMillis = 140)) togetherWith fadeOut(tween(120)) using SizeTransform(clip = false) { _, _ -> snap() } },
        label = "maneuverIcon",
    ) { type ->
        Image(
            painterResource(ManeuverIcons.forType(type)),
            contentDescription = null,
            modifier = Modifier.size(26.dp).semantics { contentDescription = content.instruction },
            colorFilter = ColorFilter.tint(AccentSoft),
        )
    }
}

/**
 * Textwechsel: alter Text blendet kurz aus, danach gleitet der neue minimal von unten ein. Die beiden sind nie
 * gleichzeitig sichtbar, deshalb überlappt nichts; die Größenanimation ist aus, damit das Layout nicht wandert.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun SwapText(text: String, style: TextStyle, textAlign: TextAlign? = null) {
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            (fadeIn(tween(200, delayMillis = 130)) + slideInVertically(tween(260, delayMillis = 130)) { it / 5 }) togetherWith
                fadeOut(tween(110)) using SizeTransform(clip = false) { _, _ -> snap() }
        },
        label = "swapText",
    ) { value ->
        Text(value, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = textAlign)
    }
}
