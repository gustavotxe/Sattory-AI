package org.sattory.app.presentation

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
internal actual fun Modifier.desktopConversationScrollInput(
    enabled: Boolean,
    listState: LazyListState,
): Modifier {
    if (!enabled) return this

    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    var autoScrollAnchorY by remember { mutableStateOf<Float?>(null) }
    var autoScrollCurrentY by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(enabled, autoScrollAnchorY) {
        while (enabled && autoScrollAnchorY != null && isActive) {
            val anchorY = autoScrollAnchorY
            val currentY = autoScrollCurrentY
            if (anchorY != null && currentY != null) {
                val distance = (currentY - anchorY).coerceIn(-AutoScrollMaxDistance, AutoScrollMaxDistance)
                val delta = distance / AutoScrollDamping
                if (abs(delta) >= AutoScrollMinDelta) {
                    listState.scrollBy(delta)
                }
            }
            delay(AutoScrollFrameMillis)
        }
    }

    return this
        .focusRequester(focusRequester)
        .focusable()
        .pointerHoverIcon(
            if (autoScrollAnchorY != null) {
                PointerIcon.Crosshair
            } else {
                PointerIcon.Default
            },
            overrideDescendants = autoScrollAnchorY != null,
        )
        .onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val scrollDelta = when (event.key) {
                Key.DirectionDown -> KeyboardScrollDelta
                Key.DirectionUp -> -KeyboardScrollDelta
                else -> return@onPreviewKeyEvent false
            }
            scope.launch { listState.animateScrollBy(scrollDelta) }
            true
        }
        .pointerInput(enabled) {
            var wasMiddlePressed = false
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    val pointer = event.changes.firstOrNull() ?: continue
                    val primaryPressed = event.buttons.isPrimaryPressed
                    val middlePressed = event.buttons.isTertiaryPressed

                    if (pointer.pressed) {
                        focusRequester.requestFocus()
                    }

                    if (autoScrollAnchorY != null && primaryPressed) {
                        autoScrollAnchorY = null
                        autoScrollCurrentY = null
                    } else if (middlePressed && !wasMiddlePressed) {
                        if (autoScrollAnchorY == null) {
                            autoScrollAnchorY = pointer.position.y
                            autoScrollCurrentY = pointer.position.y
                        } else {
                            autoScrollAnchorY = null
                            autoScrollCurrentY = null
                        }
                        pointer.consume()
                    } else if (autoScrollAnchorY != null) {
                        autoScrollCurrentY = pointer.position.y
                    }

                    wasMiddlePressed = middlePressed
                }
            }
        }
}

@Composable
internal actual fun DesktopConversationScrollbar(
    enabled: Boolean,
    listState: LazyListState,
    modifier: Modifier,
) {
    if (!enabled) return

    VerticalScrollbar(
        adapter = rememberScrollbarAdapter(listState),
        modifier = modifier,
    )
}

private const val AutoScrollFrameMillis = 16L
private const val AutoScrollMaxDistance = 360f
private const val AutoScrollDamping = 9f
private const val AutoScrollMinDelta = 0.5f
private const val KeyboardScrollDelta = 96f
