package com.html2mp4.renderer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.html2mp4.renderer.ui.i18n.AppStrings
import kotlinx.coroutines.delay

@Composable
fun GeminiSplashOverlay(
    strings: AppStrings,
    onFinished: () -> Unit
) {
    var visible by remember { mutableStateOf(true) }
    var animateIn by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        animateIn = true
        delay(2900L)
        visible = false
        delay(380L)
        onFinished()
    }

    val logoScale by animateFloatAsState(
        targetValue = if (animateIn) 1f else 0.58f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "geminiLogoScale"
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (animateIn) 1f else 0f,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "splashAlpha"
    )
    val textSlideY by animateFloatAsState(
        targetValue = if (animateIn) 0f else 18f,
        animationSpec = tween(durationMillis = 700, delayMillis = 120, easing = FastOutSlowInEasing),
        label = "splashSlideY"
    )

    val pulseTransition = rememberInfiniteTransition(label = "monetGlowPulse")
    val glowRadiusScale by pulseTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowRadiusScale"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val tertiaryContainer = MaterialTheme.colorScheme.tertiaryContainer
    val surfaceColor = MaterialTheme.colorScheme.surface

    AnimatedVisibility(
        visible = visible,
        exit = fadeOut(animationSpec = tween(380)) + scaleOut(targetScale = 1.04f, animationSpec = tween(380))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(surfaceColor)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    visible = false
                    onFinished()
                },
            contentAlignment = Alignment.Center
        ) {
            // Monet dynamic ambient wash matching the inner Material You UI
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width * 0.5f, size.height * 0.44f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryContainer.copy(alpha = 0.65f),
                            tertiaryContainer.copy(alpha = 0.30f),
                            surfaceColor.copy(alpha = 0f)
                        ),
                        center = center,
                        radius = size.minDimension * 0.72f * glowRadiusScale
                    ),
                    center = center,
                    radius = size.minDimension * 0.72f * glowRadiusScale
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .padding(horizontal = 32.dp)
                    .alpha(contentAlpha)
            ) {
                // Animated Gemini Sparkle Emblem
                Canvas(
                    modifier = Modifier
                        .size(86.dp)
                        .scale(logoScale)
                ) {
                    val cx = size.width * 0.46f
                    val cy = size.height * 0.52f
                    val r = size.minDimension * 0.40f

                    val mainStar = Path().apply {
                        moveTo(cx, cy - r)
                        quadraticTo(cx, cy, cx + r, cy)
                        quadraticTo(cx, cy, cx, cy + r)
                        quadraticTo(cx, cy, cx - r, cy)
                        quadraticTo(cx, cy, cx, cy - r)
                        close()
                    }
                    drawPath(
                        path = mainStar,
                        brush = Brush.linearGradient(
                            colors = listOf(primaryColor, tertiaryColor),
                            start = Offset(cx - r, cy - r),
                            end = Offset(cx + r, cy + r)
                        )
                    )

                    val ax = size.width * 0.80f
                    val ay = size.height * 0.22f
                    val ar = size.minDimension * 0.14f
                    val accentStar = Path().apply {
                        moveTo(ax, ay - ar)
                        quadraticTo(ax, ay, ax + ar, ay)
                        quadraticTo(ax, ay, ax, ay + ar)
                        quadraticTo(ax, ay, ax - ar, ay)
                        quadraticTo(ax, ay, ax, ay - ar)
                        close()
                    }
                    drawPath(
                        path = accentStar,
                        color = tertiaryColor
                    )
                }

                Spacer(Modifier.height(20.dp))

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.offset(y = textSlideY.dp)
                ) {
                    Text(
                        text = "HTML2MP4",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            letterSpacing = 1.2.sp
                        ),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(Modifier.height(6.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = strings.splashPoweredBy,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
