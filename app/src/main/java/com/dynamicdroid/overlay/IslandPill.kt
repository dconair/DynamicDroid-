package com.dynamicdroid.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ColorFilter
import com.dynamicdroid.R
import com.dynamicdroid.liveupdate.LiveUpdateContent
import com.dynamicdroid.liveupdate.ManeuverIcons
import kotlinx.coroutines.delay

private const val AUTO_EXPAND_MS = 4_000L
private const val ACTIONS_VISIBLE_MS = 6_000L

/**
 * Dynamic-Island-artige Pille: klein (Icon + Distanz links/rechts der Kamera), klappt bei einer neuen Abbiegung
 * kurz zu einer Karte auf, per Spring-Animation. Tap öffnet die App, Long-Tap zeigt Quick-Actions.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IslandPill(
    content: LiveUpdateContent?,
    cutoutWidthDp: Float,
    cutoutHeightDp: Float,
    onOpenApp: () -> Unit,
    onStop: () -> Unit,
) {
    if (content == null) return

    var autoExpanded by remember { mutableStateOf(false) }
    var showActions by remember { mutableStateOf(false) }

    // Neue Abbiegung -> kurz aufklappen. (Der erste Aufruf klappt ebenfalls kurz auf, das ist gewollt.)
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
    val springSpec = spring<androidx.compose.ui.unit.Dp>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
    val collapsedWidth = (cutoutWidthDp + 2 * 56f).coerceAtLeast(150f)
    val collapsedHeight = (cutoutHeightDp + 8f).coerceAtLeast(36f)
    val width by animateDpAsState(if (expanded) 340.dp else collapsedWidth.dp, springSpec, label = "pillWidth")
    val height by animateDpAsState(
        if (showActions) 150.dp else if (expanded) 108.dp else collapsedHeight.dp, springSpec, label = "pillHeight",
    )
    val corner by animateDpAsState(if (expanded) 28.dp else (collapsedHeight / 2).dp, springSpec, label = "pillCorner")

    Box(
        modifier = Modifier
            .padding(top = 4.dp)
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(corner))
            .background(Color.Black)
            .combinedClickable(
                onClick = onOpenApp,
                onLongClick = { showActions = true },
            ),
    ) {
        AnimatedVisibility(visible = !expanded, enter = fadeIn(), exit = fadeOut()) {
            CollapsedRow(content, cutoutWidthDp)
        }
        AnimatedVisibility(visible = expanded, enter = fadeIn(), exit = fadeOut()) {
            ExpandedCard(content, showActions, onOpenApp, onStop)
        }
    }
}

@Composable
private fun CollapsedRow(content: LiveUpdateContent, cutoutWidthDp: Float) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(36.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Image(
            painterResource(ManeuverIcons.forType(content.maneuverType)),
            contentDescription = content.instruction,
            modifier = Modifier.width(20.dp).height(20.dp),
            colorFilter = ColorFilter.tint(Color.White),
        )
        Spacer(Modifier.width(cutoutWidthDp.dp))
        Text(content.shortCriticalText, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun ExpandedCard(content: LiveUpdateContent, showActions: Boolean, onOpenApp: () -> Unit, onStop: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painterResource(ManeuverIcons.forType(content.maneuverType)),
                contentDescription = null,
                modifier = Modifier.width(36.dp).height(36.dp),
                colorFilter = ColorFilter.tint(Color.White),
            )
            Column(Modifier.padding(start = 12.dp)) {
                Text(content.distanceToManeuver, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(content.instruction, color = Color(0xFFCCCCCC), fontSize = 13.sp, maxLines = 1)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(content.text, color = Color(0xFFAAAAAA), fontSize = 12.sp, maxLines = 1)
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { content.progressMeters.toFloat() / content.progressMaxMeters },
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF8AB4F8),
            trackColor = Color(0xFF3C4043),
        )
        if (showActions) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onOpenApp) { Text(stringResource(R.string.action_open_app), color = Color.White) }
                TextButton(onClick = onStop) { Text(stringResource(R.string.action_stop_navigation), color = Color(0xFFF28B82)) }
            }
        }
    }
}
